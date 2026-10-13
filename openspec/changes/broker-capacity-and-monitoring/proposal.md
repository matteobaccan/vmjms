## Why

`vmjms` keeps every message in RAM in unbounded `LinkedBlockingQueue`s and
nothing ever removes a message except a consumer. Three consequences follow,
and they are all the same defect seen from different angles:

- A producer with no consumer, or a consumer slower than its producer, grows
  the heap until the JVM dies. There is no way to cap it, and
  `MessageProducer.send` has no failure mode short of `OutOfMemoryError`.
- `send` already computes and stores `JMSExpiration`, but no component ever
  reads it: an expired message is delivered as if it were live, and occupies
  memory forever.
- There is no way to observe any of this. Queue depths, lifetime enqueue and
  dequeue counts and rejections are invisible, so an operator cannot tell a
  slow consumer from a stuck one.

A capacity limit without expiry reclamation would be actively misleading: a
broker full of messages that expired hours ago would reject a legitimate `send`
while holding nothing but garbage. The three concerns share one prerequisite
and therefore one change.

The same prerequisite brings a fourth gap with it. Brokers live in a static
registry and nothing ever releases a single one: `InMemoryBrokerRegistry.clear()`
drops them all at once, and is the only way out. Once the broker owns a capacity
budget, a background sweep and an MBean, "release one broker" stops being a
memory question and becomes a lifecycle one, so this change adds it rather than
leaving a half-managed object behind.

## What Changes

- Add a **broker-wide** maximum number of waiting messages, configured when the
  broker is created, or defaulted for the whole JVM through the system property
  `vmjms.broker.maxMessages`. The limit applies to the sum of all queues of that
  broker, not to each queue.
- Make `send` fail immediately with a dedicated, numbered exception
  (`VMJMS-1001`) when the broker is at capacity, carrying the configured limit
  and the queue involved. `send` never blocks and never waits for room.
- Make the broker the single point through which messages are enqueued and
  dequeued, so that it can account for capacity instead of handing out the raw
  queue and losing sight of it.
- Honour `JMSExpiration`: an expired message is never delivered, and is removed
  from memory automatically by a background sweep that does not stop producer or
  consumer traffic. No dead-letter queue. Sweeps are separated by a fixed delay
  of one second, configurable for the whole JVM through
  `vmjms.sweep.intervalMillis`.
- Add **explicit broker lifecycle management**: look a Broker ID up without
  creating it, list the Broker IDs held, and destroy one broker or all of them.
  A destroyed broker is terminated, not merely evicted: it unblocks its waiting
  consumers and refuses further use, so a connection still holding a reference
  cannot keep exchanging messages against a broker the registry no longer
  resolves. Closing a connection still does not touch the broker.
- Add on-demand JMX monitoring of a broker, which can be enabled and disabled
  while the broker is running.
- Remove the hand-rolled `main()` runner and the private assertion helpers from
  `InMemoryJMSTest`, which predate the Maven build and are no longer executed by
  anything, before adding new tests to that class.

## Capabilities

### New Capabilities

- `broker-monitoring`: on-demand JMX exposure of the capacity, queue depths and
  lifetime counters of a broker.

### Modified Capabilities

- `in-memory-jms`: queue storage becomes broker-mediated, gains a broker-wide
  capacity limit with a numbered failure, gains expired-message reclamation, and
  gains an explicit broker lifecycle.

## Non-goals

- Per-queue limits. A single queue may consume the whole broker budget and
  starve the others; this is accepted.
- Changing the limit at runtime. The limit is fixed when the broker is created.
- Blocking or timed sends (`send` that waits for room). A full broker is always
  an error.
- A dead-letter queue, or any retention of expired messages.
- Flow control, producer throttling or back-pressure signalling.
- Honouring `JMSPriority`. Delivery stays FIFO regardless of priority. The
  header is written on send and read by nobody, exactly as `JMSExpiration` is
  today, but unlike expiry it has no bearing on memory and therefore none on
  capacity, which is what this change is about.
- Honouring `deliveryDelay`. The producer stores it and nothing applies it. A
  delayed message would be a message occupying capacity while invisible, and it
  would break the assumption that the head of the queue is deliverable, which is
  a structural change rather than an addition.
- Reference-counted brokers (a broker destroyed when its last connection
  closes). It would silently break "send, close, reopen, receive", which is how
  a test uses this library. Destruction stays explicit.

## Impact

- **Consumers**: `send` gains a new checked failure mode. Code that catches
  `JMSException` is unaffected; code that relied on unbounded queueing must now
  either raise the limit or consume. Default behaviour with no configured limit
  is unchanged (effectively unbounded).
- **Behaviour change**: a message whose `JMSExpiration` has passed is no longer
  delivered. Today it is, which makes `setTimeToLive` a setting with no effect.
  Nothing in the project depends on that: no test sets a time-to-live and
  `getJMSExpiration()` has no production caller.
- **Runtime**: at most one additional daemon thread per JVM, created only once a
  message with a non-zero time-to-live has been sent, and shut down by
  `InMemoryBrokerRegistry.clear()`.
- **Internal API**: `InMemoryBroker.getOrCreateQueue` stops being the access
  path for producers and consumers, and queues no longer hold `Message`
  directly.
- **Public API**: `InMemoryConnectionFactory` gains constructors taking a
  maximum, and `InMemoryBrokerRegistry` gains lifecycle methods. Operations on a
  broker destroyed out from under a live connection now fail where they
  previously succeeded against an unreachable broker.
- **Tests**: new tests are JUnit-only. The duplicate `main()` runner is removed
  rather than extended with the ~25 scenarios this change adds.
