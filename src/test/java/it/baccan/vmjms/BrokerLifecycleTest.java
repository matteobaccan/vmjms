/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBroker;
import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import jakarta.jms.Connection;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Explicit management of the brokers held by the registry.
 */
public class BrokerLifecycleTest {

    @BeforeEach
    @AfterEach
    public void cleanState() {
        InMemoryBrokerRegistry.clear();
    }

    @Test
    public void aDestroyedBrokerCanNoLongerBeUsed() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("life-destroyed");
        Connection connection = factory.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue("q");
        MessageProducer producer = session.createProducer(queue);
        MessageConsumer consumer = session.createConsumer(queue);
        producer.send(session.createTextMessage("before"));

        assertTrue(InMemoryBrokerRegistry.destroy("life-destroyed"));

        jakarta.jms.IllegalStateException onSend = assertThrows(jakarta.jms.IllegalStateException.class,
                () -> producer.send(session.createTextMessage("after")));
        assertTrue(onSend.getMessage().contains("destroyed"), onSend.getMessage());
        assertThrows(jakarta.jms.IllegalStateException.class, () -> consumer.receiveNoWait());
        assertThrows(jakarta.jms.IllegalStateException.class, () -> consumer.receive(50));
        assertTrue(factory.getBroker().isTerminated());
        assertEquals(0, factory.getBroker().getMessagesWaiting());
    }

    @Test
    public void destroyingUnblocksAWaitingConsumer() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("life-unblock");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageConsumer consumer = session.createConsumer(queue);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            AtomicReference<Exception> failure = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);

            executor.submit(() -> {
                try {
                    started.countDown();
                    consumer.receive();
                } catch (Exception e) {
                    failure.set(e);
                }
            });

            assertTrue(started.await(1, TimeUnit.SECONDS));
            Thread.sleep(100); // let it block

            assertTrue(InMemoryBrokerRegistry.destroy("life-unblock"));

            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS),
                    "the blocked consumer must be released");
            Exception thrown = failure.get();
            assertNotNull(thrown, "the blocked consumer must fail");
            assertTrue(thrown instanceof jakarta.jms.IllegalStateException,
                    "was: " + thrown.getClass().getName());
        }
    }

    @Test
    public void aDestroyedBrokerIdIsReusable() throws Exception {
        InMemoryConnectionFactory first = new InMemoryConnectionFactory("life-reuse");
        try (Connection connection = first.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            session.createProducer(queue).send(session.createTextMessage("gone"));
        }
        InMemoryBroker destroyed = first.getBroker();
        assertTrue(InMemoryBrokerRegistry.destroy("life-reuse"));

        InMemoryConnectionFactory second = new InMemoryConnectionFactory("life-reuse");
        assertNotSame(destroyed, second.getBroker());
        assertFalse(second.getBroker().isTerminated());
        assertEquals(0, second.getBroker().getMessagesWaiting());

        try (Connection connection = second.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            assertNull(session.createConsumer(queue).receive(100),
                    "a reused Broker ID must not expose the messages of the destroyed broker");
        }
    }

    @Test
    public void destroyingIsIdempotent() {
        new InMemoryConnectionFactory("life-idempotent");

        assertTrue(InMemoryBrokerRegistry.destroy("life-idempotent"), "the first destroy removes it");
        assertFalse(InMemoryBrokerRegistry.destroy("life-idempotent"), "the second one has nothing to remove");
        assertFalse(InMemoryBrokerRegistry.destroy("life-never-existed"));
    }

    @Test
    public void resolvingWithoutCreating() {
        assertNull(InMemoryBrokerRegistry.find("life-absent"));
        assertFalse(InMemoryBrokerRegistry.brokerIds().contains("life-absent"),
                "a non-creating lookup must not create a broker");

        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("life-present");
        assertNotNull(InMemoryBrokerRegistry.find("life-present"));
        assertEquals(factory.getBroker(), InMemoryBrokerRegistry.find("life-present"));
    }

    @Test
    public void listingTheBrokersHeld() {
        new InMemoryConnectionFactory("life-list-a");
        new InMemoryConnectionFactory("life-list-b");
        new InMemoryConnectionFactory("life-list-c");

        assertEquals(Set.of("life-list-a", "life-list-b", "life-list-c"),
                InMemoryBrokerRegistry.brokerIds());

        InMemoryBrokerRegistry.destroy("life-list-b");

        assertEquals(Set.of("life-list-a", "life-list-c"), InMemoryBrokerRegistry.brokerIds());
    }

    @Test
    public void clearingTheRegistryDestroysEveryBroker() throws Exception {
        InMemoryConnectionFactory first = new InMemoryConnectionFactory("life-clear-a");
        InMemoryConnectionFactory second = new InMemoryConnectionFactory("life-clear-b");
        Connection connection = first.createConnection();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue("q");
        MessageProducer producer = session.createProducer(queue);
        producer.send(session.createTextMessage("before"));

        InMemoryBrokerRegistry.clear();

        assertTrue(first.getBroker().isTerminated());
        assertTrue(second.getBroker().isTerminated());
        assertTrue(InMemoryBrokerRegistry.brokerIds().isEmpty());
        assertThrows(jakarta.jms.IllegalStateException.class,
                () -> producer.send(session.createTextMessage("after")));
    }

    @Test
    public void closingAConnectionLeavesQueuedMessagesIntact() throws Exception {
        InMemoryConnectionFactory producerSide = new InMemoryConnectionFactory("life-shared");
        try (Connection connection = producerSide.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            session.createProducer(queue).send(session.createTextMessage("survives"));
        } // the connection, its session and its producer are now closed

        assertFalse(producerSide.getBroker().isTerminated(),
                "closing a connection must not destroy the broker");

        InMemoryConnectionFactory consumerSide = new InMemoryConnectionFactory("life-shared");
        try (Connection connection = consumerSide.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            Message received = session.createConsumer(queue).receive(500);
            assertNotNull(received, "a message must outlive the connection that sent it");
            assertEquals("survives", received.getBody(String.class));
        }
    }
}
