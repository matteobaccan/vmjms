## 0. Clear the ground in the test class

- [x] 0.1 Remove the hand-rolled `main()` runner from `InMemoryJMSTest`, leaving the 8 JUnit `@Test` methods as the only entry point.
- [x] 0.2 Replace the private `assertEquals` / `assertNull` / `assertNotNull` / `assertTrue` helpers with `org.junit.jupiter.api.Assertions`.
- [x] 0.3 Confirm `mvn -B -ntp verify` still reports the same 8 tests passing before any new behaviour is added.

## 1. Make the broker the accounting point

- [x] 1.1 Introduce a `QueuedMessage` envelope holding the payload, the absolute expiration as a `long` and a claim flag, created once per `send`.
- [x] 1.2 Change the queues of the broker to hold envelopes instead of `Message`.
- [x] 1.3 Add broker-mediated enqueue and dequeue operations (including the blocking, timed and non-waiting forms) and stop exposing the raw queue as the access path for producers and consumers.
- [x] 1.4 Route `InMemoryMessageProducer.send` through the broker, replacing the `offer(...)` whose result is currently discarded.
- [x] 1.5 Route `InMemoryMessageConsumer.receive()` / `receive(timeout)` / `receiveNoWait()` through the broker, preserving the `waitingThread` registration and the interruption-on-close behaviour.
- [x] 1.6 Confirm the existing cascading-close test still unblocks a waiting consumer with `jakarta.jms.IllegalStateException`.
- [x] 1.7 Test that closing a connection leaves the messages already sent available to a consumer created later from another connection on the same Broker ID.
- [x] 1.8 Keep the queues unreachable: make the queue accessor private and give the sweep-failure tests a narrow package-private insertion point that refuses an envelope holding no capacity, with a test for that refusal.

## 2. Broker-wide capacity limit

- [x] 2.1 Give the broker a capacity semaphore sized to the configured maximum, defaulting to effectively unbounded.
- [x] 2.2 Reserve capacity atomically before enqueueing, and release it on every removal path through a single claim-based accounting point.
- [x] 2.3 Accept the maximum on `InMemoryConnectionFactory`, and read `vmjms.broker.maxMessages` as the default for factories that do not specify one, consulting it only when the broker is created.
- [x] 2.4 Fail fast when a factory supplies, for an existing shared Broker ID, an explicit maximum that differs from the one of the broker; a factory supplying no maximum never conflicts, whatever the property says.
- [x] 2.5 Tests: limit spanning several queues; capacity freed by consumption; unbounded default; isolation of limits between brokers; default taken from the system property; conflicting shared limit refused; no conflict for a factory without an explicit maximum.
- [x] 2.6 Concurrency test: many producers against a small limit, asserting that accepted messages plus rejections equal the attempts and that the broker holds exactly the limit.

## 3. Numbered capacity exception

- [x] 3.1 Add the capacity exception deriving from `jakarta.jms.ResourceAllocationException`, carrying the error code via the `(reason, errorCode)` constructor.
- [x] 3.2 Expose the configured limit, the target queue name and the count observed at rejection as accessors, and state the limit in the message text.
- [x] 3.3 Document the `VMJMS-<number>` error-code scheme, reserve `VMJMS-1xxx` for capacity and resource failures, and assign `VMJMS-1001` to the capacity rejection and `VMJMS-1002` to the conflicting shared limit.
- [x] 3.4 Report the conflicting shared limit as an unchecked `jakarta.jms.JMSRuntimeException` carrying `VMJMS-1002` and naming the Broker ID with both limits, thrown while the factory is being constructed.
- [x] 3.5 Tests: immediate failure with no blocking; message not enqueued and capacity not consumed; limit, queue and error code reported; catchable as `JMSException`; conflict unchecked at wiring time with its own code.

## 4. Expired message reclamation

- [x] 4.1 Skip and discard expired envelopes on the receive path, releasing their capacity and continuing to wait with the remaining timeout rather than returning early.
- [x] 4.2 Add a broker sweep that removes expired envelopes using a bulk removal that evaluates its predicate without holding the queue lock.
- [x] 4.3 Track, per broker, the number of enqueued messages that can expire, so a broker with none is never scanned.
- [x] 4.4 Add a single shared daemon scheduler owned by `InMemoryBrokerRegistry`, its thread named `vmjms-sweeper`, started lazily on the first expiring message through double-checked locking on the scheduler field itself.
- [x] 4.5 Implement the sweep as a self-re-arming task: `catch (Throwable)` around the pass, re-arm in `finally`, interval re-read from `vmjms.sweep.intervalMillis` at every re-arm, default one second between sweeps.
- [x] 4.6 Isolate failures per broker inside the iteration, and swallow the `RejectedExecutionException` raised by the re-arm after shutdown.
- [x] 4.7 Stop the scheduler in `InMemoryBrokerRegistry.clear()` with `shutdownNow()`, returning the field to `null` so the next expiring message starts it again.
- [x] 4.8 Tests: expired message not delivered; FIFO preserved across a skipped message; reclamation with no consumer present; reclaimed capacity reusable; zero time-to-live retained indefinitely.
- [x] 4.9 Tests: interval configurable JVM-wide and honoured by two brokers at once; a failing sweep does not stop later sweeps and does not cost the other brokers their pass; reclamation works again after `clear()`.
- [x] 4.10 Concurrency test: a consumer and a sweep racing on the same expired message release its capacity exactly once.
- [x] 4.11 Test that no non-daemon thread survives `clear()`.

## 5. Explicit broker lifecycle

- [x] 5.1 Add a `volatile boolean terminated` to the broker, checked at the broker-mediated enqueue and dequeue, so use after destruction fails with `jakarta.jms.IllegalStateException`.
- [x] 5.2 Add destruction on the broker: terminate, discard the queues, release capacity bookkeeping, unregister the MBean if monitoring is enabled, and unblock the threads waiting in `receive()` by interrupting them, reusing the mechanism of consumer close.
- [x] 5.3 Add registry lifecycle methods: non-creating lookup, listing of the Broker IDs held, destruction of one broker reporting whether it existed, and destruction of all.
- [x] 5.4 Redefine `clear()` as destroying every broker it holds, plus stopping the scheduler, so there is a single teardown path.
- [x] 5.5 Tests: destroyed broker refuses sends and receives; a blocked consumer is unblocked with `IllegalStateException`; a destroyed Broker ID is reusable and comes back empty; destruction is idempotent and reports whether a broker existed; non-creating lookup does not create; listing reflects creations and destructions; `clear()` terminates every broker.

## 6. On-demand JMX monitoring

- [x] 6.1 Add the broker MBean interface exposing the read-only attributes and the purge and sweep operations.
- [x] 6.2 Maintain the lifetime counters (enqueued, delivered, expired, purged, rejected) at the accounting point introduced in section 1, with the purge counted as purged and not as expired.
- [x] 6.3 Expose the per-broker sweep failure count and the text of the last failure as attributes.
- [x] 6.4 Add idempotent enable and disable on the broker, registering under `it.baccan.vmjms:type=Broker,id=<quoted Broker ID>` with `ObjectName.quote` applied unconditionally, and expose the Broker ID attribute unquoted.
- [x] 6.5 Unregister on destruction and in `InMemoryBrokerRegistry.clear()`.
- [x] 6.6 Tests: off by default; enable and disable while in use; re-enable; double enable; two brokers independent; a Broker ID containing `:` and `,` registers successfully; object-name form identical for a clean Broker ID; nothing registered after destruction or cleanup.
- [x] 6.7 Tests: depth reflects traffic; rejections counted and excluded from enqueued; expired counted and excluded from delivered; purged counted separately; the reconciliation `waiting == enqueued − delivered − expired − purged` holds after all five paths have been exercised; limit attribute not writable; purge frees capacity; a failed sweep is visible.

## 7. Documentation

- [x] 7.1 Document the capacity limit, its configuration through the factory and `vmjms.broker.maxMessages`, and the exception including `VMJMS-1001`, in the README.
- [x] 7.2 Document message expiry and automatic reclamation, the one-second default and `vmjms.sweep.intervalMillis` as a JVM-wide setting, and that there is no dead-letter queue.
- [x] 7.3 Document the broker lifecycle: destroying a broker, what terminated means for connections still holding it, and that closing a connection does not destroy the broker.
- [x] 7.4 Document enabling and disabling JMX monitoring, the object name with its quoting, and the exposed attributes and operations.
- [x] 7.5 Note in the README that a single queue may consume the whole broker budget, and that `JMSPriority` and `deliveryDelay` are accepted but not honoured.

## 8. Verification

- [x] 8.1 Recompile main and test sources from scratch.
- [x] 8.2 Run the whole test suite and confirm every test passes, including the pre-existing ones, on JDK 17 and JDK 21 as CI does.
- [x] 8.3 Confirm the suite leaves no `vmjms-sweeper` thread and no registered MBean behind, and that it does not depend on test execution order.
