package it.baccan.vmjms.message;

import jakarta.jms.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base implementation of jakarta.jms.Message interface with properties and headers.
 */
public class InMemoryMessage implements Message {
    private String jmsMessageID;
    private long jmsTimestamp;
    private String jmsCorrelationID;
    private byte[] jmsCorrelationIDBytes;
    private Destination jmsReplyTo;
    private Destination jmsDestination;
    private int jmsDeliveryMode = DeliveryMode.NON_PERSISTENT;
    private boolean jmsRedelivered = false;
    private String jmsType;
    private long jmsExpiration = 0;
    private long jmsDeliveryTime = 0;
    private int jmsPriority = 4; // Default priority

    private final Map<String, Object> properties = new ConcurrentHashMap<>();

    @Override
    public String getJMSMessageID() throws JMSException {
        return jmsMessageID;
    }

    @Override
    public void setJMSMessageID(String id) throws JMSException {
        this.jmsMessageID = id;
    }

    @Override
    public long getJMSTimestamp() throws JMSException {
        return jmsTimestamp;
    }

    @Override
    public void setJMSTimestamp(long timestamp) throws JMSException {
        this.jmsTimestamp = timestamp;
    }

    @Override
    public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
        return jmsCorrelationIDBytes;
    }

    @Override
    public void setJMSCorrelationIDAsBytes(byte[] correlationID) throws JMSException {
        this.jmsCorrelationIDBytes = correlationID;
    }

    @Override
    public void setJMSCorrelationID(String correlationID) throws JMSException {
        this.jmsCorrelationID = correlationID;
    }

    @Override
    public String getJMSCorrelationID() throws JMSException {
        return jmsCorrelationID;
    }

    @Override
    public Destination getJMSReplyTo() throws JMSException {
        return jmsReplyTo;
    }

    @Override
    public void setJMSReplyTo(Destination replyTo) throws JMSException {
        this.jmsReplyTo = replyTo;
    }

    @Override
    public Destination getJMSDestination() throws JMSException {
        return jmsDestination;
    }

    @Override
    public void setJMSDestination(Destination destination) throws JMSException {
        this.jmsDestination = destination;
    }

    @Override
    public int getJMSDeliveryMode() throws JMSException {
        return jmsDeliveryMode;
    }

    @Override
    public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
        this.jmsDeliveryMode = deliveryMode;
    }

    @Override
    public boolean getJMSRedelivered() throws JMSException {
        return jmsRedelivered;
    }

    @Override
    public void setJMSRedelivered(boolean redelivered) throws JMSException {
        this.jmsRedelivered = redelivered;
    }

    @Override
    public String getJMSType() throws JMSException {
        return jmsType;
    }

    @Override
    public void setJMSType(String type) throws JMSException {
        this.jmsType = type;
    }

    @Override
    public long getJMSExpiration() throws JMSException {
        return jmsExpiration;
    }

    @Override
    public void setJMSExpiration(long expiration) throws JMSException {
        this.jmsExpiration = expiration;
    }

    @Override
    public long getJMSDeliveryTime() throws JMSException {
        return jmsDeliveryTime;
    }

    @Override
    public void setJMSDeliveryTime(long deliveryTime) throws JMSException {
        this.jmsDeliveryTime = deliveryTime;
    }

    @Override
    public int getJMSPriority() throws JMSException {
        return jmsPriority;
    }

    @Override
    public void setJMSPriority(int priority) throws JMSException {
        this.jmsPriority = priority;
    }

    @Override
    public void clearProperties() throws JMSException {
        properties.clear();
    }

    @Override
    public boolean propertyExists(String name) throws JMSException {
        return properties.containsKey(name);
    }

    @Override
    public boolean getBooleanProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Boolean) return (Boolean) val;
        return val != null && Boolean.parseBoolean(val.toString());
    }

    @Override
    public byte getByteProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Number) return ((Number) val).byteValue();
        if (val instanceof String) return Byte.parseByte((String) val);
        throw new MessageFormatException("Property " + name + " cannot be converted to byte");
    }

    @Override
    public short getShortProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Number) return ((Number) val).shortValue();
        if (val instanceof String) return Short.parseShort((String) val);
        throw new MessageFormatException("Property " + name + " cannot be converted to short");
    }

    @Override
    public int getIntProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        throw new MessageFormatException("Property " + name + " cannot be converted to int");
    }

    @Override
    public long getLongProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        throw new MessageFormatException("Property " + name + " cannot be converted to long");
    }

    @Override
    public float getFloatProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Number) return ((Number) val).floatValue();
        if (val instanceof String) return Float.parseFloat((String) val);
        throw new MessageFormatException("Property " + name + " cannot be converted to float");
    }

    @Override
    public double getDoubleProperty(String name) throws JMSException {
        Object val = properties.get(name);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) return Double.parseDouble((String) val);
        throw new MessageFormatException("Property " + name + " cannot be converted to double");
    }

    @Override
    public String getStringProperty(String name) throws JMSException {
        Object val = properties.get(name);
        return val == null ? null : val.toString();
    }

    @Override
    public Object getObjectProperty(String name) throws JMSException {
        return properties.get(name);
    }

    @Override
    public Enumeration getPropertyNames() throws JMSException {
        return Collections.enumeration(properties.keySet());
    }

    @Override
    public void setBooleanProperty(String name, boolean value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setByteProperty(String name, byte value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setShortProperty(String name, short value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setIntProperty(String name, int value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setLongProperty(String name, long value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setFloatProperty(String name, float value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setDoubleProperty(String name, double value) throws JMSException {
        properties.put(name, value);
    }

    @Override
    public void setStringProperty(String name, String value) throws JMSException {
        if (value == null) {
            properties.remove(name);
        } else {
            properties.put(name, value);
        }
    }

    @Override
    public void setObjectProperty(String name, Object value) throws JMSException {
        if (value == null) {
            properties.remove(name);
        } else {
            properties.put(name, value);
        }
    }

    @Override
    public void acknowledge() throws JMSException {
        // No-op for our simple in-memory auto-acknowledge model
    }

    @Override
    public void clearBody() throws JMSException {
        // Overridden by subclasses if needed
    }

    @Override
    public <T> T getBody(Class<T> c) throws JMSException {
        throw new UnsupportedOperationException("getBody(Class) not supported in base message");
    }

    @Override
    public boolean isBodyAssignableTo(Class c) throws JMSException {
        return false;
    }
}
