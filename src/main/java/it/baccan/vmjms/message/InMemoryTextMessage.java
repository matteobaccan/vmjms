package it.baccan.vmjms.message;

import jakarta.jms.JMSException;
import jakarta.jms.MessageFormatException;
import jakarta.jms.TextMessage;

/**
 * Implementation of jakarta.jms.TextMessage interface.
 */
public class InMemoryTextMessage extends InMemoryMessage implements TextMessage {
    private String text;

    public InMemoryTextMessage() {
        super();
    }

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
