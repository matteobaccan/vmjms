/**
 * The failures this provider raises on its own, and the codes that name them.
 *
 * <p>Two of them, both reachable from a {@code catch} on a {@code jakarta.jms} type,
 * because an application should not need to know which provider it is talking to in order
 * to handle a failure:</p>
 *
 * <ul>
 *   <li>{@link it.baccan.vmjms.exception.CapacityExceededException}, a
 *       {@code ResourceAllocationException}, when a send would take a broker past the
 *       maximum number of messages it was configured with;</li>
 *   <li>{@link it.baccan.vmjms.exception.UnsupportedFeatureException}, a
 *       {@code JMSException}, when the operation called is one this provider does not
 *       implement.</li>
 * </ul>
 *
 * <p>Both carry a code from {@link it.baccan.vmjms.exception.VmjmsErrorCodes}, readable
 * through {@code getErrorCode()}, which is also where the few failures that have no class
 * of their own take theirs: the conflicting broker limit and the simplified API, both
 * reported as an unchecked {@code jakarta.jms.JMSRuntimeException} because the methods
 * raising them declare no checked exception.</p>
 *
 * <p>The codes live here rather than next to the connection factory because the codes and
 * the exceptions that carry them are one contract: adding a failure means adding both.</p>
 */
package it.baccan.vmjms.exception;
