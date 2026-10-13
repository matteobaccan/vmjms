package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The entry point of the provider: a {@code ConnectionFactory} onto an in-memory broker.
 *
 * <p>A factory with no Broker ID owns a private broker, which makes one test independent
 * of the next. Two factories built with the same Broker ID share one broker, and so share
 * its queues, its capacity and its counters; the broker outlives the connections and the
 * factories, until it is destroyed through
 * {@link it.baccan.vmjms.broker.InMemoryBrokerRegistry#destroy(String)}.</p>
 *
 * <p>A factory may cap the number of messages its broker holds. The cap is broker-wide, is
 * fixed when the broker is created, and defaults to the system property
 * {@code vmjms.broker.maxMessages} - or to effectively unbounded when that is not set
 * either. Supplying an explicit maximum for a Broker ID that already exists with a
 * different one fails immediately with a {@code jakarta.jms.JMSRuntimeException} carrying
 * {@link VmjmsErrorCodes#CONFLICTING_BROKER_LIMIT}, because one of the two limits would
 * otherwise be silently ignored.</p>
 *
 * <h2>Deviations from the JMS specification</h2>
 *
 * <p>The simplified API introduced in JMS 2.0 is not implemented: every
 * {@code createContext} form throws {@code UnsupportedOperationException}. Use
 * {@link #createConnection()} and work with sessions.</p>
 */
public class InMemoryConnectionFactory implements ConnectionFactory {
    private static final AtomicLong anonymousBrokerCounter = new AtomicLong(0);

    private final InMemoryBroker broker;

    /**
     * Creates a factory owning a private broker, with the default maximum number of messages.
     */
    public InMemoryConnectionFactory() {
        this(generatedBrokerId(), null);
    }

    /**
     * Creates a factory resolving the given Broker ID, with the default maximum number of
     * messages: the limit of the broker if it already exists, otherwise the default.
     */
    public InMemoryConnectionFactory(String brokerId) {
        this(brokerId, null);
    }

    /**
     * Creates a factory owning a private broker holding at most {@code maxMessages}
     * messages across all its queues.
     */
    public InMemoryConnectionFactory(int maxMessages) {
        this(generatedBrokerId(), Integer.valueOf(maxMessages));
    }

    /**
     * Creates a factory resolving the given Broker ID, requiring that the broker hold at
     * most {@code maxMessages} messages across all its queues.
     *
     * @throws jakarta.jms.JMSRuntimeException if the broker already exists with a different
     *                                         maximum
     */
    public InMemoryConnectionFactory(String brokerId, int maxMessages) {
        this(brokerId, Integer.valueOf(maxMessages));
    }

    private InMemoryConnectionFactory(String brokerId, Integer maxMessages) {
        this.broker = InMemoryBrokerRegistry.getOrCreate(brokerId, maxMessages);
    }

    private static String generatedBrokerId() {
        return Long.toHexString(System.currentTimeMillis()).toLowerCase()
                + "-" + anonymousBrokerCounter.incrementAndGet();
    }

    @Override
    public Connection createConnection() throws JMSException {
        return new InMemoryConnection(broker);
    }

    @Override
    public Connection createConnection(String userName, String password) throws JMSException {
        return createConnection();
    }

    @Override
    public JMSContext createContext() {
        throw new UnsupportedOperationException("JMSContext (JMS 2.0 Simplified API) is not supported. Use createConnection() instead.");
    }

    @Override
    public JMSContext createContext(int sessionMode) {
        throw new UnsupportedOperationException("JMSContext (JMS 2.0 Simplified API) is not supported. Use createConnection() instead.");
    }

    @Override
    public JMSContext createContext(String userName, String password) {
        throw new UnsupportedOperationException("JMSContext (JMS 2.0 Simplified API) is not supported. Use createConnection() instead.");
    }

    @Override
    public JMSContext createContext(String userName, String password, int sessionMode) {
        throw new UnsupportedOperationException("JMSContext (JMS 2.0 Simplified API) is not supported. Use createConnection() instead.");
    }

    /**
     * The broker this factory hands to the connections it creates.
     *
     * <p>Outside the JMS API: useful to read the counters of the broker, to enable its JMX
     * monitoring or to purge it in a test.</p>
     *
     * @return the broker of this factory, never null
     */
    public InMemoryBroker getBroker() {
        return broker;
    }
}
