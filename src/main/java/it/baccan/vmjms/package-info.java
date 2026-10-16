/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
/**
 * In-memory Jakarta JMS 3.1 provider: the entry point and the point-to-point API.
 *
 * <p>{@link it.baccan.vmjms.InMemoryConnectionFactory} is the only class an application
 * has to name. Everything reached from it is a {@code jakarta.jms} interface, so code
 * written against this provider runs unchanged against a real broker:</p>
 *
 * <pre>{@code
 * ConnectionFactory factory = new InMemoryConnectionFactory("orders");
 * try (Connection connection = factory.createConnection()) {
 *     Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
 *     Queue queue = session.createQueue("inbound");
 *     session.createProducer(queue).send(session.createTextMessage("hello"));
 *     Message received = session.createConsumer(queue).receive(1000);
 * }
 * }</pre>
 *
 * <p>Messages live in the heap of the running JVM and nothing is persisted: a restart
 * starts empty. Two factories sharing a Broker ID share the queues; factories with
 * different Broker IDs are isolated, which is what makes one test independent of the
 * next.</p>
 *
 * <h2>What this provider implements</h2>
 *
 * <p>Point-to-point messaging with synchronous consumption: queues, producers, consumers,
 * {@code TextMessage} and {@code BytesMessage}, FIFO delivery per queue, message expiry,
 * and a capacity limit per broker. Durability, transactions, publish-subscribe and
 * asynchronous delivery are not implemented, and the operations that would need them
 * fail fast rather than pretending to work. Each class states its own deviations, and
 * the README has the complete table.</p>
 *
 * <h2>Threading</h2>
 *
 * <p>Brokers and their queues are thread-safe: any number of threads may send to and
 * receive from the same queue, and each message is delivered to exactly one consumer.
 * Connections, sessions, producers and consumers follow the JMS threading model, where a
 * session and the objects created from it are meant for a single thread at a time; their
 * {@code close()} is the exception and may be called from another thread, which is how a
 * blocked {@code receive()} is unblocked.</p>
 *
 * @see it.baccan.vmjms.broker.InMemoryBrokerRegistry
 * @see it.baccan.vmjms.exception.VmjmsErrorCodes
 */
package it.baccan.vmjms;
