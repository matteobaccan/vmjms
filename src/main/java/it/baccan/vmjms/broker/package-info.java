/**
 * The brokers that hold the queues, their lifecycle and their monitoring.
 *
 * <p>A broker is the unit of isolation and of accounting: it owns the queues, the
 * identifier sequence used for message IDs, the capacity shared by all its queues and the
 * counters reported over JMX. Applications normally never touch this package, because
 * {@link it.baccan.vmjms.InMemoryConnectionFactory} creates and finds brokers on their
 * behalf. Two things bring them here:</p>
 *
 * <ul>
 *   <li>{@link it.baccan.vmjms.broker.InMemoryBrokerRegistry}, to destroy a broker, to
 *       list the Broker IDs in use, or to free everything between tests;</li>
 *   <li>{@link it.baccan.vmjms.broker.InMemoryBroker#enableJmx()}, to expose queue
 *       depths and lifetime counters to a JMX console.</li>
 * </ul>
 *
 * <p>Every message entering or leaving a queue passes through its broker, which is what
 * keeps the capacity accounting exact: the queues themselves are not reachable from
 * outside. Expired messages are reclaimed by a single daemon thread owned by the
 * registry, shared by every broker in the JVM and started only once a message that can
 * expire exists.</p>
 */
package it.baccan.vmjms.broker;
