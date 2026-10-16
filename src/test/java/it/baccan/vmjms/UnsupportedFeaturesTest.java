/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms;

import it.baccan.vmjms.broker.InMemoryBrokerRegistry;
import it.baccan.vmjms.exception.UnsupportedFeatureException;
import it.baccan.vmjms.exception.VmjmsErrorCodes;
import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.JMSRuntimeException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;
import jakarta.jms.TextMessage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The operations this provider does not implement fail as JMS failures.
 *
 * <p>What these tests are really about is the type, not the absence of the feature: an
 * application that already handles the failures of a real broker catches these too, which
 * {@code java.lang.UnsupportedOperationException} made impossible.</p>
 */
public class UnsupportedFeaturesTest {

    @AfterAll
    public static void releaseBrokers() {
        InMemoryBrokerRegistry.clear();
    }

    /**
     * An operation called through the JMS interfaces, so that the test sees what an
     * application sees.
     */
    private interface JmsCall {
        void run(Connection connection, Session session, Queue queue) throws JMSException;
    }

    /**
     * One operation of every unsupported family, with the feature name it must report.
     */
    private static List<Object[]> unsupportedCalls() {
        List<Object[]> calls = new ArrayList<>();
        calls.add(new Object[]{"Topics", (JmsCall) (c, s, q) -> s.createTopic("prices")});
        calls.add(new Object[]{"Durable topic subscribers",
            (JmsCall) (c, s, q) -> s.createDurableSubscriber(null, "sub")});
        calls.add(new Object[]{"Durable topic consumers",
            (JmsCall) (c, s, q) -> s.createDurableConsumer(null, "sub")});
        calls.add(new Object[]{"Shared topic subscriptions",
            (JmsCall) (c, s, q) -> s.createSharedConsumer(null, "sub")});
        calls.add(new Object[]{"Shared durable topic subscriptions",
            (JmsCall) (c, s, q) -> s.createSharedDurableConsumer(null, "sub")});
        calls.add(new Object[]{"Unsubscribing a durable subscription",
            (JmsCall) (c, s, q) -> s.unsubscribe("sub")});
        calls.add(new Object[]{"Transactions", (JmsCall) (c, s, q) -> s.commit()});
        calls.add(new Object[]{"Transactions", (JmsCall) (c, s, q) -> s.rollback()});
        calls.add(new Object[]{"MapMessage", (JmsCall) (c, s, q) -> s.createMapMessage()});
        calls.add(new Object[]{"ObjectMessage", (JmsCall) (c, s, q) -> s.createObjectMessage()});
        calls.add(new Object[]{"StreamMessage", (JmsCall) (c, s, q) -> s.createStreamMessage()});
        calls.add(new Object[]{"QueueBrowser", (JmsCall) (c, s, q) -> s.createBrowser(q)});
        calls.add(new Object[]{"Temporary queues", (JmsCall) (c, s, q) -> s.createTemporaryQueue()});
        calls.add(new Object[]{"Temporary topics", (JmsCall) (c, s, q) -> s.createTemporaryTopic()});
        calls.add(new Object[]{"Asynchronous delivery through a MessageListener",
            (JmsCall) (c, s, q) -> s.setMessageListener(null)});
        calls.add(new Object[]{"Asynchronous delivery through a MessageListener",
            (JmsCall) (c, s, q) -> s.createConsumer(q).setMessageListener(null)});
        calls.add(new Object[]{"Asynchronous delivery through a MessageListener",
            (JmsCall) (c, s, q) -> s.createConsumer(q).getMessageListener()});
        calls.add(new Object[]{"Asynchronous send with a CompletionListener",
            (JmsCall) (c, s, q) -> s.createProducer(q).send(s.createTextMessage("x"), null)});
        calls.add(new Object[]{"ConnectionConsumer",
            (JmsCall) (c, s, q) -> c.createConnectionConsumer(q, null, null, 1)});
        calls.add(new Object[]{"ConnectionConsumer",
            (JmsCall) (c, s, q) -> c.createDurableConnectionConsumer(null, "sub", null, null, 1)});
        return calls;
    }

    @Test
    public void everyUnsupportedOperationFailsAsAJmsException() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("unsupported");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");

            for (Object[] entry : unsupportedCalls()) {
                String feature = (String) entry[0];
                JmsCall call = (JmsCall) entry[1];

                UnsupportedFeatureException failure = assertThrows(
                        UnsupportedFeatureException.class,
                        () -> call.run(connection, session, queue),
                        "expected a JMS failure for " + feature);

                assertEquals(feature, failure.getFeature());
                assertEquals(VmjmsErrorCodes.UNSUPPORTED_FEATURE, failure.getErrorCode(),
                        "error code for " + feature);
                assertTrue(failure.getMessage().contains(feature),
                        "the message must name the feature: " + failure.getMessage());
                assertTrue(failure instanceof JMSException,
                        "must be catchable as JMSException: " + feature);
            }
        }
    }

    @Test
    public void theSimplifiedApiFailsUncheckedWithTheSameErrorCode() {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("unsupported-context");

        JMSRuntimeException failure = assertThrows(JMSRuntimeException.class, factory::createContext);

        assertEquals(VmjmsErrorCodes.UNSUPPORTED_FEATURE, failure.getErrorCode());
        assertTrue(failure.getMessage().contains("JMSContext"), failure.getMessage());
    }

    @Test
    public void transactionsFailEvenOnASessionCreatedTransacted() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("unsupported-tx");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(true, Session.SESSION_TRANSACTED);

            assertTrue(session.getTransacted(), "the session reports what it was asked for");
            assertEquals("Transactions",
                    assertThrows(UnsupportedFeatureException.class, session::commit).getFeature());
        }
    }

    @Test
    public void aMessageWithNoBodyAnswersNullInsteadOfFailing() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("unsupported-body");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            Message message = session.createMessage();

            // The specification: for a Message that is not one of its subtypes the
            // returned value is always null and the type asked for is irrelevant.
            assertTrue(message.isBodyAssignableTo(String.class));
            assertTrue(message.isBodyAssignableTo(byte[].class));
            assertNull(message.getBody(String.class));
            assertNull(message.getBody(Object.class));
        }
    }

    @Test
    public void whatIsSupportedStillWorksAfterTheChange() throws Exception {
        InMemoryConnectionFactory factory = new InMemoryConnectionFactory("unsupported-happy");
        try (Connection connection = factory.createConnection()) {
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("q");
            MessageProducer producer = session.createProducer(queue);
            MessageConsumer consumer = session.createConsumer(queue);

            producer.send(session.createTextMessage("still here"));

            assertEquals("still here", ((TextMessage) consumer.receive(500)).getText());
        }
    }
}
