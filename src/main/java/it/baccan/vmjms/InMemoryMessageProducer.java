package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import jakarta.jms.*;

/**
 * Implementation of jakarta.jms.MessageProducer interface.
 */
public class InMemoryMessageProducer implements MessageProducer {
    private final InMemoryBroker broker;
    private final Queue defaultDestination;
    private boolean disableMessageID = false;
    private boolean disableMessageTimestamp = false;
    private int deliveryMode = DeliveryMode.NON_PERSISTENT;
    private int priority = Message.DEFAULT_PRIORITY;
    private long timeToLive = Message.DEFAULT_TIME_TO_LIVE;
    private long deliveryDelay = 0;
    private volatile boolean closed = false;

    public InMemoryMessageProducer(InMemoryBroker broker, Queue defaultDestination) {
        this.broker = broker;
        this.defaultDestination = defaultDestination;
    }

    private void checkNotClosed() throws jakarta.jms.IllegalStateException {
        if (closed) {
            throw new jakarta.jms.IllegalStateException("Producer is closed");
        }
    }

    @Override
    public void setDisableMessageID(boolean value) throws JMSException {
        checkNotClosed();
        this.disableMessageID = value;
    }

    @Override
    public boolean getDisableMessageID() throws JMSException {
        checkNotClosed();
        return disableMessageID;
    }

    @Override
    public void setDisableMessageTimestamp(boolean value) throws JMSException {
        checkNotClosed();
        this.disableMessageTimestamp = value;
    }

    @Override
    public boolean getDisableMessageTimestamp() throws JMSException {
        checkNotClosed();
        return disableMessageTimestamp;
    }

    @Override
    public void setDeliveryMode(int deliveryMode) throws JMSException {
        checkNotClosed();
        this.deliveryMode = deliveryMode;
    }

    @Override
    public int getDeliveryMode() throws JMSException {
        checkNotClosed();
        return deliveryMode;
    }

    @Override
    public void setPriority(int defaultPriority) throws JMSException {
        checkNotClosed();
        this.priority = defaultPriority;
    }

    @Override
    public int getPriority() throws JMSException {
        checkNotClosed();
        return priority;
    }

    @Override
    public void setTimeToLive(long timeToLive) throws JMSException {
        checkNotClosed();
        this.timeToLive = timeToLive;
    }

    @Override
    public long getTimeToLive() throws JMSException {
        checkNotClosed();
        return timeToLive;
    }

    @Override
    public void setDeliveryDelay(long deliveryDelay) throws JMSException {
        checkNotClosed();
        this.deliveryDelay = deliveryDelay;
    }

    @Override
    public long getDeliveryDelay() throws JMSException {
        checkNotClosed();
        return deliveryDelay;
    }

    @Override
    public Queue getDestination() throws JMSException {
        checkNotClosed();
        return defaultDestination;
    }

    @Override
    public void close() throws JMSException {
        closed = true;
    }

    @Override
    public void send(Message message) throws JMSException {
        send(defaultDestination, message);
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        send(defaultDestination, message, deliveryMode, priority, timeToLive);
    }

    @Override
    public void send(Destination destination, Message message) throws JMSException {
        send(destination, message, this.deliveryMode, this.priority, this.timeToLive);
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive) throws JMSException {
        checkNotClosed();
        if (destination == null) {
            throw new InvalidDestinationException("Destination cannot be null");
        }
        if (!(destination instanceof Queue)) {
            throw new InvalidDestinationException("Only Queue destinations are supported");
        }

        Queue queue = (Queue) destination;

        // Set mandatory JMS headers
        if (!disableMessageID) {
            message.setJMSMessageID(broker.generateNextMessageId());
        }
        if (!disableMessageTimestamp) {
            message.setJMSTimestamp(System.currentTimeMillis());
        }
        message.setJMSDestination(queue);
        message.setJMSDeliveryMode(deliveryMode);
        message.setJMSPriority(priority);
        if (timeToLive > 0) {
            message.setJMSExpiration(System.currentTimeMillis() + timeToLive);
        } else {
            message.setJMSExpiration(0);
        }

        // If BytesMessage, ensure it is in read-only mode so consumer can read immediately
        if (message instanceof BytesMessage) {
            ((BytesMessage) message).reset();
        }

        // Hand the message to the broker, which accounts for the capacity it occupies and
        // rejects the send with a CapacityExceededException if the broker is full.
        broker.send(queue.getQueueName(), message);
    }

    @Override
    public void send(Message message, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedOperationException("Asynchronous send with CompletionListener is not supported");
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedOperationException("Asynchronous send with CompletionListener is not supported");
    }

    @Override
    public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedOperationException("Asynchronous send with CompletionListener is not supported");
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedOperationException("Asynchronous send with CompletionListener is not supported");
    }
}
