/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.exception.UnsupportedFeatureException;
import jakarta.jms.*;
import java.util.*;

/**
 * A connection to an in-memory broker, owning the sessions created from it.
 *
 * <p>Obtained from {@link InMemoryConnectionFactory#createConnection()}. Closing it
 * cascades: every session it owns is closed, with their producers and consumers, and a
 * thread blocked in {@code receive()} is unblocked with a
 * {@code jakarta.jms.IllegalStateException} rather than left waiting. Closing is
 * idempotent and a failure while closing one session does not stop the others.</p>
 *
 * <p>Closing a connection does not touch the broker: the messages already sent stay
 * queued and a consumer created later from another connection on the same Broker ID
 * still receives them. Destroying a broker is explicit, through
 * {@link it.baccan.vmjms.broker.InMemoryBrokerRegistry#destroy(String)}.</p>
 *
 * <h2>Deviations from the JMS specification</h2>
 *
 * <ul>
 *   <li>{@link #start()} and {@link #stop()} are accepted and do nothing: delivery is
 *       never gated on the state of the connection, so a consumer receives messages
 *       whether or not {@code start()} was called.</li>
 *   <li>An {@code ExceptionListener} can be set and read back but is never called, since
 *       there is no connection to lose and no asynchronous delivery to fail.</li>
 *   <li>The {@code createConnectionConsumer} forms throw
 *       {@link UnsupportedFeatureException}: application servers are not a target of this
 *       provider.</li>
 * </ul>
 */
public class InMemoryConnection implements Connection {
    private final InMemoryBroker broker;
    private final List<InMemorySession> sessions = Collections.synchronizedList(new ArrayList<>());
    private String clientID;
    private ExceptionListener exceptionListener;
    private volatile boolean closed = false;

    /**
     * Creates a connection onto a broker. Applications obtain one from
     * {@link InMemoryConnectionFactory#createConnection()} instead of calling this.
     *
     * @param broker the broker whose queues this connection works on
     */
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
            // Jakarta Messaging 3.1, the version of the api this provider is built on.
            @Override
            public String getJMSVersion() { return "3.1"; }
            @Override
            public int getJMSMajorVersion() { return 3; }
            @Override
            public int getJMSMinorVersion() { return 1; }
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
        throw new UnsupportedFeatureException("ConnectionConsumer",
                "Hosting this provider in an application server is out of scope.");
    }

    @Override
    public ConnectionConsumer createDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedFeatureException("ConnectionConsumer",
                "Hosting this provider in an application server is out of scope.");
    }

    @Override
    public ConnectionConsumer createSharedConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedFeatureException("ConnectionConsumer",
                "Hosting this provider in an application server is out of scope.");
    }

    @Override
    public ConnectionConsumer createSharedDurableConnectionConsumer(Topic topic, String subscriptionName, String messageSelector, ServerSessionPool sessionPool, int maxMessages) throws JMSException {
        throw new UnsupportedFeatureException("ConnectionConsumer",
                "Hosting this provider in an application server is out of scope.");
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

    /**
     * Whether this connection has been closed.
     *
     * @return true once {@link #close()} has run
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Forgets a session that has closed itself, so that closing the connection does not
     * close it twice. Called by {@link InMemorySession#close()}.
     *
     * @param session the session to stop tracking
     */
    public void removeSession(InMemorySession session) {
        sessions.remove(session);
    }
}
