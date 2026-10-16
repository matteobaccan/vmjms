/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms.message;

import jakarta.jms.*;
import java.io.*;

/**
 * A message whose body is a byte array, written and then read through data streams.
 *
 * <p>Created through {@code Session.createBytesMessage()}. The body starts out writable:
 * the {@code writeXxx} methods append to it, {@link #reset()} switches it to read-only
 * and rewinds it, and the {@code readXxx} methods then consume it in order. Writing to a
 * message that has been reset fails with {@code MessageNotWriteableException}, and
 * reading one that has not fails with {@code MessageNotReadableException}; a producer
 * resets the message on send, so a consumer can read it straight away.</p>
 *
 * <p>The streams hold position, so a message is readable by one thread at a time.</p>
 */
public class InMemoryBytesMessage extends InMemoryMessage implements BytesMessage {
    private byte[] bodyBytes = new byte[0];
    private boolean readOnly = false;

    private transient ByteArrayOutputStream baos;
    private transient DataOutputStream dos;
    private transient DataInputStream dis;

    /**
     * Creates an empty, writable bytes message. Applications use
     * {@code Session.createBytesMessage()} instead.
     */
    public InMemoryBytesMessage() {
        super();
        clearBody();
    }

    private void checkWriteOnly() throws MessageNotWriteableException {
        if (readOnly) {
            throw new MessageNotWriteableException("Message is in read-only mode");
        }
    }

    private void checkReadOnly() throws MessageNotReadableException {
        if (!readOnly) {
            throw new MessageNotReadableException("Message is in write-only mode");
        }
    }

    private JMSException createJmsException(String reason, Exception cause) {
        JMSException jmse = new JMSException(reason);
        jmse.setLinkedException(cause);
        return jmse;
    }

    @Override
    public long getBodyLength() throws JMSException {
        if (readOnly) {
            return bodyBytes.length;
        } else {
            return baos.size();
        }
    }

    @Override
    public boolean readBoolean() throws JMSException {
        checkReadOnly();
        try {
            return dis.readBoolean();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading boolean", e);
        }
    }

    @Override
    public byte readByte() throws JMSException {
        checkReadOnly();
        try {
            return dis.readByte();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading byte", e);
        }
    }

    @Override
    public int readUnsignedByte() throws JMSException {
        checkReadOnly();
        try {
            return dis.readUnsignedByte();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading unsigned byte", e);
        }
    }

    @Override
    public short readShort() throws JMSException {
        checkReadOnly();
        try {
            return dis.readShort();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading short", e);
        }
    }

    @Override
    public int readUnsignedShort() throws JMSException {
        checkReadOnly();
        try {
            return dis.readUnsignedShort();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading unsigned short", e);
        }
    }

    @Override
    public char readChar() throws JMSException {
        checkReadOnly();
        try {
            return dis.readChar();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading char", e);
        }
    }

    @Override
    public int readInt() throws JMSException {
        checkReadOnly();
        try {
            return dis.readInt();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading int", e);
        }
    }

    @Override
    public long readLong() throws JMSException {
        checkReadOnly();
        try {
            return dis.readLong();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading long", e);
        }
    }

    @Override
    public float readFloat() throws JMSException {
        checkReadOnly();
        try {
            return dis.readFloat();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading float", e);
        }
    }

    @Override
    public double readDouble() throws JMSException {
        checkReadOnly();
        try {
            return dis.readDouble();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading double", e);
        }
    }

    @Override
    public String readUTF() throws JMSException {
        checkReadOnly();
        try {
            return dis.readUTF();
        } catch (EOFException e) {
            throw new MessageEOFException("EOF reached");
        } catch (IOException e) {
            throw createJmsException("Error reading UTF string", e);
        }
    }

    @Override
    public int readBytes(byte[] value) throws JMSException {
        if (value == null) {
            throw new NullPointerException("Buffer cannot be null");
        }
        return readBytes(value, value.length);
    }

    @Override
    public int readBytes(byte[] value, int length) throws JMSException {
        checkReadOnly();
        if (length < 0 || length > value.length) {
            throw new IndexOutOfBoundsException("Invalid length requested: " + length);
        }
        try {
            int read = dis.read(value, 0, length);
            return read == -1 ? -1 : read;
        } catch (IOException e) {
            throw createJmsException("Error reading bytes", e);
        }
    }

    @Override
    public void writeBoolean(boolean value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeBoolean(value);
        } catch (IOException e) {
            throw createJmsException("Error writing boolean", e);
        }
    }

    @Override
    public void writeByte(byte value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeByte(value);
        } catch (IOException e) {
            throw createJmsException("Error writing byte", e);
        }
    }

    @Override
    public void writeShort(short value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeShort(value);
        } catch (IOException e) {
            throw createJmsException("Error writing short", e);
        }
    }

    @Override
    public void writeChar(char value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeChar(value);
        } catch (IOException e) {
            throw createJmsException("Error writing char", e);
        }
    }

    @Override
    public void writeInt(int value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeInt(value);
        } catch (IOException e) {
            throw createJmsException("Error writing int", e);
        }
    }

    @Override
    public void writeLong(long value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeLong(value);
        } catch (IOException e) {
            throw createJmsException("Error writing long", e);
        }
    }

    @Override
    public void writeFloat(float value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeFloat(value);
        } catch (IOException e) {
            throw createJmsException("Error writing float", e);
        }
    }

    @Override
    public void writeDouble(double value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeDouble(value);
        } catch (IOException e) {
            throw createJmsException("Error writing double", e);
        }
    }

    @Override
    public void writeUTF(String value) throws JMSException {
        checkWriteOnly();
        try {
            dos.writeUTF(value);
        } catch (IOException e) {
            throw createJmsException("Error writing UTF string", e);
        }
    }

    @Override
    public void writeBytes(byte[] value) throws JMSException {
        checkWriteOnly();
        try {
            dos.write(value);
        } catch (IOException e) {
            throw createJmsException("Error writing bytes", e);
        }
    }

    @Override
    public void writeBytes(byte[] value, int offset, int length) throws JMSException {
        checkWriteOnly();
        try {
            dos.write(value, offset, length);
        } catch (IOException e) {
            throw createJmsException("Error writing bytes", e);
        }
    }

    @Override
    public void writeObject(Object value) throws JMSException {
        checkWriteOnly();
        if (value == null) {
            throw new NullPointerException("Cannot write null object");
        }
        if (value instanceof Boolean) writeBoolean((Boolean) value);
        else if (value instanceof Byte) writeByte((Byte) value);
        else if (value instanceof Short) writeShort((Short) value);
        else if (value instanceof Character) writeChar((Character) value);
        else if (value instanceof Integer) writeInt((Integer) value);
        else if (value instanceof Long) writeLong((Long) value);
        else if (value instanceof Float) writeFloat((Float) value);
        else if (value instanceof Double) writeDouble((Double) value);
        else if (value instanceof String) writeUTF((String) value);
        else if (value instanceof byte[]) writeBytes((byte[]) value);
        else throw new MessageFormatException("Unsupported object type for BytesMessage: " + value.getClass().getName());
    }

    @Override
    public void reset() throws JMSException {
        if (!readOnly) {
            dos = null;
            bodyBytes = baos.toByteArray();
            baos = null;
            readOnly = true;
        }
        dis = new DataInputStream(new ByteArrayInputStream(bodyBytes));
    }

    @Override
    public void clearBody() {
        bodyBytes = new byte[0];
        baos = new ByteArrayOutputStream();
        dos = new DataOutputStream(baos);
        dis = null;
        readOnly = false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getBody(Class<T> c) throws JMSException {
        if (c == null) {
            throw new NullPointerException("Class type cannot be null");
        }
        if (isBodyAssignableTo(c)) {
            reset();
            return (T) bodyBytes.clone();
        }
        throw new MessageFormatException("BytesMessage body cannot be assigned to " + c.getName());
    }

    @Override
    public boolean isBodyAssignableTo(Class c) throws JMSException {
        if (c == null) {
            return false;
        }
        return ((Class<?>) c).isAssignableFrom(byte[].class);
    }
}
