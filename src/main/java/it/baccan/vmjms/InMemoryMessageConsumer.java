package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import jakarta.jms.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of jakarta.jms.MessageConsumer interface with synchronous blocking reads
 * and robust interruption on close.
 *
 * <p>Reads go through the broker, which accounts for every message it hands out and skips
 * the ones whose expiration has passed.</p>
 */
public class InMemoryMessageConsumer implements MessageConsumer {
    private final Queue destination;
    private final InMemoryBroker broker;
    private final String queueName;
    private final AtomicReference<Thread> waitingThread = new AtomicReference<>();
    private volatile boolean closed = false;

    public InMemoryMessageConsumer(InMemoryBroker broker, Queue destination) throws JMSException {
        if (destination == null) {
            throw new InvalidDestinationException("Destination cannot be null");
        }
        this.destination = destination;
        this.broker = broker;
        this.queueName = destination.getQueueName();
    }

    private void checkNotClosed() throws jakarta.jms.IllegalStateException {
        if (closed) {
            throw new jakarta.jms.IllegalStateException("Consumer is closed");
        }
    }

    @Override
    public String getMessageSelector() throws JMSException {
        checkNotClosed();
        return null;
    }

    @Override
    public MessageListener getMessageListener() throws JMSException {
        throw new UnsupportedOperationException("Asynchronous MessageListener is not supported");
    }

    @Override
    public void setMessageListener(MessageListener listener) throws JMSException {
        throw new UnsupportedOperationException("Asynchronous MessageListener is not supported");
    }

    @Override
    public Message receive() throws JMSException {
        checkNotClosed();
        waitingThread.set(Thread.currentThread());
        try {
            Message msg = broker.receive(queueName);
            checkNotClosed();
            return msg;
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            waitingThread.set(null);
        }
    }

    @Override
    public Message receive(long timeout) throws JMSException {
        checkNotClosed();
        if (timeout <= 0) {
            return receive();
        }
        waitingThread.set(Thread.currentThread());
        try {
            Message msg = broker.receive(queueName, timeout);
            checkNotClosed();
            return msg;
        } catch (InterruptedException e) {
            throw interrupted(e);
        } finally {
            waitingThread.set(null);
        }
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        checkNotClosed();
        Message msg = broker.receiveNoWait(queueName);
        checkNotClosed();
        return msg;
    }

    /**
     * Turns an interruption of the blocking wait into the right JMS failure: a closed
     * consumer reports itself as closed, anything else is reported as an interrupted
     * receive with the interrupt status restored.
     */
    private JMSException interrupted(InterruptedException e) {
        if (closed) {
            jakarta.jms.IllegalStateException ex =
                    new jakarta.jms.IllegalStateException("Consumer was closed during receive");
            ex.setLinkedException(e);
            return ex;
        }
        Thread.currentThread().interrupt(); // Restore interrupt status
        JMSException jmse = new JMSException("Receive interrupted");
        jmse.setLinkedException(e);
        return jmse;
    }

    @Override
    public void close() throws JMSException {
        if (closed) {
            return;
        }
        closed = true;

        // Interrupt any thread waiting on receive
        Thread threadToInterrupt = waitingThread.get();
        if (threadToInterrupt != null) {
            threadToInterrupt.interrupt();
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
