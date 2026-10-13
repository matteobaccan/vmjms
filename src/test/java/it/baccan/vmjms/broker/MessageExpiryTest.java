package it.baccan.vmjms.broker;

import it.baccan.vmjms.exception.CapacityExceededException;
import it.baccan.vmjms.InMemoryConnectionFactory;
import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.lang.management.ManagementFactory;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Expiry of messages and the reclamation that frees the memory and the capacity they hold.
 *
 * <p>In the broker package so that the sweep can be made to fail through an envelope whose
 * expiry check throws, which is how the resilience of the sweeper is verified without a
 * test-only hook in production code.</p>
 */
public class MessageExpiryTest {

    private static final String SWEEP_INTERVAL = "25";

    @BeforeEach
    public void startWithAFastSweeper() {
        // Stop the sweeper first: the interval is read when the next sweep is armed, so a
        // sweeper started by an earlier test would keep its own schedule for one cycle.
        InMemoryBrokerRegistry.clear();
        System.setProperty(InMemoryBrokerRegistry.SWEEP_INTERVAL_PROPERTY, SWEEP_INTERVAL);
    }

    @AfterEach
    public void cleanState() {
        InMemoryBrokerRegistry.clear();
        System.clearProperty(InMemoryBrokerRegistry.SWEEP_INTERVAL_PROPERTY);
    }

    @Test
    public void expiredMessageIsNotDelivered() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-delivery");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.setTimeToLive(100);
            producer.send(session.createTextMessage("expired"));
            Thread.sleep(300);

            assertNull(consumer.receive(200), "an expired message must not be delivered");
        }
    }

    @Test
    public void expiredMessageIsSkippedInFavourOfALiveOne() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-skip");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.setTimeToLive(80);
            producer.send(session.createTextMessage("expired"));
            producer.setTimeToLive(0);
            producer.send(session.createTextMessage("live"));
            Thread.sleep(200);

            Message received = consumer.receive(500);
            assertNotNull(received);
            assertEquals("live", received.getBody(String.class));
        }
    }

    @Test
    public void fifoOrderIsPreservedAcrossASkippedMessage() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-fifo");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.send(session.createTextMessage("A"));
            producer.setTimeToLive(80);
            producer.send(session.createTextMessage("B"));
            producer.setTimeToLive(0);
            producer.send(session.createTextMessage("C"));
            Thread.sleep(200);

            assertEquals("A", consumer.receive(500).getBody(String.class));
            assertEquals("C", consumer.receive(500).getBody(String.class));
            assertNull(consumer.receiveNoWait());
        }
    }

    @Test
    public void memoryIsReclaimedWithoutAnyTraffic() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-noconsumer");
        InMemoryBroker broker = factory.getBroker();
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);

            producer.setTimeToLive(60);
            for (int i = 0; i < 3; i++) {
                producer.send(session.createTextMessage("m" + i));
            }
            assertEquals(3, broker.getMessagesWaiting());

            assertTrue(waitUntil(() -> broker.getMessagesWaiting() == 0, 3000),
                    "expired messages must be reclaimed without any traffic");
            assertEquals(0, broker.getQueueDepths().get("q"));
            assertEquals(3, broker.getExpiredCount());
            assertEquals(0, broker.getDeliveredCount());
        }
    }

    @Test
    public void reclamationFreesCapacity() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-capacity", 1);
        InMemoryBroker broker = factory.getBroker();
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);

            producer.setTimeToLive(60);
            producer.send(session.createTextMessage("expiring"));
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected")));

            assertTrue(waitUntil(() -> broker.getMessagesWaiting() == 0, 3000),
                    "reclamation must free the capacity");

            producer.setTimeToLive(0);
            producer.send(session.createTextMessage("accepted"));
            assertEquals(1, broker.getMessagesWaiting());
        }
    }

    @Test
    public void messagesWithoutATimeToLiveAreRetained() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-retained");
        InMemoryBroker broker = factory.getBroker();
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            // One expiring message starts the sweeper, so the retained one is exposed to it.
            producer.setTimeToLive(40);
            producer.send(session.createTextMessage("expiring"));
            producer.setTimeToLive(0);
            producer.send(session.createTextMessage("retained"));

            assertTrue(waitUntil(() -> broker.getExpiredCount() == 1, 3000),
                    "the expiring message must be reclaimed");
            Thread.sleep(200); // several further sweeps

            Message received = consumer.receive(500);
            assertNotNull(received, "a message with no time-to-live must stay available");
            assertEquals("retained", received.getBody(String.class));
        }
    }

    @Test
    public void sweepIntervalIsJvmWideAndConfigurable() throws Exception {
        InMemoryConnectionFactory first = new InMemoryConnectionFactory("expiry-interval-a");
        InMemoryConnectionFactory second = new InMemoryConnectionFactory("expiry-interval-b");
        sendExpiring(first, 30);
        sendExpiring(second, 30);

        // Well below the one-second default: only the configured interval can achieve it,
        // and one sweeper serves both brokers.
        assertTrue(waitUntil(() -> first.getBroker().getMessagesWaiting() == 0
                        && second.getBroker().getMessagesWaiting() == 0, 600),
                "both brokers must be swept at the configured interval");
        assertEquals(1, sweeperThreads(), "there is exactly one sweeper thread for the JVM");
    }

    @Test
    public void aFailingSweepDoesNotStopLaterSweepsOrStarveOtherBrokers() throws Exception {
        InMemoryConnectionFactory failing = new InMemoryConnectionFactory("expiry-failing");
        InMemoryConnectionFactory healthy = new InMemoryConnectionFactory("expiry-healthy");
        InMemoryBroker failingBroker = failing.getBroker();

        // A message that can expire but will not during the test keeps this broker in the
        // scan for good - a broker with nothing left to expire is deliberately not scanned -
        // and the hostile envelope makes every scan of it throw.
        sendExpiring(failing, TimeUnit.MINUTES.toMillis(10));
        failingBroker.offerUnaccounted("boom", new ExplodingEnvelope());
        sendExpiring(healthy, 30);

        assertTrue(waitUntil(() -> failingBroker.getSweepFailureCount() >= 2, 3000),
                "later sweeps must still run after one has failed");
        assertTrue(failingBroker.getLastSweepError().contains("boom"),
                failingBroker.getLastSweepError());
        assertTrue(waitUntil(() -> healthy.getBroker().getMessagesWaiting() == 0, 3000),
                "a failure on one broker must not starve the others");
    }

    @Test
    public void aFailedSweepIsVisibleOverJmx() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-jmx-failure");
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        sendExpiring(factory, 30);
        broker.offerUnaccounted("boom", new ExplodingEnvelope());

        assertTrue(waitUntil(() -> broker.getSweepFailureCount() >= 1, 3000), "a sweep must fail");

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = broker.objectName();
        long failures = (Long) server.getAttribute(name, "SweepFailureCount");
        String lastError = (String) server.getAttribute(name, "LastSweepError");

        assertTrue(failures >= 1, "failures reported over JMX: " + failures);
        assertTrue(lastError.contains("boom"), lastError);
    }

    @Test
    public void reclamationWorksAgainAfterTheRegistryIsCleared() throws Exception {
        InMemoryConnectionFactory before = new InMemoryConnectionFactory("expiry-before-clear");
        sendExpiring(before, 30);
        assertTrue(waitUntil(() -> before.getBroker().getExpiredCount() == 1, 3000),
                "reclamation must work before the registry is cleared");

        InMemoryBrokerRegistry.clear();
        assertTrue(waitUntil(() -> sweeperThreads() == 0, 3000), "clear() must stop the sweeper");

        InMemoryConnectionFactory after = new InMemoryConnectionFactory("expiry-after-clear");
        sendExpiring(after, 30);
        assertTrue(waitUntil(() -> after.getBroker().getExpiredCount() == 1, 3000),
                "reclamation must work again after clear()");
    }

    @Test
    public void capacityAccountingSurvivesConcurrentRemoval() throws Exception {
        int rounds = 150;
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-race", 8);
        InMemoryBroker broker = factory.getBroker();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            producer.setTimeToLive(1);

            for (int round = 0; round < rounds; round++) {
                producer.send(session.createTextMessage("m" + round));
                Thread.sleep(2); // the message is now expired

                CountDownLatch start = new CountDownLatch(1);
                Future<?> sweep = pool.submit(() -> {
                    start.await();
                    broker.sweepExpired();
                    return null;
                });
                Future<?> consume = pool.submit(() -> {
                    start.await();
                    return broker.receiveNoWait("q");
                });
                start.countDown();
                sweep.get(5, TimeUnit.SECONDS);
                consume.get(5, TimeUnit.SECONDS);

                long expected = round + 1L;
                // Released at most once: a second release would show up as a negative
                // number of waiting messages, or as an outcome counted twice.
                assertTrue(waitUntil(() -> accountedFor(broker) == expected, 1000),
                        "each message must be accounted for exactly once, round " + round
                                + ", accounted for " + accountedFor(broker));
                assertEquals(0, broker.getMessagesWaiting(),
                        "capacity must be released exactly once, round " + round);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void noNonDaemonThreadSurvivesClear() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("expiry-threads");
        sendExpiring(factory, 30);

        assertTrue(waitUntil(() -> sweeperThreads() == 1, 3000), "the sweeper must have started");
        Optional<Thread> sweeper = Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> "vmjms-sweeper".equals(thread.getName()))
                .findFirst();
        assertTrue(sweeper.isPresent());
        assertTrue(sweeper.get().isDaemon(), "the sweeper must be a daemon thread");

        InMemoryBrokerRegistry.clear();

        assertTrue(waitUntil(() -> sweeperThreads() == 0, 3000),
                "no sweeper thread may survive clear()");
    }

    @Test
    public void anUnaccountedEnvelopeIsRefusedUnlessItIsClaimed() {
        InMemoryBroker broker = InMemoryBrokerRegistry.getOrCreate("expiry-unaccounted-guard");

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> broker.offerUnaccounted("boom", new QueuedMessage(null, 1L)));

        assertTrue(refusal.getMessage().contains("claimed"), refusal.getMessage());
        assertEquals(0, broker.getQueueCount(), "a refused envelope must not create its queue");
        assertEquals(0, broker.getMessagesWaiting(), "a refused envelope must hold no capacity");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void sendExpiring(InMemoryConnectionFactory factory, long timeToLive) throws Exception {
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            producer.setTimeToLive(timeToLive);
            producer.send(session.createTextMessage("expiring"));
        }
    }

    /**
     * Messages that have left a queue through one of the accounted paths.
     */
    private static long accountedFor(InMemoryBroker broker) {
        return broker.getDeliveredCount() + broker.getExpiredCount() + broker.getPurgedCount();
    }

    private static long sweeperThreads() {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(thread -> "vmjms-sweeper".equals(thread.getName()))
                .filter(Thread::isAlive)
                .count();
    }

    private static boolean waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    /**
     * An envelope whose expiry check throws, used to make a sweep fail.
     *
     * <p>It goes in through {@code offerUnaccounted}, bypassing {@code send}, so it never
     * reserved capacity. It claims itself at construction so that no removal path accounts
     * for it: releasing a permit that was never acquired would push an unbounded broker
     * past {@code Integer.MAX_VALUE} permits, which {@code Semaphore} reports as an
     * {@code Error} - the accounting working as intended, but on the test rather than on
     * the broker. {@code offerUnaccounted} refuses an unclaimed envelope for that reason.</p>
     */
    private static final class ExplodingEnvelope extends QueuedMessage {

        private ExplodingEnvelope() {
            super(null, 1L);
            claim();
        }

        @Override
        boolean isExpired(long now) {
            throw new IllegalStateException("boom");
        }
    }
}
