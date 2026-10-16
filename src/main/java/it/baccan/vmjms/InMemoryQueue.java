/*
 * vmjms - in-memory Jakarta JMS provider
 * https://github.com/matteobaccan/vmjms
 *
 * Copyright (c) 2026 Matteo Baccan
 * SPDX-License-Identifier: MIT
 */
package it.baccan.vmjms;

import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A queue, named and compared by value, that can carry parameters in its name.
 *
 * <p>Obtained from {@link InMemorySession#createQueue(String)}. The part of the name
 * after the first {@code ?} is parsed as {@code key=value} pairs separated by {@code &}
 * and removed from the queue name, so {@code "orders?retry=3"} names the queue
 * {@code "orders"} and keeps {@code retry=3} in {@link #getParameters()}. The provider
 * acts on none of those parameters: they are there for the application to read, which
 * lets a configuration string travel with the destination.</p>
 *
 * <p>Equality is the name plus the parameters, so two queue objects built from the same
 * string are interchangeable. Note that the parameters take part in it:
 * {@code createQueue("orders")} and {@code createQueue("orders?retry=3")} are two
 * different objects, yet they name the same queue inside the broker and messages sent to
 * one are received from the other. Instances are immutable and safe to share.</p>
 */
public class InMemoryQueue implements Queue {
    private final String queueName;
    private final Map<String, String> parameters;

    /**
     * Creates a queue from a name that may carry query parameters.
     *
     * <p>Everything after the first {@code ?} is parsed as {@code key=value} pairs
     * separated by {@code &} and is removed from the queue name: {@code "orders?retry=3"}
     * names the queue {@code "orders"} and keeps {@code retry=3} among the parameters. A
     * pair with no {@code =} maps to an empty value. The parameters are carried for the
     * application to read and this provider acts on none of them.</p>
     *
     * @param rawQueueName the queue name, optionally followed by {@code ?} and parameters
     * @throws IllegalArgumentException if the name is null
     */
    public InMemoryQueue(String rawQueueName) {
        if (rawQueueName == null) {
            throw new IllegalArgumentException("Queue name cannot be null");
        }

        int questionMarkIndex = rawQueueName.indexOf('?');
        if (questionMarkIndex >= 0) {
            this.queueName = rawQueueName.substring(0, questionMarkIndex);
            String query = rawQueueName.substring(questionMarkIndex + 1);
            Map<String, String> parsedParams = new HashMap<>();
            
            if (!query.isEmpty()) {
                String[] pairs = query.split("&");
                for (String pair : pairs) {
                    int eqIndex = pair.indexOf('=');
                    if (eqIndex > 0) {
                        String key = pair.substring(0, eqIndex);
                        String value = pair.substring(eqIndex + 1);
                        parsedParams.put(key, value);
                    } else if (!pair.isEmpty()) {
                        parsedParams.put(pair, "");
                    }
                }
            }
            this.parameters = Collections.unmodifiableMap(parsedParams);
        } else {
            this.queueName = rawQueueName;
            this.parameters = Collections.emptyMap();
        }
    }

    @Override
    public String getQueueName() throws JMSException {
        return queueName;
    }

    /**
     * Parameters parsed from the queue name, empty when it carried none.
     *
     * @return an unmodifiable map of the parameters, in no particular order
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "InMemoryQueue[" + queueName + ", params=" + parameters + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InMemoryQueue that = (InMemoryQueue) o;
        return queueName.equals(that.queueName) && parameters.equals(that.parameters);
    }

    @Override
    public int hashCode() {
        int result = queueName.hashCode();
        result = 31 * result + parameters.hashCode();
        return result;
    }
}
