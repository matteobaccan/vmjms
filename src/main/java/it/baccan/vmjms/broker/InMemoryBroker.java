package it.baccan.vmjms.broker;

import it.baccan.vmjms.CapacityExceededException;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.ObjectName;

/**
 * An isolated in-memory broker state holding message queues and managing message ID sequence.
 *
 * <p>The broker is the single access path for messages: producers and consumers go through
 * {@link #send(String, Message)} and the {@code receive} methods rather than touching the
 * underlying queues, so that every message entering or leaving a queue is accounted for.
 * That accounting is what makes a broker-wide capacity limit, expiry reclamation and JMX
 * monitoring possible.</p>
 */
public class InMemoryBroker {

    private static final AtomicLong brokerInstanceCounter = new AtomicLong(0);

    private final ConcurrentMap<String, BlockingQueue<QueuedMessage>> queues = new ConcurrentHashMap<>();
    private final AtomicLong messageIdSequence = new AtomicLong(0);
    private final String brokerId;
    private final long instanceId;

    /**
     * Fixed at construction. {@code Integer.MAX_VALUE} means effectively unbounded, which
     * keeps a single code path for bounded and unbounded brokers alike.
     */
    private final int maxMessages;
    private final Semaphore capacity;

    /**
     * Number of currently enqueued messages that can expire. When it is zero the queues of
     * this broker are not scanned at all, which is exact rather than heuristic.
     */
    private final AtomicInteger expiringMessages = new AtomicInteger(0);

    private final AtomicLong enqueuedCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong expiredCount = new AtomicLong(0);
    private final AtomicLong purgedCount = new AtomicLong(0);
    private final AtomicLong rejectedCount = new AtomicLong(0);
    private final AtomicLong sweepFailureCount = new AtomicLong(0);
    private volatile String lastSweepError = "";

    private final Set<Thread> waitingThreads = ConcurrentHashMap.newKeySet();
    private volatile boolean terminated = false;

    private final Object jmxLock = new Object();
    private volatile boolean jmxEnabled = false;

    /**
     * Creates an effectively unbounded broker.
     */
    public InMemoryBroker(String brokerId) {
        this(brokerId, Integer.MAX_VALUE);
    }

    /**
     * @param brokerId    identifier of the broker, used verbatim
     * @param maxMessages maximum number of messages waiting across all queues of this
     *                    broker; {@code Integer.MAX_VALUE} for effectively unbounded
     */
    public InMemoryBroker(String brokerId, int maxMessages) {
        if (brokerId == null) {
            throw new IllegalArgumentException("Broker ID cannot be null");
        }
        if (maxMessages < 0) {
            throw new IllegalArgumentException("Maximum number of messages cannot be negative");
        }
        this.brokerId = brokerId;
        this.instanceId = brokerInstanceCounter.incrementAndGet();
        this.maxMessages = maxMessages;
        this.capacity = new Semaphore(maxMessages);
    }

    // ------------------------------------------------------------------
    // Access path
    // ------------------------------------------------------------------

    /**
     * Accepts a message for the named queue, accounting for the capacity it occupies.
     *
     * @throws CapacityExceededException      if the broker already holds its configured maximum
     * @throws jakarta.jms.IllegalStateException if the broker has been destroyed
     */
    public void send(String queueName, Message message) throws JMSException {
        checkNotTerminated();
        requireQueueName(queueName);

        QueuedMessage envelope = new QueuedMessage(message, message.getJMSExpiration());

        if (!capacity.tryAcquire()) {
            rejectedCount.incrementAndGet();
            throw new CapacityExceededException(maxMessages, queueName, getMessagesWaiting());
        }

        // Counted before the message becomes visible, so that no consumer or sweep can
        // account for it while the bookkeeping still says it is not there.
        if (envelope.canExpire()) {
            expiringMessages.incrementAndGet();
            InMemoryBrokerRegistry.ensureSweeperStarted();
        }
        enqueuedCount.incrementAndGet();

        if (!getOrCreateQueue(queueName).offer(envelope)) {
            // Unreachable with an unbounded queue; the accounting stays exact regardless.
            enqueuedCount.decrementAndGet();
            if (envelope.claim()) {
                releaseWithoutCounting(envelope);
            }
            throw new CapacityExceededException(maxMessages, queueName, getMessagesWaiting());
        }
    }

    /**
     * Blocks until a live message is available on the named queue.
     *
     * <p>{@code InterruptedException} is propagated so that the caller can distinguish its
     * own close from any other interruption; a broker destroyed while waiting surfaces as
     * {@code jakarta.jms.IllegalStateException} instead.</p>
     */
    public Message receive(String queueName) throws JMSException, InterruptedException {
        return receiveInternal(queueName, -1L);
    }

    /**
     * Waits up to {@code timeoutMillis} for a live message on the named queue.
     *
     * <p>Expired messages found while waiting are discarded and the wait continues with the
     * remaining timeout rather than returning early.</p>
     *
     * @return the message, or {@code null} if the timeout elapsed
     */
    public Message receive(String queueName, long timeoutMillis) throws JMSException, InterruptedException {
        return receiveInternal(queueName, Math.max(0L, timeoutMillis));
    }

    /**
     * Takes a live message from the named queue if one is immediately available.
     *
     * @return the message, or {@code null} if none is available
     */
    public Message receiveNoWait(String queueName) throws JMSException {
        checkNotTerminated();
        requireQueueName(queueName);

        BlockingQueue<QueuedMessage> queue = getOrCreateQueue(queueName);
        QueuedMessage envelope;
        while ((envelope = queue.poll()) != null) {
            Message message = accept(envelope, System.currentTimeMillis());
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private Message receiveInternal(String queueName, long timeoutMillis)
            throws JMSException, InterruptedException {
        checkNotTerminated();
        requireQueueName(queueName);

        BlockingQueue<QueuedMessage> queue = getOrCreateQueue(queueName);
        Thread self = Thread.currentThread();
        waitingThreads.add(self);
        try {
            long deadline = timeoutMillis >= 0
                    ? System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
                    : 0L;

            while (true) {
                QueuedMessage envelope;
                if (timeoutMillis >= 0) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0) {
                        return null;
                    }
                    envelope = queue.poll(remaining, TimeUnit.NANOSECONDS);
                } else {
                    envelope = queue.take();
                }
                if (envelope == null) {
                    return null;
                }
                Message message = accept(envelope, System.currentTimeMillis());
                if (message != null) {
                    return message;
                }
                // Expired, or claimed by a sweep or a purge: keep waiting.
            }
        } catch (InterruptedException e) {
            if (terminated) {
                jakarta.jms.IllegalStateException ex = new jakarta.jms.IllegalStateException(
                        "Broker \"" + brokerId + "\" has been destroyed");
                ex.setLinkedException(e);
                throw ex;
            }
            throw e;
        } finally {
            waitingThreads.remove(self);
        }
    }

    /**
     * Accounts for a dequeued envelope.
     *
     * @return the payload when this caller owns the release and the message is still live,
     *         {@code null} when the envelope must be discarded
     */
    private Message accept(QueuedMessage envelope, long now) {
        if (!envelope.claim()) {
            // A sweep or a purge owns this envelope: it is not deliverable.
            return null;
        }
        if (envelope.isExpired(now)) {
            release(envelope, expiredCount);
            return null;
        }
        release(envelope, deliveredCount);
        return envelope.getPayload();
    }

    /**
     * The single point where the capacity occupied by a message is given back. Reached only
     * by the caller that won {@link QueuedMessage#claim()}, so it runs exactly once per
     * message however the message left its queue.
     *
     * <p>The outcome is counted before the slot is released, so that an observer which sees
     * the capacity freed also sees the counter that explains where the message went.</p>
     *
     * @param outcome the lifetime counter this removal belongs to
     */
    private void release(QueuedMessage envelope, AtomicLong outcome) {
        if (envelope.canExpire()) {
            expiringMessages.decrementAndGet();
        }
        outcome.incrementAndGet();
        capacity.release();
    }

    /**
     * Gives a slot back without attributing it to any outcome. Only for a message that
     * never became visible to anyone.
     */
    private void releaseWithoutCounting(QueuedMessage envelope) {
        if (envelope.canExpire()) {
            expiringMessages.decrementAndGet();
        }
        capacity.release();
    }

    // ------------------------------------------------------------------
    // Expiry reclamation
    // ------------------------------------------------------------------

    /**
     * Removes the expired messages of this broker, releasing the capacity they occupied.
     *
     * <p>Uses {@code removeIf}, which evaluates its predicate with the queue lock released
     * and re-acquires it only to unlink the marked nodes, so producers and consumers
     * interleave with the sweep instead of waiting for it.</p>
     */
    void sweepExpired() {
        if (expiringMessages.get() == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        for (BlockingQueue<QueuedMessage> queue : queues.values()) {
            queue.removeIf(envelope -> {
                if (!envelope.isExpired(now)) {
                    return false;
                }
                if (!envelope.claim()) {
                    // A consumer or a purge owns it; it is already accounted for.
                    return false;
                }
                release(envelope, expiredCount);
                return true;
            });
        }
    }

    void recordSweepFailure(Throwable failure) {
        sweepFailureCount.incrementAndGet();
        lastSweepError = failure.getClass().getName()
                + (failure.getMessage() != null ? ": " + failure.getMessage() : "");
    }

    /**
     * Discards every message waiting in the named queue, freeing the capacity they occupied.
     *
     * @return the number of messages removed
     */
    int purgeQueue(String queueName) {
        BlockingQueue<QueuedMessage> queue = queueName == null ? null : queues.get(queueName);
        if (queue == null) {
            return 0;
        }
        List<QueuedMessage> drained = new ArrayList<>();
        queue.drainTo(drained);

        int removed = 0;
        for (QueuedMessage envelope : drained) {
            if (envelope.claim()) {
                release(envelope, purgedCount);
                removed++;
            }
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Terminates this broker: discards its messages, releases its resources and unblocks
     * the threads waiting on its queues. Called by {@link InMemoryBrokerRegistry}, which
     * removes the broker from the registry first.
     */
    void destroy() {
        terminated = true;

        disableJmx();

        for (String queueName : new ArrayList<>(queues.keySet())) {
            purgeQueue(queueName);
        }
        queues.clear();

        // Same mechanism a closed consumer uses, hoisted to the broker, which after this
        // change is the component that knows who is waiting.
        for (Thread waiting : waitingThreads) {
            waiting.interrupt();
        }
    }

    /**
     * Whether this broker has been destroyed and refuses every send and receive.
     *
     * @return true once the broker has been destroyed
     */
    public boolean isTerminated() {
        return terminated;
    }

    private void checkNotTerminated() throws jakarta.jms.IllegalStateException {
        if (terminated) {
            throw new jakarta.jms.IllegalStateException(
                    "Broker \"" + brokerId + "\" has been destroyed");
        }
    }

    // ------------------------------------------------------------------
    // JMX
    // ------------------------------------------------------------------

    /**
     * Registers this broker in the platform MBean server under
     * {@code it.baccan.vmjms:type=Broker,id=<quoted Broker ID>}. Idempotent.
     */
    public void enableJmx() {
        synchronized (jmxLock) {
            if (jmxEnabled) {
                return;
            }
            try {
                ManagementFactory.getPlatformMBeanServer()
                        .registerMBean(new BrokerMonitor(this), objectName());
            } catch (InstanceAlreadyExistsException e) {
                // Something is already registered under this object name: nothing to do.
            } catch (JMException e) {
                throw new JMSRuntimeException("Cannot enable JMX monitoring for broker \""
                        + brokerId + "\": " + e);
            }
            jmxEnabled = true;
        }
    }

    /**
     * Unregisters the MBean of this broker, if monitoring is enabled. Idempotent.
     */
    public void disableJmx() {
        synchronized (jmxLock) {
            if (!jmxEnabled) {
                return;
            }
            try {
                ManagementFactory.getPlatformMBeanServer().unregisterMBean(objectName());
            } catch (InstanceNotFoundException e) {
                // Already gone: nothing to do.
            } catch (JMException e) {
                throw new JMSRuntimeException("Cannot disable JMX monitoring for broker \""
                        + brokerId + "\": " + e);
            }
            jmxEnabled = false;
        }
    }

    /**
     * Whether this broker is currently registered with the platform MBean server.
     *
     * @return true between a successful {@link #enableJmx()} and the matching
     *         {@link #disableJmx()} or the destruction of the broker
     */
    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    /**
     * The object name this broker registers under. The Broker ID is always passed through
     * {@link ObjectName#quote(String)}: it is written by the user and may contain
     * characters reserved by JMX, and quoting unconditionally keeps the shape of the name
     * independent of its content.
     */
    public ObjectName objectName() throws JMException {
        return new ObjectName("it.baccan.vmjms:type=Broker,id=" + ObjectName.quote(brokerId));
    }

    // ------------------------------------------------------------------
    // Queues, identity and metrics
    // ------------------------------------------------------------------

    /**
     * Places in a queue an envelope that holds no capacity, so that a sweep can be made
     * to fail on demand.
     *
     * <p>Only a claimed envelope is accepted. An unclaimed one would be released by the
     * next purge or destruction, returning a permit that was never acquired: on a broker
     * created with the unbounded default that overflows the permit count of the semaphore,
     * which surfaces as an {@link Error}.</p>
     *
     * @param queueName the queue to insert into
     * @param envelope  an envelope already claimed by its creator
     * @throws IllegalArgumentException if the envelope has not been claimed
     */
    void offerUnaccounted(String queueName, QueuedMessage envelope) {
        if (!envelope.isClaimed()) {
            throw new IllegalArgumentException(
                    "Envelope must be claimed before it is offered unaccounted: it holds no capacity");
        }
        getOrCreateQueue(queueName).offer(envelope);
    }

    /**
     * Retrieves or creates a FIFO LinkedBlockingQueue for the specified queue name.
     *
     * <p>Private because a queue is not an access path: everything entering or leaving one
     * goes through this broker, which is what keeps the capacity accounting exact.</p>
     */
    private BlockingQueue<QueuedMessage> getOrCreateQueue(String queueName) {
        requireQueueName(queueName);
        return queues.computeIfAbsent(queueName, name -> new LinkedBlockingQueue<>());
    }

    private static void requireQueueName(String queueName) {
        if (queueName == null) {
            throw new IllegalArgumentException("Queue name cannot be null");
        }
    }

    /**
     * Generates a unique sequential message ID: ID:&lt;brokerId&gt;:&lt;instanceId&gt;:&lt;sequence&gt;
     */
    public String generateNextMessageId() {
        return "ID:" + brokerId + ":" + instanceId + ":" + messageIdSequence.incrementAndGet();
    }

    /**
     * Identifier of this broker, as the factory supplied it and never quoted.
     *
     * @return the Broker ID
     */
    public String getBrokerId() {
        return brokerId;
    }

    /**
     * Ordinal of this broker among those created in this JVM, used to keep message IDs
     * unique when a Broker ID is destroyed and created again.
     *
     * @return the instance number, counting from 1
     */
    public long getInstanceId() {
        return instanceId;
    }

    /**
     * The maximum number of waiting messages this broker was configured with;
     * {@code Integer.MAX_VALUE} when it is effectively unbounded.
     */
    public int getMaxMessages() {
        return maxMessages;
    }

    /**
     * Messages currently waiting across all queues of this broker.
     */
    public int getMessagesWaiting() {
        return maxMessages - capacity.availablePermits();
    }

    /**
     * Number of queues this broker holds. A queue is created by the first send or receive
     * naming it and is never removed while the broker lives.
     *
     * @return the number of queues
     */
    public int getQueueCount() {
        return queues.size();
    }

    /**
     * Depth of each queue of this broker, by queue name.
     */
    public Map<String, Integer> getQueueDepths() {
        Map<String, Integer> depths = new LinkedHashMap<>();
        queues.forEach((name, queue) -> depths.put(name, queue.size()));
        return Collections.unmodifiableMap(depths);
    }

    /**
     * Messages accepted into a queue since this broker was created. A message rejected for
     * lack of capacity is not counted here.
     *
     * @return the number of messages enqueued over the lifetime of the broker
     */
    public long getEnqueuedCount() {
        return enqueuedCount.get();
    }

    /**
     * Messages handed to a consumer since this broker was created.
     *
     * @return the number of messages delivered over the lifetime of the broker
     */
    public long getDeliveredCount() {
        return deliveredCount.get();
    }

    /**
     * Messages discarded because their expiration had passed, whether the reclamation
     * found them or a consumer skipped them.
     *
     * @return the number of messages expired over the lifetime of the broker
     */
    public long getExpiredCount() {
        return expiredCount.get();
    }

    /**
     * Messages discarded by a purge or by the destruction of the broker, counted apart
     * from the expired ones so that the two reasons stay distinguishable.
     *
     * @return the number of messages purged over the lifetime of the broker
     */
    public long getPurgedCount() {
        return purgedCount.get();
    }

    /**
     * Sends refused because the broker was already holding its maximum. A rejected message
     * never entered a queue and occupies no capacity.
     *
     * @return the number of sends rejected over the lifetime of the broker
     */
    public long getRejectedCount() {
        return rejectedCount.get();
    }

    /**
     * Reclamation passes that ended in a failure on this broker. A non-zero value means
     * expired messages may still be holding capacity; later passes keep running.
     *
     * @return the number of failed sweeps over the lifetime of the broker
     */
    public long getSweepFailureCount() {
        return sweepFailureCount.get();
    }

    /**
     * Type and message of the last failure met while reclaiming expired messages.
     *
     * @return the description of the last failure, or an empty string if no sweep has
     *         failed on this broker
     */
    public String getLastSweepError() {
        return lastSweepError;
    }
}
