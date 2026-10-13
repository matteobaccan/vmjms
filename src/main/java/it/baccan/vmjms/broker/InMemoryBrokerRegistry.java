package it.baccan.vmjms.broker;

import it.baccan.vmjms.VmjmsErrorCodes;
import jakarta.jms.JMSRuntimeException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Registry holding shared InMemoryBroker instances by a string identifier, and owner of the
 * single expiry-reclamation thread of the JVM.
 */
public class InMemoryBrokerRegistry {

    /**
     * Default maximum number of waiting messages for brokers created by a factory that does
     * not supply one. Consulted only when a broker is created.
     */
    public static final String MAX_MESSAGES_PROPERTY = "vmjms.broker.maxMessages";

    /**
     * Delay between expiry sweeps, in milliseconds. JVM-wide: there is one sweeper for the
     * whole JVM and it visits every broker, so the interval cannot be a property of a
     * single broker. Re-read before every sweep, so it can be changed while running.
     */
    public static final String SWEEP_INTERVAL_PROPERTY = "vmjms.sweep.intervalMillis";

    private static final long DEFAULT_SWEEP_INTERVAL_MILLIS = 1000L;

    private static final ConcurrentMap<String, InMemoryBroker> registry = new ConcurrentHashMap<>();

    private static final Object sweeperLock = new Object();

    /**
     * Null means "not started". The state is the field itself rather than a separate flag,
     * so that stopping and starting again is the same code path as the first start.
     */
    private static volatile ScheduledExecutorService sweeper;

    private InMemoryBrokerRegistry() {
        // Prevent instantiation
    }

    /**
     * Resolves or instantiates an InMemoryBroker for the given brokerId, with the default
     * maximum number of messages.
     */
    public static InMemoryBroker getOrCreate(String brokerId) {
        return getOrCreate(brokerId, null);
    }

    /**
     * Resolves or instantiates an InMemoryBroker for the given brokerId.
     *
     * @param maxMessages explicit maximum number of waiting messages, or {@code null} to
     *                    accept whatever the broker has - the default from
     *                    {@link #MAX_MESSAGES_PROPERTY} when the broker is created here
     * @throws JMSRuntimeException with error code
     *         {@link VmjmsErrorCodes#CONFLICTING_BROKER_LIMIT} if an explicit maximum
     *         differs from the one an existing broker was created with, since one of the
     *         two limits would otherwise be silently ignored
     */
    public static InMemoryBroker getOrCreate(String brokerId, Integer maxMessages) {
        if (brokerId == null) {
            throw new IllegalArgumentException("Broker ID cannot be null");
        }
        int resolved = maxMessages != null ? maxMessages : defaultMaxMessages();
        InMemoryBroker broker = registry.computeIfAbsent(brokerId, id -> new InMemoryBroker(id, resolved));

        if (maxMessages != null && broker.getMaxMessages() != maxMessages) {
            throw new JMSRuntimeException("Broker \"" + brokerId + "\" already exists with a maximum of "
                    + broker.getMaxMessages() + " messages, which conflicts with the requested maximum of "
                    + maxMessages + "; the limit of a broker is fixed when it is created",
                    VmjmsErrorCodes.CONFLICTING_BROKER_LIMIT);
        }
        return broker;
    }

    /**
     * Resolves a Broker ID without creating anything.
     *
     * @return the broker, or {@code null} if the registry holds none for that identifier
     */
    public static InMemoryBroker find(String brokerId) {
        return brokerId == null ? null : registry.get(brokerId);
    }

    /**
     * The Broker IDs currently held, as an immutable snapshot.
     */
    public static Set<String> brokerIds() {
        return Set.copyOf(registry.keySet());
    }

    /**
     * Destroys one broker: removes it from the registry, terminates it, discards its
     * messages, unregisters its MBean and unblocks the threads waiting on its queues.
     *
     * <p>Idempotent. Resolving the same Broker ID afterwards creates a new, empty broker.</p>
     *
     * @return true if a broker was actually removed
     */
    public static boolean destroy(String brokerId) {
        InMemoryBroker broker = brokerId == null ? null : registry.remove(brokerId);
        if (broker == null) {
            return false;
        }
        broker.destroy();
        return true;
    }

    /**
     * Destroys every registered broker and stops the expiry-reclamation thread, leaving no
     * thread and no MBean behind.
     *
     * <p>Meant to be called when no producer or consumer is active, typically between
     * tests. Reclamation starts again by itself for brokers created afterwards.</p>
     */
    public static void clear() {
        for (String brokerId : new ArrayList<>(registry.keySet())) {
            destroy(brokerId);
        }
        stopSweeper();
    }

    // ------------------------------------------------------------------
    // Expiry reclamation
    // ------------------------------------------------------------------

    /**
     * Starts the sweeper if it is not running. Called on the enqueue of a message that can
     * expire, so a JVM that never sets a time-to-live never creates the thread.
     */
    static void ensureSweeperStarted() {
        if (sweeper == null) {
            startSweeper();
        }
    }

    private static void startSweeper() {
        synchronized (sweeperLock) {
            if (sweeper != null) {
                return;
            }
            sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "vmjms-sweeper");
                thread.setDaemon(true);
                return thread;
            });
            scheduleNextSweep();
        }
    }

    /**
     * Arms the next sweep. The task re-arms itself rather than using
     * {@code scheduleAtFixedRate} or {@code scheduleWithFixedDelay}, both of which stop
     * re-arming a task that throws: with one sweeper for the whole JVM that would stop
     * reclamation for every broker, silently. Re-arming here also re-reads the interval and
     * guarantees a fixed delay between sweeps.
     */
    private static void scheduleNextSweep() {
        ScheduledExecutorService current = sweeper;
        if (current == null) {
            return;
        }
        try {
            current.schedule(InMemoryBrokerRegistry::sweepPass, sweepIntervalMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // The sweeper has been shut down: this is how the chain terminates.
        }
    }

    private static void sweepPass() {
        try {
            for (InMemoryBroker broker : registry.values()) {
                try {
                    broker.sweepExpired();
                } catch (Throwable failure) {
                    // Per broker, so that one failing broker does not cost the others
                    // their sweep.
                    broker.recordSweepFailure(failure);
                }
            }
        } catch (Throwable failure) {
            // A failure of the iteration itself cannot be attributed to a broker; the
            // chain must survive it anyway.
        } finally {
            scheduleNextSweep();
        }
    }

    private static void stopSweeper() {
        synchronized (sweeperLock) {
            if (sweeper == null) {
                return;
            }
            // Not shutdown(): clear() must not wait for a sweep in progress.
            sweeper.shutdownNow();
            sweeper = null;
        }
    }

    /**
     * The delay between sweeps, from {@link #SWEEP_INTERVAL_PROPERTY}, defaulting to one
     * second. An unparseable or non-positive value is ignored.
     */
    static long sweepIntervalMillis() {
        String configured = System.getProperty(SWEEP_INTERVAL_PROPERTY);
        if (configured != null) {
            try {
                long interval = Long.parseLong(configured.trim());
                if (interval > 0) {
                    return interval;
                }
            } catch (NumberFormatException e) {
                // Fall through to the default.
            }
        }
        return DEFAULT_SWEEP_INTERVAL_MILLIS;
    }

    /**
     * The default maximum number of messages, from {@link #MAX_MESSAGES_PROPERTY},
     * defaulting to effectively unbounded. An unparseable or negative value is ignored.
     */
    static int defaultMaxMessages() {
        String configured = System.getProperty(MAX_MESSAGES_PROPERTY);
        if (configured != null) {
            try {
                int max = Integer.parseInt(configured.trim());
                if (max >= 0) {
                    return max;
                }
            } catch (NumberFormatException e) {
                // Fall through to the default.
            }
        }
        return Integer.MAX_VALUE;
    }
}
