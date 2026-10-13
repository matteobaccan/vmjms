package it.baccan.vmjms.exception;

import jakarta.jms.JMSException;

/**
 * Thrown when an operation of the JMS API is not implemented by this provider.
 *
 * <p>This provider covers point-to-point messaging with synchronous consumption. The
 * operations outside that scope - topics, durable and shared subscriptions, transactions,
 * asynchronous delivery, queue browsers, temporary destinations and the message types
 * other than {@code TextMessage} and {@code BytesMessage} - fail here rather than
 * pretending to work, because a silent no-op in a messaging provider is a bug that
 * surfaces far from its cause.</p>
 *
 * <p>It derives from {@link JMSException}, so an application that already handles the
 * failures of a real broker catches this one too, and it carries
 * {@link VmjmsErrorCodes#UNSUPPORTED_FEATURE} as its error code. Those two properties are
 * the reason it exists: before, these operations threw
 * {@code java.lang.UnsupportedOperationException}, which is unchecked, foreign to JMS and
 * invisible to a {@code catch (JMSException)}.</p>
 *
 * <p>The methods of the simplified API introduced in JMS 2.0, which declare no checked
 * exception, cannot use this class: they throw {@code jakarta.jms.JMSRuntimeException}
 * with the same error code.</p>
 *
 * @see VmjmsErrorCodes#UNSUPPORTED_FEATURE
 */
public class UnsupportedFeatureException extends JMSException {

    private static final long serialVersionUID = 1L;

    private final String feature;

    /**
     * @param feature the operation or capability that is not implemented
     */
    public UnsupportedFeatureException(String feature) {
        this(feature, null);
    }

    /**
     * @param feature the operation or capability that is not implemented
     * @param hint    what to use instead, appended to the message; may be null
     */
    public UnsupportedFeatureException(String feature, String hint) {
        super(message(feature, hint), VmjmsErrorCodes.UNSUPPORTED_FEATURE);
        this.feature = feature;
    }

    private static String message(String feature, String hint) {
        String text = "Not implemented by this in-memory provider: " + feature + ".";
        return hint == null ? text : text + " " + hint;
    }

    /**
     * The operation or capability that is not implemented, without the hint.
     *
     * @return the name of the missing feature
     */
    public String getFeature() {
        return feature;
    }
}
