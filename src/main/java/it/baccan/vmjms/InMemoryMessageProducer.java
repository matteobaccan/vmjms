package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.exception.UnsupportedFeatureException;
import it.baccan.vmjms.exception.CapacityExceededException;
import jakarta.jms.*;

/**
 * A producer that hands messages to a broker queue.
 *
 * <p>Obtained from {@link InMemorySession#createProducer(jakarta.jms.Destination)}. Every
 * {@code send} form ends in the broker, which accounts for the capacity the message
 * occupies and refuses the send with a {@link CapacityExceededException} when the broker
 * is already holding its maximum. A refused send throws immediately instead of blocking,
 * and the message is not queued.</p>
 *
 * <p>Before handing a message over, the producer stamps the headers the specification
 * makes its responsibility: the message ID, unless disabled through
 * {@link #setDisableMessageID(boolean)}, the timestamp, unless disabled through
 * {@link #setDisableMessageTimestamp(boolean)}, the destination, the delivery mode, the
 * priority, and the absolute expiration computed from the time to live. A time to live of
 * zero, which is the default, means the message never expires. A {@code BytesMessage} is
 * reset to read-only so the consumer can read it straight away.</p>
 *
 * <h2>Deviations from the JMS specification</h2>
 *
 * <ul>
 *   <li>Only {@code Queue} destinations are accepted; anything else, including null,
 *       fails with {@code jakarta.jms.InvalidDestinationException}.</li>
 *   <li>{@code JMSPriority} is stamped on the message and reported back, but delivery is
 *       FIFO per queue: a higher priority does not overtake.</li>
 *   <li>The delivery delay is recorded and reported, and ignored: a message is available
 *       to a consumer as soon as it is sent.</li>
 *   <li>Nothing is persisted, so {@code PERSISTENT} and {@code NON_PERSISTENT} behave
 *       alike and a JVM restart starts empty.</li>
 *   <li>The asynchronous {@code send} forms taking a {@code CompletionListener} throw
 *       {@link UnsupportedFeatureException}.</li>
 * </ul>
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

    /**
     * Creates a producer. Applications obtain one from
     * {@link InMemorySession#createProducer(jakarta.jms.Destination)} instead of calling
     * this.
     *
     * @param broker             the broker the messages are sent to
     * @param defaultDestination the queue used by the {@code send} forms that take no
     *                           destination; may be null, in which case every send must
     *                           name its own
     */
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
        throw new UnsupportedFeatureException("Asynchronous send with a CompletionListener",
                "Use one of the synchronous send forms.");
    }

    @Override
    public void send(Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedFeatureException("Asynchronous send with a CompletionListener",
                "Use one of the synchronous send forms.");
    }

    @Override
    public void send(Destination destination, Message message, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedFeatureException("Asynchronous send with a CompletionListener",
                "Use one of the synchronous send forms.");
    }

    @Override
    public void send(Destination destination, Message message, int deliveryMode, int priority, long timeToLive, CompletionListener completionListener) throws JMSException {
        throw new UnsupportedFeatureException("Asynchronous send with a CompletionListener",
                "Use one of the synchronous send forms.");
    }
}
