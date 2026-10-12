package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import jakarta.jms.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implementation of jakarta.jms.MessageConsumer interface with synchronous blocking reads
 * and robust interruption on close.
 */
public class InMemoryMessageConsumer implements MessageConsumer {
    private final Queue destination;
    private final BlockingQueue<Message> brokerQueue;
    private final AtomicReference<Thread> waitingThread = new AtomicReference<>();
    private volatile boolean closed = false;

    public InMemoryMessageConsumer(InMemoryBroker broker, Queue destination) throws JMSException {
        if (destination == null) {
            throw new InvalidDestinationException("Destination cannot be null");
        }
        this.destination = destination;
        this.brokerQueue = broker.getOrCreateQueue(destination.getQueueName());
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
            Message msg = brokerQueue.take();
            checkNotClosed();
            return msg;
        } catch (InterruptedException e) {
            if (closed) {
                jakarta.jms.IllegalStateException ex = new jakarta.jms.IllegalStateException("Consumer was closed during receive");
                ex.setLinkedException(e);
                throw ex;
            }
            Thread.currentThread().interrupt(); // Restore interrupt status
            JMSException jmse = new JMSException("Receive interrupted");
            jmse.setLinkedException(e);
            throw jmse;
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
            Message msg = brokerQueue.poll(timeout, TimeUnit.MILLISECONDS);
            checkNotClosed();
            return msg;
        } catch (InterruptedException e) {
            if (closed) {
                jakarta.jms.IllegalStateException ex = new jakarta.jms.IllegalStateException("Consumer was closed during receive");
                ex.setLinkedException(e);
                throw ex;
            }
            Thread.currentThread().interrupt(); // Restore interrupt status
            JMSException jmse = new JMSException("Receive interrupted");
            jmse.setLinkedException(e);
            throw jmse;
        } finally {
            waitingThread.set(null);
        }
    }

    @Override
    public Message receiveNoWait() throws JMSException {
        checkNotClosed();
        Message msg = brokerQueue.poll();
        checkNotClosed();
        return msg;
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
