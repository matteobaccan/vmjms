package it.baccan.vmjms.broker;

import java.util.Map;

/**
 * Management interface of an {@link InMemoryBroker}, exposed while JMX monitoring is
 * enabled for it.
 *
 * <p>Every attribute is read-only. The configured maximum in particular is fixed when the
 * broker is created and cannot be written here. The operations are limited to ones that
 * cannot corrupt the accounting: both go through the same claim-based release as delivery
 * and automatic reclamation.</p>
 */
public interface BrokerMonitorMBean {

    /**
     * The Broker ID exactly as supplied, without the quoting applied to the object name.
     */
    String getBrokerId();

    /**
     * Maximum number of waiting messages, {@code Integer.MAX_VALUE} when unbounded.
     */
    int getMaxMessages();

    /**
     * Messages currently waiting across all queues of this broker.
     */
    int getMessagesWaiting();

    int getQueueCount();

    /**
     * Depth of each queue, by queue name.
     */
    Map<String, Integer> getQueueDepths();

    /**
     * Messages accepted by send over the lifetime of this broker. Sends rejected for
     * capacity are not counted here: they never entered a queue.
     */
    long getEnqueuedCount();

    /**
     * Messages handed to a consumer over the lifetime of this broker.
     */
    long getDeliveredCount();

    /**
     * Messages removed because their expiration had passed. A purge is not counted here,
     * so that this number stays usable as the signal that time-to-live values are too
     * short or consumers too slow.
     */
    long getExpiredCount();

    /**
     * Messages removed by an administrative purge.
     */
    long getPurgedCount();

    /**
     * Sends rejected because the broker was at its configured maximum.
     */
    long getRejectedCount();

    /**
     * Reclamation sweeps that failed for this broker.
     */
    long getSweepFailureCount();

    /**
     * Description of the most recent sweep failure, or an empty string if there was none.
     */
    String getLastSweepError();

    /**
     * Discards every message waiting in the named queue, freeing the capacity it occupied.
     *
     * @return the number of messages removed
     */
    int purgeQueue(String queueName);

    /**
     * Runs an expiry sweep on this broker immediately, without waiting for the next
     * scheduled one.
     */
    void sweepExpiredNow();
}
