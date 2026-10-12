package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import jakarta.jms.*;
import java.util.*;

/**
 * Implementation of jakarta.jms.Connection managing sessions and cascading closes.
 */
public class InMemoryConnection implements Connection {
    private final InMemoryBroker broker;
    private final List<InMemorySession> sessions = Collections.synchronizedList(new ArrayList<>());
    private String clientID;
    private ExceptionListener exceptionListener;
    private volatile boolean closed = false;

    public InMemoryConnection(InMemoryBroker broker) {
        this.broker = broker;
    }

    private void checkNotClosed() throws jakarta.jms.IllegalStateException {
        if (closed) {
            throw new jakarta.jms.IllegalStateException("Connection is closed");
        }
    }

    @Override
    public Session createSession(boolean transacted, int acknowledgeMode) throws JMSException {
        checkNotClosed();
        InMemorySession session = new InMemorySession(broker, this, transacted, acknowledgeMode);
        sessions.add(session);
        return session;
    }

    @Override
    public Session createSession(int sessionMode) throws JMSException {
        return createSession(false, sessionMode);
    }

    @Override
    public Session createSession() throws JMSException {
        return createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    @Override
    public String getClientID() throws JMSException {
        checkNotClosed();
        return clientID;
    }

    @Override
    public void setClientID(String clientID) throws JMSException {
        checkNotClosed();
        this.clientID = clientID;
    }

    @Override
    public ConnectionMetaData getMetaData() throws JMSException {
        checkNotClosed();
        return new ConnectionMetaData() {
            @Override
            public String getJMSVersion() { return "3.0"; }
            @Override
            public int getJMSMajorVersion() { return 3; }
            @Override
            public int getJMSMinorVersion() { return 0; }
            @Override
            public String getJMSProviderName() { return "InMemoryJMS"; }
            @Override
            public String getProviderVersion() { return "1.0.0"; }
            @Override
            public int getProviderMajorVersion() { return 1; }
            @Override
            public int getProviderMinorVersion() { return 0; }
            @Override
            public Enumeration getJMSXPropertyNames() { return Collections.emptyEnumeration(); }
        };
    }

    @Override
    public ExceptionListener getExceptionListener() throws JMSException {
        checkNotClosed();
        return exceptionListener;
    }

    @Override
    public void setExceptionListener(ExceptionListener listener) throws JMSException {
        checkNotClosed();
        this.exceptionListener = listener;
    }

    @Override
    public void start() throws JMSException {
        checkNotClosed();
        // Ignore connection state for message delivery as requested
    }

    @Override
    public void stop() throws JMSException {
        checkNotClosed();
        // Ignore connection state for message delivery as requested
    }

    @Override
    public ConnectionConsumer createConnectionConsumer(Destination destination, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedOperationException("ConnectionConsumer is not supported");
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedOperationException("ConnectionConsumer is not supported");
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedOperationException("ConnectionConsumer is not supported");
    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedOperationException("ConnectionConsumer is not supported");
    }

    @Override
    public void close() throws JMSException {
        if (closed) {
            return;
        }
        closed = true;
        
        // Cascading close: close all registered sessions using a copy to avoid concurrent modification
        List<InMemorySession> sessionsCopy;
        synchronized (sessions) {
            sessionsCopy = new ArrayList<>(sessions);
        }
        
        for (InMemorySession session : sessionsCopy) {
            try {
                session.close();
            } catch (Exception e) {
                // Suppress and continue closing others
            }
        }
        
        synchronized (sessions) {
            sessions.clear();
        }
    }

    public boolean isClosed() {
        return closed;
    }

    public void removeSession(InMemorySession session) {
        sessions.remove(session);
    }
}
