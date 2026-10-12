package it.baccan.vmjms.broker;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Registry holding shared InMemoryBroker instances by a string identifier.
 */
public class InMemoryBrokerRegistry {
    private static final ConcurrentMap<String, InMemoryBroker> registry = new ConcurrentHashMap<>();

    private InMemoryBrokerRegistry() {
        // Prevent instantiation
    }

    /**
     * Resolves or instantiates an InMemoryBroker for the given brokerId.
     */
    public static InMemoryBroker getOrCreate(String brokerId) {
        if (brokerId == null) {
            throw new IllegalArgumentException("Broker ID cannot be null");
        }
        return registry.computeIfAbsent(brokerId, InMemoryBroker::new);
    }

    /**
     * Clears all registered brokers (primarily for memory release between tests).
     */
    public static void clear() {
        registry.clear();
    }
}
