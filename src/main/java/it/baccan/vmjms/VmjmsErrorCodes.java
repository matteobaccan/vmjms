package it.baccan.vmjms;

/**
 * Error codes carried by vmjms exceptions through {@code JMSException.getErrorCode()} and
 * {@code JMSRuntimeException.getErrorCode()}.
 *
 * <p>Codes are namespaced as {@code VMJMS-<number>} and grouped by kind, so that new
 * failures can be added without renumbering the existing ones:</p>
 *
 * <ul>
 *   <li>{@code VMJMS-1xxx} - capacity and resource failures.</li>
 * </ul>
 */
public final class VmjmsErrorCodes {

    /**
     * A send was rejected because the broker already holds its configured maximum number
     * of waiting messages. Carried by {@link CapacityExceededException}.
     */
    public static final String CAPACITY_EXCEEDED = "VMJMS-1001";

    /**
     * A connection factory asked for an existing shared broker with an explicit maximum
     * number of messages that differs from the one the broker was created with. Carried by
     * the {@code jakarta.jms.JMSRuntimeException} thrown while the factory is constructed.
     */
    public static final String CONFLICTING_BROKER_LIMIT = "VMJMS-1002";

    private VmjmsErrorCodes() {
        // Prevent instantiation
    }
}
