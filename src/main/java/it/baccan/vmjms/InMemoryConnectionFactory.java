package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation of jakarta.jms.ConnectionFactory containing a private or shared InMemoryBroker.
 */
public class InMemoryConnectionFactory implements ConnectionFactory {
    private static final AtomicLong anonymousBrokerCounter = new AtomicLong(0);

    private final InMemoryBroker broker;

    public InMemoryConnectionFactory() {
        this(Long.toHexString(System.currentTimeMillis()).toLowerCase()
                + "-" + anonymousBrokerCounter.incrementAndGet());
    }

    public InMemoryConnectionFactory(String brokerId) {
        this.broker = InMemoryBrokerRegistry.getOrCreate(brokerId);
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

    public InMemoryBroker getBroker() {
        return broker;
    }
}
