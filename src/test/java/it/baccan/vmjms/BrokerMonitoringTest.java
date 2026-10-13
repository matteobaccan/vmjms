package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import it.baccan.vmjms.exception.CapacityExceededException;
import jakarta.jms.Connection;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.lang.management.ManagementFactory;
import java.util.Map;
import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * On-demand JMX monitoring of a broker, and the metrics it exposes.
 */
public class BrokerMonitoringTest {

    private static final MBeanServer SERVER = ManagementFactory.getPlatformMBeanServer();

    @BeforeEach
    @AfterEach
    public void cleanState() {
        InMemoryBrokerRegistry.clear();
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    public void monitoringIsOffByDefault() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-default");

        assertFalse(factory.getBroker().isJmxEnabled());
        assertFalse(SERVER.isRegistered(factory.getBroker().objectName()));
        assertTrue(registeredBrokers().isEmpty());
    }

    @Test
    public void enablingWhileTheBrokerIsInUse() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-enable");
        InMemoryBroker broker = factory.getBroker();
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);
            producer.send(session.createTextMessage("already waiting"));

            broker.enableJmx();

            assertTrue(SERVER.isRegistered(broker.objectName()));
            assertEquals(1, SERVER.getAttribute(broker.objectName(), "MessagesWaiting"));

            // Everything created before enabling keeps working.
            producer.send(session.createTextMessage("after enabling"));
            assertNotNull(consumer.receive(500));
            assertEquals(1, SERVER.getAttribute(broker.objectName(), "MessagesWaiting"));
        }
    }

    @Test
    public void disablingWhileTheBrokerIsInUse() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-disable");
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);
            producer.send(session.createTextMessage("one"));

            broker.disableJmx();

            assertFalse(SERVER.isRegistered(broker.objectName()));
            assertFalse(broker.isJmxEnabled());
            producer.send(session.createTextMessage("two"));
            assertNotNull(consumer.receive(500));
            assertNotNull(consumer.receive(500));
        }
    }

    @Test
    public void reEnablingAfterDisabling() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-reenable");
        InMemoryBroker broker = factory.getBroker();

        broker.enableJmx();
        broker.disableJmx();
        broker.enableJmx();

        assertTrue(SERVER.isRegistered(broker.objectName()));
        assertEquals(1, registeredBrokers().size());
        assertEquals(0, SERVER.getAttribute(broker.objectName(), "MessagesWaiting"));
    }

    @Test
    public void enablingTwiceDoesNotFail() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-twice");
        InMemoryBroker broker = factory.getBroker();

        broker.enableJmx();
        broker.enableJmx();

        assertTrue(broker.isJmxEnabled());
        assertEquals(1, registeredBrokers().size());

        broker.disableJmx();
        broker.disableJmx();
        assertFalse(broker.isJmxEnabled());
    }

    @Test
    public void twoBrokersAreMonitoredIndependently() throws Exception {
        InMemoryConnectionFactory first = new InMemoryConnectionFactory("jmx-one");
        InMemoryConnectionFactory second = new InMemoryConnectionFactory("jmx-two");
        first.getBroker().enableJmx();
        second.getBroker().enableJmx();

        try (Connection connection = first.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("only-on-first");
            session.createProducer(queue).send(session.createTextMessage("m"));
        }

        assertEquals(2, registeredBrokers().size());
        assertFalse(first.getBroker().objectName().equals(second.getBroker().objectName()));

        Map<?, ?> firstDepths = (Map<?, ?>) SERVER.getAttribute(first.getBroker().objectName(), "QueueDepths");
        Map<?, ?> secondDepths = (Map<?, ?>) SERVER.getAttribute(second.getBroker().objectName(), "QueueDepths");
        assertEquals(1, firstDepths.get("only-on-first"));
        assertTrue(secondDepths.isEmpty(), "each broker reports only its own queues");
    }

    @Test
    public void brokerIdContainingCharactersReservedByJmx() throws Exception {
        String brokerId = "orders:test,v=1";
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory(brokerId);
        InMemoryBroker broker = factory.getBroker();

        broker.enableJmx(); // must not raise MalformedObjectNameException

        assertTrue(SERVER.isRegistered(broker.objectName()));
        assertEquals(brokerId, SERVER.getAttribute(broker.objectName(), "BrokerId"));
    }

    @Test
    public void objectNameFormDoesNotDependOnTheBrokerId() throws Exception {
        InMemoryConnectionFactory clean = new InMemoryConnectionFactory("plain");
        InMemoryConnectionFactory reserved = new InMemoryConnectionFactory("with:reserved,chars");

        String cleanId = clean.getBroker().objectName().getKeyProperty("id");
        String reservedId = reserved.getBroker().objectName().getKeyProperty("id");

        assertEquals("\"plain\"", cleanId, "a clean Broker ID is quoted like any other");
        assertTrue(reservedId.startsWith("\"") && reservedId.endsWith("\""), reservedId);
        assertEquals("it.baccan.vmjms", clean.getBroker().objectName().getDomain());
        assertEquals("Broker", clean.getBroker().objectName().getKeyProperty("type"));
    }

    @Test
    public void destroyingABrokerUnregistersItsMBean() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-destroy");
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();
        ObjectName name = broker.objectName();
        assertTrue(SERVER.isRegistered(name));

        assertTrue(InMemoryBrokerRegistry.destroy("jmx-destroy"));

        assertFalse(SERVER.isRegistered(name));
        assertFalse(broker.isJmxEnabled());
    }

    @Test
    public void monitoringLeavesNothingRegisteredAfterCleanup() throws Exception {
        new InMemoryConnectionFactory("jmx-cleanup-a").getBroker().enableJmx();
        new InMemoryConnectionFactory("jmx-cleanup-b").getBroker().enableJmx();
        new InMemoryConnectionFactory("jmx-cleanup-c").getBroker().enableJmx();
        assertEquals(3, registeredBrokers().size());

        InMemoryBrokerRegistry.clear();

        assertTrue(registeredBrokers().isEmpty(), "clear() must leave no MBean behind");
    }

    // ------------------------------------------------------------------
    // Metrics
    // ------------------------------------------------------------------

    @Test
    public void currentDepthReflectsTraffic() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-depth");
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("orders");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            for (int i = 0; i < 3; i++) {
                producer.send(session.createTextMessage("m" + i));
            }
            assertNotNull(consumer.receive(500));

            ObjectName name = broker.objectName();
            assertEquals(2, SERVER.getAttribute(name, "MessagesWaiting"));
            assertEquals(1, SERVER.getAttribute(name, "QueueCount"));
            Map<?, ?> depths = (Map<?, ?>) SERVER.getAttribute(name, "QueueDepths");
            assertEquals(2, depths.get("orders"));
            assertEquals(3L, SERVER.getAttribute(name, "EnqueuedCount"));
            assertEquals(1L, SERVER.getAttribute(name, "DeliveredCount"));
        }
    }

    @Test
    public void rejectionsAreCounted() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-rejected", 1);
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);

            producer.send(session.createTextMessage("accepted"));
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected-1")));
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected-2")));

            ObjectName name = broker.objectName();
            assertEquals(2L, SERVER.getAttribute(name, "RejectedCount"));
            assertEquals(1L, SERVER.getAttribute(name, "EnqueuedCount"),
                    "a rejected message never entered a queue");
        }
    }

    @Test
    public void reclaimedMessagesAreCounted() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-expired");
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            producer.setTimeToLive(50);
            producer.send(session.createTextMessage("one"));
            producer.send(session.createTextMessage("two"));
            Thread.sleep(120);

            ObjectName name = broker.objectName();
            SERVER.invoke(name, "sweepExpiredNow", new Object[0], new String[0]);

            assertEquals(2L, SERVER.getAttribute(name, "ExpiredCount"));
            assertEquals(0L, SERVER.getAttribute(name, "DeliveredCount"),
                    "an expired message is not a delivered one");
            assertEquals(0, SERVER.getAttribute(name, "MessagesWaiting"));
        }
    }

    @Test
    public void purgedMessagesAreCountedAsPurged() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-purged");
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            for (int i = 0; i < 3; i++) {
                producer.send(session.createTextMessage("m" + i));
            }

            ObjectName name = broker.objectName();
            Object removed = SERVER.invoke(name, "purgeQueue",
                    new Object[]{"q"}, new String[]{"java.lang.String"});

            assertEquals(3, removed);
            assertEquals(3L, SERVER.getAttribute(name, "PurgedCount"));
            assertEquals(0L, SERVER.getAttribute(name, "ExpiredCount"),
                    "a purged message is not an expired one");
            assertEquals(0L, SERVER.getAttribute(name, "DeliveredCount"));
        }
    }

    @Test
    public void countersReconcileWithTheCurrentDepth() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-reconcile", 6);
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();
        ObjectName name = broker.objectName();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            // enqueued, then delivered
            for (int i = 0; i < 4; i++) {
                producer.send(session.createTextMessage("live" + i));
            }
            assertNotNull(consumer.receive(500));

            // enqueued, then expired
            producer.setTimeToLive(40);
            producer.send(session.createTextMessage("expiring-1"));
            producer.send(session.createTextMessage("expiring-2"));
            Thread.sleep(120);
            SERVER.invoke(name, "sweepExpiredNow", new Object[0], new String[0]);

            // enqueued up to the limit, then rejected
            producer.setTimeToLive(0);
            for (int i = 0; i < 3; i++) {
                producer.send(session.createTextMessage("more" + i));
            }
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected")));

            // enqueued, then purged
            int purged = (Integer) SERVER.invoke(name, "purgeQueue",
                    new Object[]{"q"}, new String[]{"java.lang.String"});
            assertEquals(6, purged);

            long enqueued = (Long) SERVER.getAttribute(name, "EnqueuedCount");
            long delivered = (Long) SERVER.getAttribute(name, "DeliveredCount");
            long expired = (Long) SERVER.getAttribute(name, "ExpiredCount");
            long purgedCount = (Long) SERVER.getAttribute(name, "PurgedCount");
            long rejected = (Long) SERVER.getAttribute(name, "RejectedCount");
            int waiting = (Integer) SERVER.getAttribute(name, "MessagesWaiting");

            assertEquals(9L, enqueued);
            assertEquals(1L, delivered);
            assertEquals(2L, expired);
            assertEquals(6L, purgedCount);
            assertEquals(1L, rejected);
            assertEquals(waiting, enqueued - delivered - expired - purgedCount,
                    "the counters must reconcile with the current depth");
            assertEquals(0, waiting);
        }
    }

    @Test
    public void theConfiguredLimitIsReadOnly() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-readonly", 4);
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();
        ObjectName name = broker.objectName();

        assertEquals(4, SERVER.getAttribute(name, "MaxMessages"));
        assertThrows(Exception.class,
                () -> SERVER.setAttribute(name, new Attribute("MaxMessages", 99)));

        assertEquals(4, SERVER.getAttribute(name, "MaxMessages"));
        assertEquals(4, broker.getMaxMessages());
    }

    @Test
    public void brokerIdIsReportedAsSupplied() throws Exception {
        String brokerId = "reported:as,supplied";
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory(brokerId);
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();

        assertEquals(brokerId, SERVER.getAttribute(broker.objectName(), "BrokerId"));
        assertTrue(broker.objectName().getKeyProperty("id").contains(brokerId));
    }

    @Test
    public void purgingAQueueFreesCapacity() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("jmx-purge-capacity", 2);
        InMemoryBroker broker = factory.getBroker();
        broker.enableJmx();
        ObjectName name = broker.objectName();

        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            producer.send(session.createTextMessage("one"));
            producer.send(session.createTextMessage("two"));
            assertThrows(CapacityExceededException.class,
                    () -> producer.send(session.createTextMessage("rejected")));

            int removed = (Integer) SERVER.invoke(name, "purgeQueue",
                    new Object[]{"q"}, new String[]{"java.lang.String"});

            assertEquals(2, removed);
            assertEquals(0, SERVER.getAttribute(name, "MessagesWaiting"));
            Map<?, ?> depths = (Map<?, ?>) SERVER.getAttribute(name, "QueueDepths");
            assertEquals(0, depths.get("q"));

            producer.send(session.createTextMessage("accepted again"));
            assertEquals(1, SERVER.getAttribute(name, "MessagesWaiting"));
        }
    }

    private static java.util.Set<ObjectName> registeredBrokers() throws Exception {
        return SERVER.queryNames(new ObjectName("it.baccan.vmjms:type=Broker,*"), null);
    }
}
