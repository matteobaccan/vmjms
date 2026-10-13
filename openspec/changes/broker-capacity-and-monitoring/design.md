## Context

Today `InMemoryBroker.getOrCreateQueue(name)` returns the raw
`BlockingQueue<Message>` and the broker then plays no further part:
`InMemoryMessageProducer.send` calls `offer(...)` on it (discarding the boolean
result) and `InMemoryMessageConsumer` calls `take()` / `poll(...)` on it
directly. The broker therefore observes neither enqueues nor dequeues.

Every requirement in this change needs the broker to observe them:

- a broker-wide count must be decremented when a message leaves, wherever it
  leaves from;
- JMX counters are that same accounting, read from outside;
- removing expired messages is a third kind of removal that must be accounted
  for identically.

So the structural decision comes first, and the rest follows from it.

## Decisions

### The broker becomes the access path

Alternatives considered:

1. **Decorating `BlockingQueue`** — `getOrCreateQueue` returns a wrapper that
   notifies the broker. Producer and consumer stay as they are.
2. **Routing through the broker** — `broker.send(queueName, ...)` and
   `broker.receive(queueName, timeout)`; the producer and consumer become thin.

Decision: option 2. The decorator has to intercept every mutating method of
`BlockingQueue`, and the interface has many that remove elements
(`poll`, `drainTo`, `remove`, `removeIf`, `clear`, `iterator().remove()`, ...).
Missing one does not fail: it silently desynchronises the capacity counter, and
a counter that drifts upward makes an empty broker declare itself full. With an
explicit broker API the set of state-mutating operations is closed and small.

The consumer's interruption-on-close mechanism is preserved: the broker's
`receive` delegates the blocking wait to the underlying queue, so the consumer
still registers its `waitingThread` and is still unblocked by interruption.

### Messages are wrapped in an envelope

Queues hold a `QueuedMessage` envelope rather than a `Message`:

- `Message payload`
- `long expiresAt` — the absolute `JMSExpiration`, cached as a primitive so the
  expiry sweep does not have to call `getJMSExpiration()`, which declares
  `throws JMSException`, inside its predicate.
- `AtomicBoolean claimed` — see below.

A `Message` instance cannot carry this state itself: JMS allows a sender to
reuse the same `Message` object across several `send` calls, and the current
implementation enqueues the instance it is given. Two sends of the same object
would share one envelope's worth of state. An envelope is created per `send`,
so uniqueness holds regardless of what the caller does with the message object.

### Capacity is a semaphore

The limit is fixed when the broker is created, so a `Semaphore` initialised to
the limit is the simplest correct primitive: `tryAcquire()` is the atomic
"reserve a slot or fail" that `send` needs, with no check-then-act window. A
plain `AtomicInteger` with a compare-and-set loop would be equivalent but more
code, and its only advantage — a limit that can be resized at runtime — is
explicitly out of scope.

An unconfigured broker is given `Integer.MAX_VALUE` permits rather than a
conditional fast path, so there is a single code path for all brokers.

`availablePermits()` additionally provides the free-capacity gauge for JMX
without separate bookkeeping.

### Exactly-once release: the claim flag

The release of a permit must happen exactly once per message, and a message can
leave a queue in three ways: taken by a consumer, removed by the expiry sweep,
or purged administratively. These races are real, not theoretical:
`LinkedBlockingQueue.removeIf` is implemented by `bulkRemove`, which evaluates
the predicate **with the queue lock released** and then re-checks each node
before unlinking it. An element the predicate judged expired may therefore have
been consumed in the meantime and not be unlinked at all — so "my predicate
returned true" does not mean "I removed it", and `bulkRemove` only reports
whether *anything* was removed.

Decision: ownership of the release is acquired by compare-and-set on the
envelope's `claimed` flag, not inferred from who unlinked the node. Whoever
wins `claimed: false -> true` performs the release. Both the consumer and the
sweep claim before accounting, so the count is exact regardless of who wins the
race, and no coordination between them is required.

As a direct consequence the consumer also discards expired envelopes it has
dequeued and continues waiting, which is the "never deliver an expired message"
half of the requirement; the sweep is only about reclaiming memory.

### Expiry reclamation: background sweep, not sweep-on-demand

Alternatives considered:

1. **Lazy only** — expired messages are discarded when a consumer reaches them.
   Frees nothing while no one consumes, which is exactly the situation a
   capacity limit exists for.
2. **Sweep on pressure** — when `send` finds the broker full, sweep and retry
   once before failing. No threads, cost paid only under pressure, but it pays
   that cost as latency on a producer's `send`.
3. **Background sweep** — a scheduled task reclaims expired messages
   independently of traffic.

Decision: option 3 (with option 1 retained for delivery correctness, which it
provides for free). Memory must be reclaimed automatically, and the reclamation
must not be charged to in-flight traffic.

Non-blocking is achievable with the JDK as it is: `LinkedBlockingQueue.removeIf`
delegates to `bulkRemove`, which extracts batches of up to 64 nodes under the
lock, evaluates the predicate with the lock released, and re-acquires it only to
unlink the marked nodes. The lock is held for pointer manipulation on 64 nodes
at a time, never for the scan itself, so producers and consumers interleave with
the sweep rather than waiting for it.

### Sweep scheduling

- **One shared scheduler per JVM**, owned by `InMemoryBrokerRegistry`, with a
  single thread. One thread per broker would mean one thread per broker in a
  test suite that creates hundreds.
- **The thread is a daemon.** A non-daemon thread in a library used by test
  suites would prevent JVM exit, and would be observed as "the build no longer
  terminates" — a symptom very far from its cause.
- **Started lazily**, on the first enqueue of a message that can expire. The
  default `timeToLive` is `0`, which `send` already translates to
  `JMSExpiration = 0` meaning "never expires", so the common case never creates
  a thread at all.
- **Skipped cheaply**: each broker keeps an `AtomicInteger` of currently
  enqueued messages that can expire, incremented on enqueue and decremented on
  claim. When it is zero the broker's queues are not touched. This is exact
  rather than heuristic, and costs one read per sweep per broker.
- `InMemoryBrokerRegistry.clear()` stops the scheduler, so a test suite leaves
  no thread behind.

### Configuration

The limit is supplied where the broker is created, i.e. through
`InMemoryConnectionFactory`, and a system property provides the default for
factories that do not specify one. The property matters because it allows a cap
to be imposed on an existing suite without editing the code that builds the
factories.

Because brokers are shared through `InMemoryBrokerRegistry`, two factories can
name the same broker. Sharing a Broker ID is the documented way to share
queues, so it is not in itself an error: the first factory to resolve the id
creates the broker with its limit, and a factory that asks for no limit
inherits whatever the broker has. Only two *conflicting explicit* limits are an
error, because one of the two would silently not be honoured while the code
that set it keeps claiming otherwise.

The system property is a default, not a request. It is consulted only when the
broker is created; a factory that specifies no limit never conflicts, whatever
the property says. Treating the property as a declaration would mean that
setting one globally breaks every configuration that shares a broker.

The conflict is reported as `jakarta.jms.JMSRuntimeException`, carrying its own
code through the same `getErrorCode()` channel as the capacity failure, and
naming the Broker ID together with both the existing and the requested limit.
It is unchecked deliberately: `InMemoryConnectionFactory`'s constructors do not
declare `throws JMSException` today, and making them declare it would break
every existing call site for the sake of a condition that is a configuration
mistake rather than a messaging failure. It surfaces when the factory is
constructed — at wiring time, before any traffic — so an unhandled one stops
the application at startup, which is the right outcome for a misconfiguration.

Which of two concurrently constructed factories observes the conflict is not
deterministic, since `computeIfAbsent` decides who creates the broker; that one
of them fails is.

Queue-name URI parameters are deliberately not used for this. They are parsed
per destination (`InMemoryQueue.getParameters()`, which currently has no
production consumer), while the limit is a property of the broker; and since
queues are created by whoever touches them first, a limit carried on a
destination name would take effect or not depending on which participant
happened to create the queue.

### The numbered exception

`jakarta.jms.ResourceAllocationException` already exists for exactly this
condition — "a provider is unable to allocate the resources required by a
method" — and extends `JMSException`, so the new exception derives from it
rather than from `JMSException` directly. Code that already handles resource
exhaustion from a real broker keeps working.

JMS's own channel for a numbered error is `JMSException.getErrorCode()`, so the
number travels there rather than in a bespoke field. Codes are namespaced and
grouped by kind, with `VMJMS-1xxx` reserved for capacity and resource failures
and `VMJMS-1001` assigned to this one, so later additions do not have to
renumber anything.

The configured limit, the queue that the rejected `send` targeted and the count
observed at rejection are exposed as accessors as well as being interpolated
into the message text: the text is for logs, the accessors are for code that
reacts.

### JMX

One MBean per broker, registered in the platform MBean server under
`it.baccan.vmjms:type=Broker,id=<brokerId>`. Broker ids are unique in the
registry, so object names do not collide.

Exposed read-only: broker id, configured limit, current total, per-queue depths,
and lifetime counters for enqueued, dequeued, expired and rejected messages.
`rejected` is the one that matters operationally — it is the signal that the
limit is biting, available before anyone opens a report.

Operations are limited to ones that cannot corrupt accounting: purging a queue
and triggering a sweep, both of which go through the same claim-based release as
every other removal. The limit is not writable, consistent with it being fixed
at construction.

Enabling and disabling are idempotent, because a library that throws
`InstanceAlreadyExistsException` on a second `enableJmx()` is a library that
gets wrapped in a try/catch by every caller. `InMemoryBrokerRegistry.clear()`
unregisters, so a suite that enables JMX per test does not accumulate MBeans.

### The sweep is a task that cannot die

`ScheduledExecutorService.scheduleAtFixedRate` and `scheduleWithFixedDelay` both
stop re-arming a task that throws: "if any execution of the task encounters an
exception, subsequent executions are suppressed". With one shared scheduler per
JVM that is a single point of silent failure — one unexpected throwable and
reclamation stops for **every** broker in the JVM, with no exception surfacing
anywhere and no thread dying to show for it. The symptom would be "memory is no
longer reclaimed", arbitrarily far from its cause.

Decision: the sweep does not use the periodic forms at all. It is a task that
re-arms itself:

```
run() {
    try               { sweepAllBrokers(); }
    catch (Throwable) { record the failure on the affected broker; }
    finally           { schedule(this, intervalMillis(), MILLISECONDS); }
}
```

Because the re-arm is in the `finally`, no throwable — `Error` included — can
break the chain. Three properties fall out of the same five lines:

- **A fixed delay between sweeps.** The next sweep is scheduled when the
  previous one ends, so the duration of a sweep is never charged to the next,
  and a sweep that takes longer than the interval cannot queue up behind
  itself. This is what `scheduleWithFixedDelay` would have given, obtained
  without the suppression behaviour.
- **A re-readable interval.** `intervalMillis()` is read at every re-arm, so
  `vmjms.sweep.intervalMillis` can be changed while the system is running, and
  a test suite that wants a 50 ms interval does not have to set the property
  before the first expiring send.
- **Immunity to suppression**, which is the point above.

A watchdog task that detects a dead sweeper and resubmits it was considered and
rejected: it is a second mechanism that can itself fail, and it needs a
staleness threshold to be tuned, to solve a problem that `finally` removes.

Two details the structure depends on:

- The `try`/`catch` for a broker goes **inside** the iteration, not around it,
  so one failing broker does not cost the others their sweep.
- After `shutdownNow()` the re-arm throws `RejectedExecutionException`. That is
  not an error: it is how the chain terminates. It is swallowed deliberately,
  otherwise every `clear()` prints a stack trace.

The project has no logging framework and this change does not add one, so a
sweep failure is surfaced where the operator already looks: a failure count and
the text of the last failure, as attributes of the broker MBean. Attributing
them per broker is exact, because the catch is per broker.

### Restarting is the same code path as starting

`clear()` stops the shared scheduler, and the suite goes on: a test that sends a
message with a time-to-live after a `clear()` must get its reclamation back. The
failure mode to avoid is a separate "already started" flag, which `clear()`
would leave set — the next expiring message then has no sweeper, and nothing
fails. A green suite with reclamation switched off is worse than a broken one.

Decision: the state "started" is not held separately, it *is* the field.

```
volatile ScheduledExecutorService sweeper;     // null == not started

on enqueue of an expiring message:             clear():
    if (sweeper == null) startLazily();            synchronized (lock) {
                                                       if (sweeper != null) {
startLazily():  // synchronized (lock)                     sweeper.shutdownNow();
    if (sweeper != null) return;  // re-check              sweeper = null;
    sweeper = newSingleDaemonScheduler();              }
    sweeper.schedule(task, intervalMillis());      }
```

Double-checked locking: a volatile read on the hot path (every send with a
time-to-live), the lock only on the cold one. `clear()` returns the field to
`null` under the same lock, so restarting is not a special case — it is the
first start again, with no residual state that can lie.

`shutdownNow()` rather than `shutdown()`: `clear()` must not block for up to a
whole interval waiting for a sweep to finish. The thread is a named daemon
(`vmjms-sweeper`), which makes a thread dump self-explanatory and lets the
"no non-daemon thread survives" test assert on a name instead of a count.

### Destroying a broker terminates it

Making destruction mean only "evict from the registry" leaves the destroyed
broker alive in the hands of every factory, connection, session, producer and
consumer still referencing it, while the same Broker ID now resolves to a new
broker:

```
registry:  "x" ──╳
conn1 ──► broker "x" (orphan)     ← send/receive still succeed here
factory.new("x") ──► broker "x"   ← and here, on a different, empty broker
                                    two brokers, one name, no error anywhere
```

In a test suite that surfaces as messages that vanish, with an outcome that
depends on test order and no exception to follow. Decision: destruction
terminates. A `volatile boolean terminated` is checked at the broker-mediated
enqueue and dequeue — the single accounting point this change already
introduces, so the check costs one volatile read on a path that already has
one — and any use after destruction fails with
`jakarta.jms.IllegalStateException`.

Threads blocked in `receive()` are unblocked by interrupting them, which is not
a new mechanism: `InMemoryMessageConsumer.close()` already registers its
`waitingThread` and interrupts it, and the suite already tests that the blocked
caller surfaces `IllegalStateException`. Destruction hoists the same mechanism
one level up, to the broker, which after this change is the component that knows
who is waiting.

`clear()` is then defined as destroying every broker it holds, so there is one
teardown path rather than two. A non-creating lookup and a listing of the Broker
IDs held come with it: without them the only way to find out whether a broker
exists is `getOrCreate`, which creates it.

Connection close deliberately keeps its current semantics and does **not**
destroy the broker. The broker is shared through the registry, and
"send, close the connection, reopen, receive" is how a test uses this library;
reference counting would break it silently. Destruction is an explicit gesture.

### Object names are always quoted

`ObjectName` forbids `:`, `,`, `=`, `"`, `*` and `?` in a value, and the Broker
ID is written by the user — `new InMemoryConnectionFactory("orders:test")` is
legal everywhere else in the library. Concatenating it into the object name
makes `enableJmx()` throw `MalformedObjectNameException` for ids that nothing
else rejects.

Decision: `ObjectName.quote(brokerId)`, unconditionally. Quoting only when
necessary would make the shape of the object name depend on the content of the
id — `id=orders` for one broker and `id="orders:test"` for another — so an
operator's script matching `id=orders` would work for some brokers and not
others; and there is no public API that answers "is this value valid?", so
"when necessary" would mean discovering it with a try/catch. The cost of
quoting always is cosmetic (quotes appear in jconsole on clean ids too) and is
offset by the Broker ID attribute, which reports the id exactly as supplied.

### Purged messages are counted separately

A purge takes the same route as an expiry — same claim, same release — as
decided; only the counter differs. The release mechanism stays shared, so none
of the concurrency reasoning is reopened by giving the purge its own
`AtomicLong`.

The reason not to fold it into `expired`: `expired` is the operational signal
that time-to-live values are too short or consumers too slow. A manual
administrative purge inflating it corrupts exactly the number someone is
reading while investigating an incident. With five counters the reconciliation
is also complete and testable:

```
waiting == enqueued − delivered − expired − purged        (rejected never entered)
```

That invariant is one test crossing all five accounting paths at once, and it
fails if a sixth removal path is ever added without being counted — the same
class of defect the claim flag prevents for capacity, and that nothing would
otherwise prevent for the counters.

### Configuration property names and their scope

- `vmjms.broker.maxMessages` — the default limit for a broker that is created
  without an explicit one. Per broker, consulted only at creation.
- `vmjms.sweep.intervalMillis` — the delay between sweeps. **JVM-wide**, because
  there is one sweeper for the JVM and it iterates every broker; a per-broker
  interval would be unimplementable without one thread per broker, which is
  explicitly rejected above. The name says `sweep`, not `broker`, so that the
  scope is legible from the property itself.

### The hand-rolled test runner goes before the new tests arrive

`InMemoryJMSTest` carries both 8 JUnit `@Test` methods and a `main()` that
re-runs the same 8 with its own `assertEquals`/`assertNull`/`assertTrue` and
`passed`/`failed` counters. The duplication is archaeological: the tests were
written by the `in-memory-jms-server` change, before `rebrand-and-build-automation`
introduced Maven, JUnit and surefire, when `java InMemoryJMSTest` was the only
way to run them. CI now runs `mvn verify`, so nothing executes that `main()`.

This change adds roughly 25 scenarios to that class. Keeping the duplicate
runner means writing each of them twice, and the copy that nobody runs would
stay green by construction. It is removed first, together with the private
assertion helpers (which shadow JUnit's and give worse failure messages), so the
new tests are written once.

## Risks / Trade-offs

- **Expired messages are no longer delivered.** This is a change in observable
  behaviour but not a trade-off: a time-to-live whose elapsing changes nothing
  is not a guarantee anyone can build on, so today's behaviour is the defect
  being fixed. `send` already writes `JMSExpiration` and
  `getJMSExpiration()` has no production caller, no test in the suite sets a
  time-to-live, and the README documents no expiry semantics, so nothing in the
  project can be relying on an expired message still being delivered.
- **A single queue can exhaust the broker budget** and starve the others. This
  is accepted; a per-queue cap remains possible later without revisiting the
  structure, since the accounting point already exists.
- **Reclamation is periodic, not immediate.** Memory is freed within one sweep
  interval of expiry, not at the instant of expiry. An expiry-ordered index
  could wake exactly on time, but it would have to remove messages one at a
  time, at a cost per message, where a sweep removes a whole batch in one pass.
  Precision to the millisecond has no value here; not disturbing traffic does.
- **The internal queue type changes.** `getOrCreateQueue` is public on
  `InMemoryBroker`, so anything reaching into it breaks. No released version
  exists, and the README documents no such usage.
- **The unbounded default sits exactly on the overflow boundary.**
  `Semaphore.release()` throws `java.lang.Error("Maximum permit count exceeded")`
  if the permit count would pass `Integer.MAX_VALUE`, which is the count an
  unconfigured broker starts with. Only the exactly-once release makes that
  unreachable, so an accounting bug on the most common configuration surfaces as
  an `Error` rather than as drift. That is deliberate — loud beats silent — and
  it is precisely why the sweep task must survive a `Throwable` rather than an
  `Exception`.
- **The sweep makes the growth of the registry a recurring cost.** Every
  `new InMemoryConnectionFactory()` without a Broker ID creates a broker that
  nothing releases, so a suite with 500 anonymous factories holds 500 brokers.
  Until now that was inert memory; the sweeper walks all of them once per
  interval. The per-broker cost is one atomic read, so this is not a performance
  problem, but it turns latent growth into a cost that is paid continuously.
  `destroy(brokerId)` is the release path that did not exist before this change;
  `clear()` remains the bulk one.
- **Destruction can discard a live message already in the hands of a consumer.**
  A consumer that has dequeued an envelope and loses the claim race against a
  purge, or finds the broker terminated, discards it and keeps waiting. The same
  is true of the sweep, but there the message was expired anyway. For purge and
  destroy the message was deliverable, and losing it is the intended meaning of
  both operations; it is called out here because it is the only path on which a
  `receive()` in flight loses a live message.
- **The sweep interval is also the latency of the test suite.** A scenario that
  asserts capacity is freed by reclamation has to wait for a sweep, so at the
  default of one second each such test would cost a second of wall clock. The
  interval is configurable for exactly this reason, and the new tests set it
  rather than sleeping for the default.
- **This change grew a fourth theme.** Capacity, expiry and monitoring were one
  change because they share the accounting point. Broker lifecycle joined them
  because `clear()` has to tear down a scheduler and MBeans either way, and
  `destroy(brokerId)` is that teardown generalised; implementing the two
  separately would mean two teardown paths. The cost is a wider change than
  proposed initially.
