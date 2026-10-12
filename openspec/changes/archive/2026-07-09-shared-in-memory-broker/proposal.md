## Why

Currently, each `InMemoryConnectionFactory` instance owns an isolated, private `InMemoryBroker` state. This prevents separate factories or client instances from communicating with each other over the same in-memory queues. To support modular architectures and integration tests that instantiate separate connection factories but expect to share the same message queues, we need a mechanism to share broker instances in RAM using a shared identifier.

## What Changes

- Introduce a static `InMemoryBrokerRegistry` to hold and coordinate shared `InMemoryBroker` instances by a string identifier.
- Add a new constructor to `InMemoryConnectionFactory` accepting a `String brokerId` to resolve to the shared broker.
- Maintain backwards compatibility by defaulting to a unique broker ID if none is provided.
- Ensure that connection factories sharing the same broker ID share the same underlying queue storage.
- Support parsing of queue names containing query parameters (e.g., `queueName?param1=val1&param2=val2`), separating the base name from a key-value map of parameters.

## Capabilities

### New Capabilities

*None.*

### Modified Capabilities

- `in-memory-jms`: Extend the capability to support broker sharing across factories based on a shared broker ID.

## Impact

- **API Extensions**: Adds new constructors to `InMemoryConnectionFactory` (`InMemoryConnectionFactory(String brokerId)`).
- **Core State**: Introduces `InMemoryBrokerRegistry` as a static repository of shared brokers in RAM.
- **Resource Management**: Ensures that all connections created from factories sharing a broker ID share the same queues and message counters.
- **Queue Metadata**: `InMemoryQueue` class parses query parameters from the queue name and exposes them via a Map getter.
