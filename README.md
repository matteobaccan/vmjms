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

Releases are published to GitHub Packages:

```xml
<dependency>
    <groupId>it.baccan</groupId>
    <artifactId>vmjms</artifactId>
    <version>1.0.0</version>
</dependency>
```

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/matteobaccan/vmjms</url>
    </repository>
</repositories>
```

GitHub Packages requires authentication even for public artifacts: add a
`<server>` entry with id `github` to your `~/.m2/settings.xml`, using a personal
access token with the `read:packages` scope. The jars of every release are also
attached to the [GitHub Release](https://github.com/matteobaccan/vmjms/releases)
if you prefer to download them directly.

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

Brokers are resolved through the static `InMemoryBrokerRegistry`. In test
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

## What is supported

| Area | Status |
|---|---|
| `ConnectionFactory`, `Connection`, `Session` | supported |
| Point-to-point queues (`Queue`) | supported, FIFO |
| `MessageProducer.send(...)` (synchronous) | supported |
| `MessageConsumer.receive()` / `receive(timeout)` / `receiveNoWait()` | supported, blocking |
| `TextMessage`, `BytesMessage`, plain `Message` | supported |
| Headers and properties (`JMSMessageID`, `JMSTimestamp`, `JMSPriority`, ...) | supported |
| Cascading `close()` with interruption of blocked receivers | supported |
| Shared brokers across factories | supported |

### Not supported

These methods exist (the interfaces are fully implemented) but throw
`UnsupportedOperationException`:

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
the test suite). Every push and pull request is built on JDK 17 and 21 by the
[`build`](.github/workflows/build.yml) workflow.

### Releasing

Pushing a `v*` tag runs the [`release`](.github/workflows/release.yml)
workflow, which sets the POM version from the tag, builds the main, sources and
javadoc jars, publishes them to GitHub Packages and attaches them to the
GitHub Release:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## License

Released under the [MIT License](LICENSE).
