package it.baccan.vmjms.broker;

import jakarta.jms.Message;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An isolated in-memory broker state holding message queues and managing message ID sequence.
 */
public class InMemoryBroker {
    private static final AtomicLong brokerInstanceCounter = new AtomicLong(0);
    
    private final ConcurrentMap<String, BlockingQueue<Message>> queues = new ConcurrentHashMap<>();
    private final AtomicLong messageIdSequence = new AtomicLong(0);
    private final String brokerId;
    private final long instanceId;

    public InMemoryBroker(String brokerId) {
        if (brokerId == null) {
            throw new IllegalArgumentException("Broker ID cannot be null");
        }
        this.brokerId = brokerId;
        this.instanceId = brokerInstanceCounter.incrementAndGet();
    }

    /**
     * Retrieves or creates a FIFO LinkedBlockingQueue for the specified queue name.
     */
    public BlockingQueue<Message> getOrCreateQueue(String queueName) {
        if (queueName == null) {
            throw new IllegalArgumentException("Queue name cannot be null");
        }
        return queues.computeIfAbsent(queueName, name -> new LinkedBlockingQueue<>());
    }

    /**
     * Generates a unique sequential message ID: ID:<brokerId>:<instanceId>:<sequence>
     */
    public String generateNextMessageId() {
        return "ID:" + brokerId + ":" + instanceId + ":" + messageIdSequence.incrementAndGet();
    }

    public String getBrokerId() {
        return brokerId;
    }

    public long getInstanceId() {
        return instanceId;
    }
}
