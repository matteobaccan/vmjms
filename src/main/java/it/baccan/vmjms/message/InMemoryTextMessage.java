/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms.message;

import jakarta.jms.JMSException;
import jakarta.jms.MessageFormatException;
import jakarta.jms.TextMessage;

/**
 * A message whose body is a string.
 *
 * <p>Created through {@code Session.createTextMessage()} or
 * {@code Session.createTextMessage(String)}. The body may be null.
 * {@link #getBody(Class)} serves any type a {@code String} can be assigned to, so
 * {@code String}, {@code CharSequence} and {@code Object} all work, and refuses anything
 * else with {@code jakarta.jms.MessageFormatException}.</p>
 */
public class InMemoryTextMessage extends InMemoryMessage implements TextMessage {
    private String text;

    /**
     * Creates a text message with no body. Applications use
     * {@code Session.createTextMessage()} instead.
     */
    public InMemoryTextMessage() {
        super();
    }

    /**
     * Creates a text message with a body. Applications use
     * {@code Session.createTextMessage(String)} instead.
     *
     * @param text the body, which may be null
     */
    public InMemoryTextMessage(String text) {
        super();
        this.text = text;
    }

    @Override
    public void setText(String string) throws JMSException {
        this.text = string;
    }

    @Override
    public String getText() throws JMSException {
        return text;
    }

    @Override
    public void clearBody() throws JMSException {
        super.clearBody();
        this.text = null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getBody(Class<T> c) throws JMSException {
        if (c == null) {
            throw new NullPointerException("Class type cannot be null");
        }
        if (isBodyAssignableTo(c)) {
            return (T) this.text;
        }
        throw new MessageFormatException("TextMessage body cannot be assigned to " + c.getName());
    }

    @Override
    public boolean isBodyAssignableTo(Class c) throws JMSException {
        if (c == null) {
            return false;
        }
        return ((Class<?>) c).isAssignableFrom(String.class);
    }
}
