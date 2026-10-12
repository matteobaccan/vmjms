package it.baccan.vmjms;

import jakarta.jms.JMSException;
import jakarta.jms.Queue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A simple implementation of the jakarta.jms.Queue interface with parameter parsing support.
 */
public class InMemoryQueue implements Queue {
    private final String queueName;
    private final Map<String, String> parameters;

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
