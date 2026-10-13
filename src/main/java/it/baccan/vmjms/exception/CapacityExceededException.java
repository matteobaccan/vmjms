package it.baccan.vmjms.exception;

import jakarta.jms.ResourceAllocationException;

/**
 * Thrown by {@code MessageProducer.send} when the broker already holds the maximum number
 * of waiting messages it was configured with.
 *
 * <p>It derives from {@link ResourceAllocationException} - "a provider is unable to
 * allocate the resources required by a method" - so that code already handling resource
 * exhaustion from a real broker keeps working, and it is therefore catchable as
 * {@code jakarta.jms.JMSException}. Its error code is
 * {@link VmjmsErrorCodes#CAPACITY_EXCEEDED}.</p>
 *
 * <p>The send fails immediately: it never blocks waiting for room, and the rejected
 * message is not queued and does not consume capacity.</p>
 */
public class CapacityExceededException extends ResourceAllocationException {

    private static final long serialVersionUID = 1L;

    private final int limit;
    private final String queueName;
    private final int waitingCount;

    /**
     * @param limit        the maximum number of messages the broker was configured with
     * @param queueName    the queue the rejected send targeted
     * @param waitingCount the number of waiting messages observed at rejection
     */
    public CapacityExceededException(int limit, String queueName, int waitingCount) {
        super("Broker capacity exceeded: the maximum number of queued messages ("
                + limit + ") was exceeded while sending to queue \"" + queueName
                + "\" (" + waitingCount + " waiting)", VmjmsErrorCodes.CAPACITY_EXCEEDED);
        this.limit = limit;
        this.queueName = queueName;
        this.waitingCount = waitingCount;
    }

    /**
     * The maximum number of waiting messages the broker was configured with.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * The name of the queue the rejected send targeted.
     */
    public String getQueueName() {
        return queueName;
    }

    /**
     * The number of messages the broker was holding when the send was rejected.
     */
    public int getWaitingCount() {
        return waitingCount;
    }
}
