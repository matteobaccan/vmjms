## Context

For testing and local development, applications using standard JMS (specifically `jakarta.jms`) often require an external broker. To facilitate lighter and faster unit/integration testing, this project implements a minimal, in-memory JMS broker that runs entirely in RAM using Java 17 and adheres to the `jakarta.jms` API interfaces.

## Goals / Non-Goals

**Goals:**
- Implement `jakarta.jms` interfaces: `ConnectionFactory`, `Connection`, `Session`, `MessageProducer`, `MessageConsumer`, `Message`, `TextMessage`, and `BytesMessage`.
- Provide thread-safe, synchronous FIFO queueing inside an isolated in-memory broker instance per `ConnectionFactory`.
- Automatically generate unique, sequential message IDs based on broker creation timestamp and a progressive atomic counter.
- Support robust resource cleanup by propagating `close()` downwards, causing blocked consumer threads to immediately throw a `jakarta.jms.IllegalStateException`.

**Non-Goals:**
- No support for asynchronous message consumption (`MessageListener`).
- No support for local transactions or XA transactions.
- No support for other message types (`MapMessage`, `ObjectMessage`, `StreamMessage`).
- No queue persistence or message durability across JVM restarts.
- No compliance check for Connection start/stop states (messages are consumed instantly regardless of connection state).

## Decisions

### Decision 1: jakarta.jms API Compliance
- **Choice**: Implement standard `jakarta.jms` interfaces, throwing `UnsupportedOperationException` on all unsupported operations (e.g., transaction commit/rollback, JNDI lookup, asynchronous listeners).
- **Rationale**: Enables drop-in compatibility with codebases requiring standard `jakarta.jms` types.
- **Alternatives Considered**: Custom lightweight classes matching JMS names. Rejected because client code could not cast them to `jakarta.jms` types.

### Decision 2: In-Memory Storage via Concurrent Collections
- **Choice**: Map queues to `LinkedBlockingQueue` inside a `ConcurrentHashMap` in the `InMemoryBroker`.
- **Rationale**: `LinkedBlockingQueue` provides thread-safe, lock-free concurrency for producers inserting and consumers fetching, with native blocking support.
- **Alternatives Considered**: Custom synced collections. Rejected due to complexity and risk of deadlocks.

### Decision 3: Unblocking Waiting Consumers via Thread Interruption
- **Choice**: Register the active consumer thread in an `AtomicReference<Thread>` on `receive()`. On `close()`, if a thread is registered, interrupt it. In the `receive()` catch block, check if closed and throw `jakarta.jms.IllegalStateException`.
- **Rationale**: Avoids polling loops (e.g. `poll(100ms)`) and wakes up the blocked thread instantly.
- **Alternatives Considered**: Periodically polling the queue. Rejected due to latency and CPU overhead.

### Decision 4: Hex-Timestamp and Progressive Sequence for Message IDs
- **Choice**: Broker instances generate IDs with the format `ID:<hex-millis>:<sequence-long>`.
- **Rationale**: The hex timestamp represents the broker's instantiation time, and the atomic sequence tracks messages within the broker. This avoids conflicts across sequentially/concurrently run tests while remaining short and readable.
- **Alternatives Considered**: UUID-based IDs. Rejected because UUID strings are verbose and harder to track in test assertions.

## Risks / Trade-offs

- **Risk**: Client libraries/frameworks (like Spring JMS or Camel) calling unsupported methods (such as connection metadata queries or transaction setup) will fail with `UnsupportedOperationException`.
  - **Mitigation**: Implement common fallback methods to return reasonable defaults (e.g. return non-transacted session values, mock ConnectionMetadata) where possible.
- **Risk**: Thread interruption during `close()` could clash with standard thread pool interrupts.
  - **Mitigation**: Verify the volatile `closed` flag before acting on `InterruptedException` to ensure we only throw `IllegalStateException` when the close was explicitly initiated.
