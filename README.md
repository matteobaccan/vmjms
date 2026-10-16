<p align="center">
  <img src="docs/logo.svg" alt="vmjms logo" width="128" height="128">
</p>

<h1 align="center">vmjms</h1>

<p align="center">
  <strong>In-memory JMS provider for Java</strong><br>
  Jakarta JMS 3.1 API &middot; no broker, no network, no disk
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-17%2B-blue" alt="Java 17+">
  <img src="https://img.shields.io/badge/Jakarta%20JMS-3.1-orange" alt="Jakarta JMS 3.1">
  <img src="https://img.shields.io/badge/license-MIT-green" alt="MIT license">
</p>

---

`vmjms` implements the `jakarta.jms` interfaces on top of plain in-process data
structures (`ConcurrentHashMap` + `LinkedBlockingQueue`). There is no broker
process, no socket, no persistence: queues live inside the JVM and disappear
when it exits. Your code keeps talking to `ConnectionFactory`, `Session`,
`MessageProducer` and `MessageConsumer` exactly as it would against ActiveMQ,
Artemis or IBM MQ — only the factory you wire in changes.

- **Group / artifact:** `it.baccan:vmjms`
- **Base package:** `it.baccan.vmjms`
- **Requires:** Java 17+, `jakarta.jms:jakarta.jms-api:3.1.0`

## Use cases

**Integration tests that need a queue, not a broker.** Swap the broker's
`ConnectionFactory` for `InMemoryConnectionFactory` in your test configuration
and the messaging paths of your application run unchanged — no Docker, no
Testcontainers, no port allocation, no `@Disabled` on the slow suite.

```java
@Bean
@Profile("test")
ConnectionFactory connectionFactory() {
    return new InMemoryConnectionFactory("integration-tests");
}
```

**Deterministic tests of consumer behaviour.** `receive(timeout)` returns
`null` on expiry and FIFO order is guaranteed, so "no message arrived",
"messages arrived out of order" and "the consumer was closed while waiting" are
reproducible assertions instead of timing-dependent flakes.

**CI pipelines without service containers.** The whole provider is a single jar
with one API dependency, so a build agent needs nothing but a JVM. No broker to
start, wait for, and tear down between jobs.

**Local development and offline demos.** Run the full application on a laptop
with no infrastructure: a developer clones the repository and the messaging
layer just works, which also makes the project easy to demo on a plane.

**In-process hand-off between modules.** When two components of the same
application need a decoupled hand-off, a JMS queue is a well-understood
contract. Starting with `vmjms` means that the day those components are split
into separate services, only the `ConnectionFactory` changes.

**Proving your code depends on the API, not on the vendor.** If an application
runs against `vmjms` it is using nothing but standard `jakarta.jms` calls — a
cheap, continuous guard against accidental coupling to a broker-specific
extension.

**Teaching and prototyping.** The provider is a handful of readable classes, so
it doubles as a worked example of what a JMS provider actually has to
implement.

**Single-process and edge applications.** For a tool deployed as one JVM that
only needs an internal work queue, a full broker is operational cost with no
return.

> Not a use case: anything that needs durability, cross-process delivery,
> publish/subscribe or transactions. Messages live in RAM and die with the JVM —
> see [what is not supported](#not-supported).

## Install

Releases are published to Maven Central, so a dependency declaration is all you
need: no repository to declare, no credentials to configure.

```xml
<dependency>
    <groupId>it.baccan</groupId>
    <artifactId>vmjms</artifactId>
    <version>1.1.0</version>
</dependency>
```

Every published file is signed, and the jars of every release are also attached
to the [GitHub Release](https://github.com/matteobaccan/vmjms/releases) if you
prefer to download them directly.

Version 1.0.0 was published to GitHub Packages and stays there as a historical
artifact; 1.1.0 onwards is on Maven Central.

## Quick start

```java
import it.baccan.vmjms.InMemoryConnectionFactory;
import jakarta.jms.*;

ConnectionFactory factory = new InMemoryConnectionFactory();

try (Connection connection = factory.createConnection()) {
    connection.start();
    Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
    Queue queue = session.createQueue("orders");

    MessageProducer producer = session.createProducer(queue);
    producer.send(session.createTextMessage("Hello World"));

    MessageConsumer consumer = session.createConsumer(queue);
    TextMessage received = (TextMessage) consumer.receive(1000);
    System.out.println(received.getText()); // Hello World
}
```

## Brokers: isolated or shared

Each `InMemoryConnectionFactory` owns an `InMemoryBroker`, which holds the
queues and the message-ID sequence.

```java
// Isolated: a brand new, private broker (unique generated id)
ConnectionFactory isolated = new InMemoryConnectionFactory();

// Shared: every factory built with the same id sees the same queues
ConnectionFactory a = new InMemoryConnectionFactory("app-broker");
ConnectionFactory b = new InMemoryConnectionFactory("app-broker"); // same broker as a
```

Brokers are resolved through the static `InMemoryBrokerRegistry`, which also
owns their lifecycle (see [Broker lifecycle](#broker-lifecycle)). In test
suites, release them between runs:

```java
@AfterEach
void tearDown() {
    InMemoryBrokerRegistry.clear();
}
```

## Queue names with parameters

A queue name may carry a query string; the name and the parameters are parsed
and kept separately, so `orders?consumer.prefetch=10` addresses the queue
`orders`. Broker-style URIs copied from a real configuration therefore do not
break the lookup.

```java
InMemoryQueue queue = (InMemoryQueue) session.createQueue("orders?priority=5&durable");
queue.getQueueName();  // "orders"
queue.getParameters(); // {priority=5, durable=}
```

Parameters are exposed for inspection; they do not change delivery behaviour.

## Message IDs

The broker generates sequential identifiers in the form
`ID:<brokerId>:<brokerInstance>:<sequence>`, unique and monotonic per broker.

## Capacity: limiting what a broker holds

By default a broker is unbounded: a producer with no consumer grows the heap
until the JVM dies. A maximum number of waiting messages can be configured when
the broker is created.

```java
// At most 1000 messages waiting across ALL queues of this broker
ConnectionFactory capped = new InMemoryConnectionFactory("app-broker", 1000);
```

The limit is **broker-wide**, not per queue: it applies to the sum of every
queue of that broker. A single queue may therefore consume the whole budget and
starve the others; there is no per-queue cap.

A send that would exceed the limit fails immediately with
`CapacityExceededException`, which derives from
`jakarta.jms.ResourceAllocationException` and is therefore catchable as
`JMSException`. It never blocks waiting for room, and the rejected message is
not queued:

```java
try {
    producer.send(session.createTextMessage("one more"));
} catch (CapacityExceededException e) {
    e.getErrorCode();    // "VMJMS-1001"
    e.getLimit();        // 1000
    e.getQueueName();    // "orders"
    e.getWaitingCount(); // messages waiting when the send was rejected
}
```

The limit is fixed when the broker is created and cannot be changed afterwards.
A default for brokers created without an explicit one can be set for the whole
JVM, which is how an existing suite gets a cap without touching the code that
builds its factories:

```
-Dvmjms.broker.maxMessages=1000
```

That property is a *default*, not a request: a factory which supplies no
maximum inherits whatever the broker has, whatever the property says. Supplying
an explicit maximum for a Broker ID that already exists with a different one is
a configuration mistake and fails at once, while the factory is built, with an
unchecked `jakarta.jms.JMSRuntimeException` carrying the error code
`VMJMS-1002`.

Error codes are namespaced `VMJMS-<number>` and grouped by kind: `VMJMS-1xxx`
for capacity and resource failures, `VMJMS-2xxx` for operations this provider
does not implement.

The exceptions of the provider and the code constants live together in
`it.baccan.vmjms.exception`:

```java
import it.baccan.vmjms.exception.CapacityExceededException;
import it.baccan.vmjms.exception.UnsupportedFeatureException;
import it.baccan.vmjms.exception.VmjmsErrorCodes;
```

Both derive from `jakarta.jms.JMSException`, so code written against a real
broker catches them without naming them.

## Message expiry

`setTimeToLive` is honoured. A message whose expiration has passed is never
delivered, and it is removed from memory automatically - also when no consumer
ever connects, which is exactly the situation a capacity limit exists for.

```java
producer.setTimeToLive(5000); // this message is deliverable for 5 seconds
```

Reclamation runs on a single daemon thread for the whole JVM, named
`vmjms-sweeper`, created only once a message with a non-zero time-to-live has
been sent. Sweeps are separated by a fixed delay of one second, which can be
changed for the whole JVM - a test suite that asserts on reclamation will want
it much shorter:

```
-Dvmjms.sweep.intervalMillis=50
```

The interval is a property of the JVM, not of a broker: there is one sweeper and
it visits every broker. It is re-read before each sweep, so it takes effect
while the system is running. A broker holding no message that can expire is not
scanned at all, and a message sent with the default time-to-live of zero never
expires.

There is **no dead-letter queue**: an expired message is discarded, not moved
elsewhere. The capacity it occupied is released, so reclaimed memory becomes
available to new sends.

## Broker lifecycle

Brokers belong to the static `InMemoryBrokerRegistry`, not to the factories or
connections that use them. Closing a connection closes its sessions, producers
and consumers, and **does not** touch the broker: messages sent before the close
stay available to a consumer created later from any connection resolving the
same Broker ID. That is what makes "send, close, reopen, receive" work.

Releasing a broker is therefore an explicit gesture:

```java
InMemoryBrokerRegistry.find("app-broker");    // lookup that does NOT create one, null if absent
InMemoryBrokerRegistry.brokerIds();           // the Broker IDs currently held
InMemoryBrokerRegistry.destroy("app-broker"); // destroy one, true if it existed
InMemoryBrokerRegistry.clear();               // destroy all of them
```

A destroyed broker is *terminated*, not merely forgotten: its messages are
discarded, its MBean is unregistered, threads blocked in `receive()` on its
queues are released with `jakarta.jms.IllegalStateException`, and any further
send or receive through it fails the same way. Without that, a connection still
holding the old broker would keep exchanging messages against it while the same
Broker ID resolved to a new one - two brokers with one name and no error
anywhere. Destroying is idempotent, and resolving the same Broker ID afterwards
creates a new, empty broker.

`clear()` also stops the reclamation thread, so a suite leaves no thread and no
MBean behind; reclamation starts again by itself for brokers created afterwards.

## Monitoring over JMX

Monitoring is off by default and can be turned on and off while a broker is
running, without recreating anything:

```java
InMemoryBroker broker = ((InMemoryConnectionFactory) factory).getBroker();
broker.enableJmx();
// ...
broker.disableJmx();
```

Both calls are idempotent. The MBean is registered in the platform MBean server
under:

```
it.baccan.vmjms:type=Broker,id="<Broker ID>"
```

The Broker ID is always quoted, because it is written by the caller and may
contain characters JMX reserves (`:`, `,`, `=`, `"`, `*`, `?`); quoting
unconditionally keeps the shape of the name independent of its content. The
unquoted identifier is available as the `BrokerId` attribute.

| Attribute | Meaning |
|---|---|
| `BrokerId` | the Broker ID as supplied |
| `MaxMessages` | configured maximum, `Integer.MAX_VALUE` when unbounded (read-only) |
| `MessagesWaiting` | messages waiting across all queues |
| `QueueCount`, `QueueDepths` | number of queues, and the depth of each |
| `EnqueuedCount` | messages accepted by send |
| `DeliveredCount` | messages handed to a consumer |
| `ExpiredCount` | messages reclaimed because they had expired |
| `PurgedCount` | messages removed by a purge |
| `RejectedCount` | sends rejected for capacity - the signal that the limit is biting |
| `SweepFailureCount`, `LastSweepError` | reclamation failures, if any |

The counters account for every message exactly once, so they reconcile with the
current depth:

```
MessagesWaiting == EnqueuedCount - DeliveredCount - ExpiredCount - PurgedCount
```

`RejectedCount` does not appear in that identity: a rejected message never
entered a queue. A purge is counted separately from an expiry on purpose, so
`ExpiredCount` stays usable as the signal that time-to-live values are too short
or consumers too slow.

Two operations are exposed, both accounting for what they remove exactly as
delivery and automatic reclamation do:

- `purgeQueue(String)` - discard every message waiting in a queue, returning how
  many were removed
- `sweepExpiredNow()` - run an expiry sweep immediately instead of waiting for
  the next one

## What is supported

| Area | Status |
|---|---|
| `ConnectionFactory`, `Connection`, `Session` | supported |
| Point-to-point queues (`Queue`) | supported, FIFO |
| `MessageProducer.send(...)` (synchronous) | supported |
| `MessageConsumer.receive()` / `receive(timeout)` / `receiveNoWait()` | supported, blocking |
| `TextMessage`, `BytesMessage`, plain `Message` | supported |
| Headers and properties (`JMSMessageID`, `JMSTimestamp`, `JMSExpiration`, ...) | carried |
| Cascading `close()` with interruption of blocked receivers | supported |
| Shared brokers across factories | supported |
| Broker-wide capacity limit with a numbered rejection | supported |
| `setTimeToLive` and automatic reclamation of expired messages | supported |
| Explicit broker lifecycle (`find`, `brokerIds`, `destroy`, `clear`) | supported |
| On-demand JMX monitoring | supported |

### Not supported

These methods exist, because the interfaces are implemented in full, but they
fail rather than pretend: they throw `UnsupportedFeatureException`, which
derives from `jakarta.jms.JMSException` and carries the error code
`VMJMS-2001`, with the missing feature in `getFeature()` and a hint in the
message:

```java
try {
    session.createTopic("prices");
} catch (UnsupportedFeatureException e) {
    e.getErrorCode(); // "VMJMS-2001"
    e.getFeature();   // "Topics"
    e.getMessage();   // "Not implemented by this in-memory provider: Topics. Use a Queue."
}
```

The exception is checked, so the compiler already forces the `catch` where
these methods are called. The only ones that cannot use it are the
`createContext` forms of the simplified API, which declare no checked
exception: they throw `jakarta.jms.JMSRuntimeException` with the same error
code.


- **Topics** and publish/subscribe: `createTopic`, durable and shared
  consumers, `unsubscribe`
- **Asynchronous delivery**: `MessageListener`, `CompletionListener`,
  `ConnectionConsumer`
- **Transactions**: `commit` / `rollback`
- **`JMSContext`** (JMS 2.0 simplified API) — use `createConnection()`
- `MapMessage`, `ObjectMessage`, `StreamMessage`
- `QueueBrowser`, temporary queues and topics

Message selectors are accepted by `createConsumer(...)` but ignored: every
consumer receives the next message in the queue.

`JMSPriority` and `deliveryDelay` are accepted and carried, but they do not
change delivery: it stays FIFO, and a delayed message is deliverable at once.
Unlike expiry, neither has any bearing on how much memory a broker holds, which
is what the capacity accounting is about.

Consumers are synchronous and blocking by design: `receive()` waits on the
underlying `BlockingQueue`, and closing a consumer or session interrupts the
waiting thread.

> **Jakarta Messaging TCK:** `vmjms` is *not* a certified JMS provider and does
> not aim to be. The TCK exercises topics, `JMSContext`, selectors and
> `QueueConnectionFactory`, none of which are implemented here.

## Build and test

```bash
mvn clean verify
```

The project has no dependencies beyond the Jakarta JMS API (plus JUnit 5 for
the test suite). Every push and pull request is built on JDK 17, 21 and 25 by the
[`build`](.github/workflows/build.yml) workflow.

### Source headers

Every Java file, main and test alike, starts with the same four-line header -
the product, the repository URL, the copyright and the SPDX licence identifier -
whose text lives in [`etc/header.txt`](etc/header.txt). `verify` fails and names
the file when one is missing or when its header differs from the template, so a
new file is caught before the pull request rather than after. To write it into
the files that lack it:

```bash
mvn license:format
```

Running it on a file that already carries the header leaves it untouched.

### Releasing

Pushing a `v*` tag runs the [`release`](.github/workflows/release.yml)
workflow, which sets the POM version from the tag, builds the main, sources and
javadoc jars, signs them, publishes them to Maven Central through the Central
Portal and attaches them to the GitHub Release:

```bash
git tag v1.1.0 && git push origin v1.1.0
```

The publishing plugin runs with `autoPublish` off, so a tag uploads and
validates the deployment and then waits for the maintainer to publish it from
the Portal. The prerequisites that live outside the build — the verified
namespace, the signing key, the repository secrets — the local dry run and the
troubleshooting are in [docs/release.md](docs/release.md).

## License

Released under the [MIT License](LICENSE).
