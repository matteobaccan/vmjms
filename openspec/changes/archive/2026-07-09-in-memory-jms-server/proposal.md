## Why

Testing and local development of JMS-based Java applications often requires a heavy external message broker (e.g., ActiveMQ, RabbitMQ). There is a need for a lightweight, fast, in-memory JMS server that runs entirely in RAM, conforms to the Jakarta JMS API, maintains test isolation by providing private broker instances per connection factory, and supports synchronous FIFO message producer and consumer operations with robust thread interruption on close.

## What Changes

- Introduce a new in-memory JMS provider implementing the `jakarta.jms` specification.
- Create class implementations for `ConnectionFactory`, `Connection`, `Session`, `MessageProducer`, `MessageConsumer`, `Queue`, `Message`, `TextMessage`, and `BytesMessage`.
- Implement a thread-safe, in-memory broker state using concurrent collections (`ConcurrentHashMap`, `LinkedBlockingQueue`).
- Support automatic message ID generation using a hexadecimal timestamp combined with an atomic sequential counter, ensuring uniqueness and order.
- Implement robust cleanup and closure behavior, propagating `close()` from Connections down to Sessions and Consumers, interrupting any threads waiting on synchronous reads.

## Capabilities

### New Capabilities

- `in-memory-jms`: Conform to Jakarta JMS 3.x API for in-memory messaging, providing isolated, thread-safe, and synchronous FIFO queueing.

### Modified Capabilities

*None.*

## Impact

- **New Dependency**: Adds `jakarta.jms:jakarta.jms-api` (version `3.1.0`) to the Maven project.
- **Java Class Hierarchy**: Introduces new classes under the `it.baccan.vmjms` package.
- **API Surface**: Client applications can instantiate `InMemoryConnectionFactory` and use standard `jakarta.jms` interfaces without external infrastructure dependencies.
