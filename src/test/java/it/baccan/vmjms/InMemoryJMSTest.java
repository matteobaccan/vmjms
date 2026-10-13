package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import jakarta.jms.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

public class InMemoryJMSTest {

    /**
     * Whichever class runs last, the suite leaves no broker, no sweeper thread and no
     * MBean behind.
     */
    @AfterAll
    public static void releaseBrokers() {
        InMemoryBrokerRegistry.clear();
    }

    @Test
    public void testIsolatedFactories() throws Exception {
        InMemoryConnectionFactory factoryA = new InMemoryConnectionFactory();
        InMemoryConnectionFactory factoryB = new InMemoryConnectionFactory();

        try (Connection connA = factoryA.createConnection();
             Connection connB = factoryB.createConnection()) {
            
            Session sessionA = connA.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Session sessionB = connB.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue queue = sessionA.createQueue("myQueue");

            MessageProducer producerA = sessionA.createProducer(queue);
            MessageConsumer consumerB = sessionB.createConsumer(queue);

            // Send to factory A
            TextMessage msg = sessionA.createTextMessage("Hello World");
            producerA.send(msg);

            // Try to receive from factory B - should be empty (isolated)
            Message received = consumerB.receive(100);
            assertNull(received, "Broker states should be isolated between factories");
            
            // Receive from factory A - should succeed
            MessageConsumer consumerA = sessionA.createConsumer(queue);
            Message receivedA = consumerA.receive(100);
            assertNotNull(receivedA);
            assertEquals("Hello World", ((TextMessage) receivedA).getText());
        }
    }

    @Test
    public void testFIFOOrderOfDelivery() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("fifoQueue");

            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.send(session.createTextMessage("First"));
            producer.send(session.createTextMessage("Second"));

            TextMessage msg1 = (TextMessage) consumer.receive(100);
            TextMessage msg2 = (TextMessage) consumer.receive(100);

            assertNotNull(msg1);
            assertEquals("First", msg1.getText());
            assertNotNull(msg2);
            assertEquals("Second", msg2.getText());
        }
    }

    @Test
    public void testHexTimestampSequenceIDGeneration() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("idQueue");

            MessageProducer producer = session.createProducer(queue);

            TextMessage msg1 = session.createTextMessage("Msg 1");
            TextMessage msg2 = session.createTextMessage("Msg 2");

            producer.send(msg1);
            producer.send(msg2);

            String id1 = msg1.getJMSMessageID();
            String id2 = msg2.getJMSMessageID();

            assertNotNull(id1);
            assertNotNull(id2);
            assertTrue(id1.startsWith("ID:"));
            assertTrue(id2.startsWith("ID:"));

            // Verify they have same hex prefix, same instance ID but sequence incremented
            String[] parts1 = id1.split(":");
            String[] parts2 = id2.split(":");

            assertEquals(4, parts1.length);
            assertEquals(4, parts2.length);
            assertEquals(parts1[1], parts2[1], "Broker ID prefixes should match");
            assertEquals(parts1[2], parts2[2], "Instance IDs should match");
            
            long seq1 = Long.parseLong(parts1[3]);
            long seq2 = Long.parseLong(parts2[3]);
            assertEquals(seq1 + 1, seq2, "Sequence number should increment sequentially");
        }
    }

    @Test
    public void testTextMessageAndBytesMessageSupport() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("messageTypesQueue");

            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            // Test TextMessage
            TextMessage txtIn = session.createTextMessage("TextPayload");
            producer.send(txtIn);
            TextMessage txtOut = (TextMessage) consumer.receive(100);
            assertNotNull(txtOut);
            assertEquals("TextPayload", txtOut.getText());
            assertTrue(txtOut.isBodyAssignableTo(String.class));
            assertEquals("TextPayload", txtOut.getBody(String.class));

            // Test BytesMessage
            BytesMessage bytesIn = session.createBytesMessage();
            bytesIn.writeBoolean(true);
            bytesIn.writeInt(42);
            bytesIn.writeUTF("HelloBytes");
            producer.send(bytesIn);

            BytesMessage bytesOut = (BytesMessage) consumer.receive(100);
            assertNotNull(bytesOut);
            
            // Read should be valid
            assertTrue(bytesOut.readBoolean());
            assertEquals(42, bytesOut.readInt());
            assertEquals("HelloBytes", bytesOut.readUTF());

            // Assignable tests
            assertTrue(bytesOut.isBodyAssignableTo(byte[].class));
            byte[] rawBytes = bytesOut.getBody(byte[].class);
            assertNotNull(rawBytes);
            assertTrue(rawBytes.length > 0);
        }
    }

    @Test
    public void testReceiveTimeout() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("timeoutQueue");
            MessageConsumer consumer = session.createConsumer(queue);

            long start = System.currentTimeMillis();
            Message received = consumer.receive(150);
            long elapsed = System.currentTimeMillis() - start;

            assertNull(received);
            assertTrue(elapsed >= 140, "Should have blocked for at least 150ms");
        }
    }

    @Test
    public void testCascadingCloseAndThreadInterruption() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        Connection conn = factory.createConnection();
        Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue("closeQueue");
        MessageConsumer consumer = session.createConsumer(queue);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            try {
                latch.countDown();
                consumer.receive(); // Will block indefinitely
            } catch (Exception e) {
                exceptionRef.set(e);
            }
        });

        // Wait for thread to start and block
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        Thread.sleep(100); // Give it a moment to enter block

        // Cascading close starting from connection
        conn.close();

        executor.shutdown();
        assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));

        Exception exception = exceptionRef.get();
        assertNotNull(exception, "Blocked thread should have thrown an exception on close");
        assertTrue(exception instanceof jakarta.jms.IllegalStateException, 
            "Exception should be IllegalStateException but was: " + exception.getClass().getName());
        assertTrue(exception.getMessage().contains("closed"), 
            "Exception message should mention closure");
    }

    @Test
    public void testSharedFactoriesWithMatchingBrokerId() throws Exception {
        it.baccan.vmjms.broker.InMemoryBrokerRegistry.clear();
        InMemoryConnectionFactory factoryA = new InMemoryConnectionFactory("sharedBroker");
        InMemoryConnectionFactory factoryB = new InMemoryConnectionFactory("sharedBroker");

        try (Connection connA = factoryA.createConnection();
             Connection connB = factoryB.createConnection()) {
            
            Session sessionA = connA.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Session sessionB = connB.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Queue queue = sessionA.createQueue("sharedQueue");

            MessageProducer producerA = sessionA.createProducer(queue);
            MessageConsumer consumerB = sessionB.createConsumer(queue);

            // Send to factory A
            TextMessage msg = sessionA.createTextMessage("Hello Shared Broker");
            producerA.send(msg);

            // Receive from factory B - should succeed since they share the same brokerId
            TextMessage received = (TextMessage) consumerB.receive(500);
            assertNotNull(received, "Should receive message from shared broker");
            assertEquals("Hello Shared Broker", received.getText());
        }
    }

    @Test
    public void testQueueNameParameterParsing() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory();
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Create queue with query parameters
            InMemoryQueue queue = (InMemoryQueue) session.createQueue("myTestQueue?delay=1000&retry=true&emptyVal=");
            
            assertEquals("myTestQueue", queue.getQueueName());
            assertNotNull(queue.getParameters());
            assertEquals("1000", queue.getParameters().get("delay"));
            assertEquals("true", queue.getParameters().get("retry"));
            assertEquals("", queue.getParameters().get("emptyVal"));
        }
    }
}
