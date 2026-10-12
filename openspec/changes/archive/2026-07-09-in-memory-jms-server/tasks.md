## 1. Project Setup

- [x] 1.1 Create `pom.xml` with standard Maven layout, Java 17 configuration, and `jakarta.jms:jakarta.jms-api:3.1.0` dependency.
- [x] 1.2 Establish the base directories for the package `it.baccan.vmjms`.

## 2. Core Broker State

- [x] 2.1 Implement `InMemoryBroker` to store queues in a `ConcurrentHashMap` with `LinkedBlockingQueue` values and generate progressive Hex-timestamp IDs.
- [x] 2.2 Implement `InMemoryQueue` implementing `jakarta.jms.Queue`.

## 3. Messages Implementation

- [x] 3.1 Implement base class `InMemoryMessage` implementing `jakarta.jms.Message` with support for headers and property storage.
- [x] 3.2 Implement `InMemoryTextMessage` implementing `jakarta.jms.TextMessage`.
- [x] 3.3 Implement `InMemoryBytesMessage` implementing `jakarta.jms.BytesMessage` with write-only/read-only mode transitions.

## 4. Connection & Session Resources

- [x] 4.1 Implement `InMemoryConnectionFactory` implementing `jakarta.jms.ConnectionFactory` holding its private `InMemoryBroker` instance.
- [x] 4.2 Implement `InMemoryConnection` implementing `jakarta.jms.Connection` managing the list of sessions.
- [x] 4.3 Implement `InMemorySession` implementing `jakarta.jms.Session`.

## 5. Producers & Consumers

- [x] 5.1 Implement `InMemoryMessageProducer` implementing `jakarta.jms.MessageProducer` which assigns IDs/timestamps and appends messages to the broker.
- [x] 5.2 Implement `InMemoryMessageConsumer` implementing `jakarta.jms.MessageConsumer` with synchronous `receive()`, `receive(timeout)`, and `receiveNoWait()`.
- [x] 5.3 Implement the robust `close()` logic in `InMemoryMessageConsumer` that interrupts the active waiting thread and throws `IllegalStateException`.
- [x] 5.4 Implement cascading `close()` on Connection and Session to clean up all open child resources.

## 6. Verification and Unit Tests

- [x] 6.1 Implement unit tests verifying isolated factories, FIFO order, hex-timestamp sequence ID generation, TextMessage & BytesMessage serialization, timeout blocking, and cascading close behaviors.
