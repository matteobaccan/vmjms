package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.message.InMemoryBytesMessage;
import it.baccan.vmjms.message.InMemoryMessage;
import it.baccan.vmjms.message.InMemoryTextMessage;
import jakarta.jms.*;
import java.io.Serializable;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;

/**
 * A session on an in-memory connection: it creates queues, messages, producers and
 * consumers, and owns them until it closes.
 *
 * <p>Obtained from {@link InMemoryConnection#createSession(boolean, int)}. As in the JMS
 * threading model, a session and everything created from it are meant to be used by one
 * thread at a time; {@link #close()} is the exception and may be called from another
 * thread to unblock a consumer. Closing cascades to the producers and the consumers of
 * the session and is idempotent.</p>
 *
 * <p>{@link #createQueue(String)} accepts query parameters after a {@code ?}, which
 * {@link InMemoryQueue} parses and carries for the application to read.</p>
 *
 * <h2>Deviations from the JMS specification</h2>
 *
 * <ul>
 *   <li>No transactions: {@code commit()} and {@code rollback()} throw
 *       {@code UnsupportedOperationException}, even on a session created with
 *       {@code transacted} set to true, which is reported back faithfully by
 *       {@link #getTransacted()}.</li>
 *   <li>Delivery always behaves as {@code AUTO_ACKNOWLEDGE}. The acknowledge mode is
 *       recorded and reported, {@link #recover()} does nothing, and
 *       {@code Message.acknowledge()} does nothing.</li>
 *   <li>Queues only: every {@code Topic} form, durable and shared subscriptions and
 *       {@code unsubscribe} throw {@code UnsupportedOperationException}.</li>
 *   <li>Two body types only: {@code createTextMessage} and {@code createBytesMessage}
 *       work, while {@code MapMessage}, {@code ObjectMessage} and {@code StreamMessage}
 *       throw {@code UnsupportedOperationException}.</li>
 *   <li>No asynchronous delivery: the {@code MessageListener} accessors and the
 *       {@code QueueBrowser}, temporary-destination and {@code run()} forms are either
 *       unsupported or do nothing.</li>
 *   <li>Message selectors are accepted and ignored, so a consumer created with one
 *       receives every message on its queue.</li>
 * </ul>
 */
public class InMemorySession implements Session {
    private final InMemoryBroker broker;
    private final InMemoryConnection connection;
    private final boolean transacted;
    private final int acknowledgeMode;
    private final List<InMemoryMessageProducer> producers = Collections.synchronizedList(new ArrayList<>());
    private final List<InMemoryMessageConsumer> consumers = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean closed = false;

    /**
     * Creates a session on a connection. Applications obtain one from
     * {@link InMemoryConnection#createSession(boolean, int)} instead of calling this.
     *
     * @param broker          the broker whose queues this session works on
     * @param connection      the connection this session belongs to
     * @param transacted      recorded and reported by {@link #getTransacted()}; this
     *                        provider implements no transactions, so a transacted session
     *                        can be created but neither committed nor rolled back
     * @param acknowledgeMode recorded and reported by {@link #getAcknowledgeMode()};
     *                        delivery always behaves as {@code AUTO_ACKNOWLEDGE}
     */
    public InMemorySession(InMemoryBroker broker, InMemoryConnection connection, boolean transacted, int acknowledgeMode) {
        this.broker = broker;
        this.connection = connection;
        this.transacted = transacted;
        this.acknowledgeMode = acknowledgeMode;
    }

    private void checkNotClosed() throws jakarta.jms.IllegalStateException {
        if (closed) {
            throw new jakarta.jms.IllegalStateException("Session is closed");
        }
    }

    @Override
    public BytesMessage createBytesMessage() throws JMSException {
        checkNotClosed();
        return new InMemoryBytesMessage();
    }

    @Override
    public MapMessage createMapMessage() throws JMSException {
        throw new UnsupportedOperationException("MapMessage is not supported");
    }

    @Override
    public Message createMessage() throws JMSException {
        checkNotClosed();
        return new InMemoryMessage();
    }

    @Override
    public ObjectMessage createObjectMessage() throws JMSException {
        throw new UnsupportedOperationException("ObjectMessage is not supported");
    }

    @Override
    public ObjectMessage createObjectMessage(Serializable object) throws JMSException {
        throw new UnsupportedOperationException("ObjectMessage is not supported");
    }

    @Override
    public StreamMessage createStreamMessage() throws JMSException {
        throw new UnsupportedOperationException("StreamMessage is not supported");
    }

    @Override
    public TextMessage createTextMessage() throws JMSException {
        checkNotClosed();
        return new InMemoryTextMessage();
    }

    @Override
    public TextMessage createTextMessage(String text) throws JMSException {
        checkNotClosed();
        return new InMemoryTextMessage(text);
    }

    @Override
    public boolean getTransacted() throws JMSException {
        checkNotClosed();
        return transacted;
    }

    @Override
    public int getAcknowledgeMode() throws JMSException {
        checkNotClosed();
        return acknowledgeMode;
    }

    @Override
    public void commit() throws JMSException {
        throw new UnsupportedOperationException("Transactions are not supported by this in-memory session");
    }

    @Override
    public void rollback() throws JMSException {
        throw new UnsupportedOperationException("Transactions are not supported by this in-memory session");
    }

    @Override
    public void close() throws JMSException {
        if (closed) {
            return;
        }
        closed = true;

        // Close all producers using a copy to be safe
        List<InMemoryMessageProducer> producersCopy;
        synchronized (producers) {
            producersCopy = new ArrayList<>(producers);
        }
        for (InMemoryMessageProducer producer : producersCopy) {
            try {
                producer.close();
            } catch (Exception e) {
                // Suppress
            }
        }
        synchronized (producers) {
            producers.clear();
        }

        // Close all consumers using a copy to be safe
        List<InMemoryMessageConsumer> consumersCopy;
        synchronized (consumers) {
            consumersCopy = new ArrayList<>(consumers);
        }
        for (InMemoryMessageConsumer consumer : consumersCopy) {
            try {
                consumer.close();
            } catch (Exception e) {
                // Suppress
            }
        }
        synchronized (consumers) {
            consumers.clear();
        }

        connection.removeSession(this);
    }

    @Override
    public void recover() throws JMSException {
        checkNotClosed();
        // No-op for simple session
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
    public void run() {
        // No-op
    }

    @Override
    public MessageProducer createProducer(Destination destination) throws JMSException {
        checkNotClosed();
        InMemoryMessageProducer producer = new InMemoryMessageProducer(broker, (Queue) destination);
        producers.add(producer);
        return producer;
    }

    @Override
    public MessageConsumer createConsumer(Destination destination) throws JMSException {
        checkNotClosed();
        InMemoryMessageConsumer consumer = new InMemoryMessageConsumer(broker, (Queue) destination);
        consumers.add(consumer);
        return consumer;
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector) throws JMSException {
        return createConsumer(destination);
    }

    @Override
    public MessageConsumer createConsumer(Destination destination, String messageSelector, boolean noLocal) throws JMSException {
        return createConsumer(destination);
    }

    @Override
    public Queue createQueue(String queueName) throws JMSException {
        checkNotClosed();
        return new InMemoryQueue(queueName);
    }

    @Override
    public Topic createTopic(String topicName) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported. Use Queues instead.");
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public TopicSubscriber createDurableSubscriber(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public MessageConsumer createDurableConsumer(Topic topic, String name, String messageSelector, boolean noLocal) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public MessageConsumer createSharedConsumer(Topic topic, String sharedSubscriptionName, String messageSelector) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public MessageConsumer createSharedDurableConsumer(Topic topic, String name, String messageSelector) throws JMSException {
        throw new UnsupportedOperationException("Topics are not supported.");
    }

    @Override
    public QueueBrowser createBrowser(Queue queue) throws JMSException {
        throw new UnsupportedOperationException("QueueBrowser is not supported.");
    }

    @Override
    public QueueBrowser createBrowser(Queue queue, String messageSelector) throws JMSException {
        throw new UnsupportedOperationException("QueueBrowser is not supported.");
    }

    @Override
    public TemporaryQueue createTemporaryQueue() throws JMSException {
        throw new UnsupportedOperationException("Temporary queues are not supported.");
    }

    @Override
    public TemporaryTopic createTemporaryTopic() throws JMSException {
        throw new UnsupportedOperationException("Temporary topics are not supported.");
    }

    @Override
    public void unsubscribe(String name) throws JMSException {
        throw new UnsupportedOperationException("Unsubscribe is not supported.");
    }

    /**
     * Whether this session has been closed.
     *
     * @return true once {@link #close()} has run
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Forgets a producer that has closed itself, so that closing the session does not
     * close it twice. Called by {@link InMemoryMessageProducer#close()}.
     *
     * @param producer the producer to stop tracking
     */
    public void removeProducer(InMemoryMessageProducer producer) {
        producers.remove(producer);
    }

    /**
     * Forgets a consumer that has closed itself, so that closing the session does not
     * close it twice. Called by {@link InMemoryMessageConsumer#close()}.
     *
     * @param consumer the consumer to stop tracking
     */
    public void removeConsumer(InMemoryMessageConsumer consumer) {
        consumers.remove(consumer);
    }
}
