package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Broker-wide capacity limit and the numbered exception it raises.
 */
public class BrokerCapacityTest {

    @BeforeEach
    @AfterEach
    public void cleanState() {
        System.clearProperty(InMemoryBrokerRegistry.MAX_MESSAGES_PROPERTY);
        InMemoryBrokerRegistry.clear();
    }

    @Test
    public void limitSpansAllQueuesOfTheBroker() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-span", 10);
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queueA = session.createQueue("a");
            Queue queueB = session.createQueue("b");
            MessageProducer producer = session.createProducer(null);

            for (int i = 0; i < 6; i++) {
                producer.send(queueA, session.createTextMessage("a" + i));
            }
            for (int i = 0; i < 4; i++) {
                producer.send(queueB, session.createTextMessage("b" + i));
            }

            assertEquals(10, factory.getBroker().getMessagesWaiting());
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(queueA, session.createTextMessage("overflow-a")));
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(queueB, session.createTextMessage("overflow-b")));
        }
    }

    @Test
    public void consumingAMessageFreesCapacity() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-consume", 1);
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.send(session.createTextMessage("first"));
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("second")));

            assertNotNull(consumer.receive(500));
            producer.send(session.createTextMessage("second"));
            assertEquals(1, factory.getBroker().getMessagesWaiting());
        }
    }

    @Test
    public void unconfiguredBrokerIsUnbounded() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);

            for (int i = 0; i < 500; i++) {
                producer.send(session.createTextMessage("m" + i));
            }

            assertEquals(Integer.MAX_VALUE, factory.getBroker().getMaxMessages());
            assertEquals(500, factory.getBroker().getMessagesWaiting());
        }
    }

    @Test
    public void limitIsNotSharedBetweenBrokers() throws Exception {
        InMemoryConnectionFactory first = new InMemoryConnectionFactory("cap-first", 5);
        InMemoryConnectionFactory second = new InMemoryConnectionFactory("cap-second", 5);

        fill(first, 5);
        fill(second, 5);

        assertEquals(5, first.getBroker().getMessagesWaiting());
        assertEquals(5, second.getBroker().getMessagesWaiting());
    }

    @Test
    public void defaultMaximumTakenFromSystemProperty() throws Exception {
        System.setProperty(InMemoryBrokerRegistry.MAX_MESSAGES_PROPERTY, "3");

        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-property");
        assertEquals(3, factory.getBroker().getMaxMessages());

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);

            for (int i = 0; i < 3; i++) {
                producer.send(session.createTextMessage("m" + i));
            }
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("fourth")));
        }
    }

    @Test
    public void conflictingLimitsForASharedBrokerAreRefused() {
        new InMemoryConnectionFactory("cap-conflict", 5);

        JMSRuntimeException failure = assertThrows(JMSRuntimeException.class,
                () -> new InMemoryConnectionFactory("cap-conflict", 9));

        assertEquals(VmjmsErrorCodes.CONFLICTING_BROKER_LIMIT, failure.getErrorCode());
        assertTrue(failure.getMessage().contains("cap-conflict"), failure.getMessage());
        assertTrue(failure.getMessage().contains("5"), failure.getMessage());
        assertTrue(failure.getMessage().contains("9"), failure.getMessage());
        assertEquals(5, InMemoryBrokerRegistry.find("cap-conflict").getMaxMessages());
    }

    @Test
    public void factoryWithoutExplicitMaximumNeverConflicts() {
        new InMemoryConnectionFactory("cap-inherit", 5);
        System.setProperty(InMemoryBrokerRegistry.MAX_MESSAGES_PROPERTY, "7");

        InMemoryConnectionFactory inheriting = new InMemoryConnectionFactory("cap-inherit");

        assertEquals(5, inheriting.getBroker().getMaxMessages());
    }

    @Test
    public void sendToAFullBrokerFailsImmediately() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-immediate", 1);
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.send(session.createTextMessage("first"));

            long startedAt = System.nanoTime();
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected")));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
            assertTrue(elapsedMillis < 200, "send must not block, took " + elapsedMillis + " ms");

            // The rejected message is nowhere to be found.
            Message received = consumer.receive(200);
            assertNotNull(received);
            assertEquals("first", received.getBody(String.class));
            assertNull(consumer.receiveNoWait());
        }
    }

    @Test
    public void exceptionReportsTheLimitAndTheQueue() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-reporting", 50);
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("orders");
            MessageProducer producer = session.createProducer(queue);

            for (int i = 0; i < 50; i++) {
                producer.send(session.createTextMessage("m" + i));
            }

            CapacityExceededException failure = assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected")));

            assertEquals(50, failure.getLimit());
            assertEquals("orders", failure.getQueueName());
            assertEquals(50, failure.getWaitingCount());
            assertEquals(VmjmsErrorCodes.CAPACITY_EXCEEDED, failure.getErrorCode());
            assertEquals("VMJMS-1001", failure.getErrorCode());
            assertTrue(failure.getMessage().contains("maximum number of queued messages"),
                    failure.getMessage());
            assertTrue(failure.getMessage().contains("50"), failure.getMessage());
        }
    }

    @Test
    public void rejectionIsCatchableAsAJmsException() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-catchable", 1);
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);

            producer.send(session.createTextMessage("first"));

            boolean caught = false;
            try {
                producer.send(session.createTextMessage("rejected"));
            } catch (JMSException e) {
                caught = true;
            }
            assertTrue(caught, "A capacity rejection must be catchable as jakarta.jms.JMSException");

            // The rejection consumed no capacity: one message in, one slot taken.
            assertEquals(1, factory.getBroker().getMessagesWaiting());
            assertEquals(1, factory.getBroker().getEnqueuedCount());
            assertEquals(1, factory.getBroker().getRejectedCount());
        }
    }

    @Test
    public void manyProducersAgainstASmallLimit() throws Exception {
        int limit = 10;
        int threads = 8;
        int sendsPerThread = 25;
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("cap-race", limit);
        InMemoryBroker broker = factory.getBroker();

        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        try (Connection connection = factory.createConnection()) {
            for (int t = 0; t < threads; t++) {
                pool.submit(() -> {
                    try {
                        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                        Queue queue = session.createQueue("q" + Thread.currentThread().getId());
                        MessageProducer producer = session.createProducer(queue);
                        start.await();
                        for (int i = 0; i < sendsPerThread; i++) {
                            try {
                                producer.send(session.createTextMessage("m" + i));
                                accepted.incrementAndGet();
                            } catch (CapacityExceededException e) {
                                rejected.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertTrue(done.await(30, TimeUnit.SECONDS), "producers did not finish");

            assertEquals(threads * sendsPerThread, accepted.get() + rejected.get(),
                    "every attempt is either accepted or rejected");
            assertEquals(limit, accepted.get(), "exactly the limit can be accepted with no consumer");
            assertEquals(limit, broker.getMessagesWaiting(), "the broker holds exactly the limit");
            assertEquals(limit, broker.getEnqueuedCount());
            assertEquals((long) threads * sendsPerThread - limit, broker.getRejectedCount());
        } finally {
            pool.shutdownNow();
        }
    }

    private static void fill(InMemoryConnectionFactory factory, int messages) throws JMSException {
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            for (int i = 0; i < messages; i++) {
                producer.send(session.createTextMessage("m" + i));
            }
        }
    }
}
