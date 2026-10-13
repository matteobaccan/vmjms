## ADDED Requirements

### Requirement: Broker-Wide Message Capacity Limit
The system SHALL support configuring a maximum number of messages waiting in a single `InMemoryBroker`. The limit SHALL apply to the sum of the messages waiting across every queue of that broker, not to any individual queue. A message counts against the limit from the moment it is accepted by `send` until the moment it is removed from its queue, whether by delivery to a consumer, by expiry reclamation or by an administrative purge. The limit SHALL be fixed when the broker is created and SHALL NOT be modifiable afterwards. A broker created without a limit SHALL be effectively unbounded, preserving the current behaviour.

The limit MAY be supplied through `InMemoryConnectionFactory`, and the system property `vmjms.broker.maxMessages` SHALL provide the default for factories that supply none. That property SHALL be consulted only when a broker is created, and SHALL act as a default and not as a requested value: a factory that supplies no explicit maximum SHALL never be refused for a conflicting limit, whatever the property is set to.

#### Scenario: Limit spans all queues of the broker
- **WHEN** a broker is configured with a maximum of 10 messages and a producer sends 6 messages to queue "a" and 4 messages to queue "b"
- **THEN** the broker SHALL hold 10 waiting messages and a further send to either queue SHALL be rejected

#### Scenario: Consuming a message frees capacity
- **WHEN** a broker configured with a maximum of 1 message is full, and a consumer receives the waiting message
- **THEN** a subsequent send SHALL succeed

#### Scenario: Unconfigured broker is unbounded
- **WHEN** a broker is created without specifying a maximum and no default is configured
- **THEN** sends SHALL NOT be rejected for capacity reasons

#### Scenario: Limit is not shared between brokers
- **WHEN** two isolated brokers are each configured with a maximum of 5 messages and each receives 5 messages
- **THEN** both SHALL have accepted all 5 of their messages

#### Scenario: Default maximum taken from the system property
- **WHEN** `vmjms.broker.maxMessages` is set to 3 and a factory that supplies no explicit maximum creates a broker
- **THEN** that broker SHALL accept 3 waiting messages and SHALL reject the fourth

#### Scenario: Conflicting limits for a shared broker are refused
- **WHEN** a factory resolves an existing shared Broker ID and supplies an explicit maximum that differs from the one the broker was created with
- **THEN** the factory SHALL fail rather than silently retain the existing limit

#### Scenario: A factory with no explicit maximum never conflicts
- **WHEN** a factory resolves an existing shared Broker ID without supplying a maximum, while `vmjms.broker.maxMessages` is set to a value that differs from the limit of that broker
- **THEN** the factory SHALL succeed and the limit of the broker SHALL be unchanged

### Requirement: Numbered Capacity Exception on Send
When a `send` would exceed the configured maximum of the broker, the system SHALL reject it immediately by throwing a dedicated exception deriving from `jakarta.jms.ResourceAllocationException`. The send SHALL NOT block, wait for capacity, or discard the message silently. The exception SHALL carry a stable numeric error code through `JMSException.getErrorCode()`, and SHALL expose the configured limit, the queue the rejected send targeted and the number of waiting messages observed at rejection, both as accessors and in its message text. A rejected message SHALL NOT be enqueued and SHALL NOT consume capacity.

Error codes SHALL be namespaced as `VMJMS-<number>` and grouped by kind, with `VMJMS-1xxx` reserved for capacity and resource failures. `VMJMS-1001` SHALL be the code for exceeding the maximum of the broker on send. `VMJMS-1002` SHALL be the code for a factory supplying, for an existing shared Broker ID, an explicit maximum that differs from the one of the broker; that failure SHALL be reported as an unchecked `jakarta.jms.JMSRuntimeException` when the factory is constructed, before any traffic, and SHALL name the Broker ID together with both the existing and the requested limit.

#### Scenario: Send to a full broker fails immediately
- **WHEN** a producer sends a message to a broker that is already holding its configured maximum
- **THEN** `send` SHALL throw the capacity exception without blocking, and the message SHALL NOT appear in any queue

#### Scenario: Exception reports the limit and the queue
- **WHEN** a send is rejected on a broker configured with a maximum of 50 while targeting queue "orders"
- **THEN** the thrown exception SHALL report the limit `50` and the queue name "orders", and its message text SHALL state that the maximum number of queued messages was exceeded

#### Scenario: Exception carries a stable error code
- **WHEN** a send is rejected for capacity
- **THEN** `getErrorCode()` SHALL return `VMJMS-1001`, and that code SHALL be the same for every such rejection

#### Scenario: Rejection is catchable as a JMS exception
- **WHEN** a caller catches `jakarta.jms.JMSException` around a send that is rejected for capacity
- **THEN** the rejection SHALL be caught by that handler

#### Scenario: Conflicting limit fails at wiring time with its own code
- **WHEN** a factory is constructed with an explicit maximum that conflicts with the limit of the shared broker it resolves
- **THEN** the constructor SHALL throw an unchecked `jakarta.jms.JMSRuntimeException` whose error code is `VMJMS-1002` and whose text names the Broker ID, the existing limit and the requested one

### Requirement: Expired Message Reclamation
The system SHALL honour the `JMSExpiration` header it assigns on send. A message whose expiration time has passed SHALL NOT be delivered to a consumer, and SHALL be removed from memory automatically without requiring a consumer, a send, or any other traffic to trigger the removal. Removing an expired message SHALL release the capacity it occupied. Expired messages SHALL NOT be retained anywhere, in particular not in a dead-letter queue. Reclamation SHALL NOT suspend producers or consumers for the duration of a scan, and a broker holding no message that can expire SHALL NOT be scanned at all. A message sent with a time-to-live of zero never expires and SHALL NOT be reclaimed.

Reclamation SHALL run on a single shared daemon thread for the whole JVM, created lazily on the first enqueue of a message that can expire. Successive sweeps SHALL be separated by a fixed delay, one second by default, so that the time taken by one sweep is never charged to the next. That delay SHALL be configurable for the whole JVM through the system property `vmjms.sweep.intervalMillis`, and SHALL be read again before each sweep so that a change to it takes effect while the system is running; it SHALL NOT be a property of an individual broker. A sweep that fails SHALL NOT prevent later sweeps from running, and a failure encountered on one broker SHALL NOT prevent the remaining brokers from being swept in the same pass. Reclamation SHALL work again for brokers created after `InMemoryBrokerRegistry.clear()`.

#### Scenario: Expired message is not delivered
- **WHEN** a message is sent with a time-to-live of 100 ms and a consumer receives from that queue after 300 ms
- **THEN** the consumer SHALL NOT receive the expired message, and SHALL receive the next live message or `null` on timeout

#### Scenario: Memory is reclaimed without any traffic
- **WHEN** messages are sent with a short time-to-live and no consumer ever connects
- **THEN** the messages SHALL be removed from their queue after their expiration time has passed

#### Scenario: Reclamation frees capacity
- **WHEN** a broker configured with a maximum of 1 message holds one message that has expired and been reclaimed
- **THEN** a subsequent send SHALL succeed

#### Scenario: Messages without a time-to-live are retained
- **WHEN** a message is sent with the default time-to-live of zero and remains unconsumed
- **THEN** it SHALL remain available to a consumer indefinitely

#### Scenario: Capacity accounting survives concurrent removal
- **WHEN** a consumer and the reclamation sweep act on the same expired message concurrently
- **THEN** the capacity occupied by that message SHALL be released exactly once

#### Scenario: Sweep interval is JVM-wide and configurable
- **WHEN** `vmjms.sweep.intervalMillis` is set to a value shorter than the default and messages with a short time-to-live are sent to two different brokers
- **THEN** the expired messages of both brokers SHALL be reclaimed at that interval, and no per-broker interval SHALL be required or honoured

#### Scenario: A failing sweep does not stop later sweeps
- **WHEN** one pass of the reclamation sweep fails with an unexpected error
- **THEN** later sweeps SHALL still run, and the expired messages of the other brokers SHALL still be reclaimed in the pass that failed

#### Scenario: Reclamation works again after the registry is cleared
- **WHEN** messages with a time-to-live have been reclaimed, `InMemoryBrokerRegistry.clear()` is called, and a new broker then receives a message with a time-to-live
- **THEN** that message SHALL be reclaimed as before, without the JVM being restarted

#### Scenario: Reclamation leaves no thread behind
- **WHEN** messages with a time-to-live have been sent and `InMemoryBrokerRegistry.clear()` is then called
- **THEN** no non-daemon thread SHALL remain and the JVM SHALL be able to exit

### Requirement: Explicit Broker Lifecycle Management
The system SHALL provide explicit management of the brokers held by `InMemoryBrokerRegistry`: resolving a Broker ID **without** creating a broker for it, listing the Broker IDs currently held, destroying one broker, and destroying all of them.

Destroying a broker SHALL remove it from the registry, mark it terminated, discard the messages waiting in its queues, and release the resources it holds. Any thread blocked in `receive()` on one of its queues SHALL be unblocked with a `jakarta.jms.IllegalStateException`, as it is when its consumer is closed. Any subsequent send or receive through a terminated broker SHALL fail with `jakarta.jms.IllegalStateException`, so that a connection still holding a reference to a destroyed broker cannot keep exchanging messages invisibly against a broker the registry no longer resolves. Destroying SHALL be idempotent, and destroying an unknown Broker ID SHALL be reported as such rather than failing. Resolving a destroyed Broker ID again SHALL create a new, empty broker with no memory of the previous one. `InMemoryBrokerRegistry.clear()` SHALL be equivalent to destroying every broker it holds.

#### Scenario: A destroyed broker can no longer be used
- **WHEN** a broker holding messages is destroyed and a producer created before the destruction sends to it
- **THEN** the send SHALL fail with `jakarta.jms.IllegalStateException` and no message SHALL be queued

#### Scenario: Destroying unblocks a waiting consumer
- **WHEN** a thread is blocked in `receive()` on a queue of a broker and that broker is destroyed
- **THEN** the blocked thread SHALL return immediately with a `jakarta.jms.IllegalStateException`

#### Scenario: A destroyed Broker ID is reusable
- **WHEN** a broker holding messages is destroyed and a new factory then resolves the same Broker ID
- **THEN** a new empty broker SHALL be created and SHALL NOT expose the messages of the destroyed one

#### Scenario: Destroying is idempotent
- **WHEN** the same Broker ID is destroyed twice, and an unknown Broker ID is then destroyed
- **THEN** no call SHALL fail, and each call SHALL report whether a broker was actually removed

#### Scenario: Resolving without creating
- **WHEN** an unknown Broker ID is looked up through the non-creating lookup
- **THEN** the lookup SHALL report that no such broker exists, SHALL NOT create one, and that Broker ID SHALL NOT appear in the list of Broker IDs held

#### Scenario: Listing the brokers held
- **WHEN** three brokers are created and one of them is destroyed
- **THEN** the list of Broker IDs held SHALL contain exactly the two remaining ones

#### Scenario: Clearing the registry destroys every broker
- **WHEN** several brokers hold messages and `InMemoryBrokerRegistry.clear()` is called
- **THEN** every one of them SHALL be terminated, and a send through any of them SHALL fail with `jakarta.jms.IllegalStateException`

## MODIFIED Requirements

### Requirement: Thread-Safe FIFO Queueing
The system SHALL store messages in RAM using a thread-safe Queue structure (`LinkedBlockingQueue`) that guarantees strict First-In, First-Out (FIFO) delivery of messages. Enqueueing and dequeueing SHALL be mediated by the `InMemoryBroker`, so that the broker accounts for every message that enters or leaves a queue; producers and consumers SHALL NOT bypass that accounting. Messages whose expiration has passed are skipped rather than delivered, which SHALL NOT disturb the relative order of the remaining messages.

#### Scenario: FIFO Order of delivery
- **WHEN** two messages (Message A then Message B) are sent to the same queue in sequence
- **THEN** a consumer receiving from that queue SHALL receive Message A first, followed by Message B

#### Scenario: FIFO order is preserved across skipped messages
- **WHEN** messages A, B and C are sent to the same queue in sequence and B has expired by the time they are consumed
- **THEN** the consumer SHALL receive A followed by C

### Requirement: Robust Cascading Close with Exception
The system SHALL support cascading resource cleanup: closing a `Connection` MUST close all its associated `Session`s, which in turn MUST close all their associated `MessageConsumer`s and `MessageProducer`s. If a consumer is closed while a thread is blocked on `receive()`, the blocked thread MUST immediately unblock and throw a `jakarta.jms.IllegalStateException`.

Cascading close SHALL affect only the objects it closes. It SHALL NOT destroy the broker, discard its queues, or discard the messages waiting in them: the broker belongs to `InMemoryBrokerRegistry` and MAY be shared by other factories and connections. Messages sent before a connection is closed SHALL remain available to a consumer created later from any connection resolving the same Broker ID. Destroying a broker SHALL be an explicit operation, distinct from closing a connection.

#### Scenario: Active consumer receive throws exception on close
- **WHEN** thread A calls `receive()` on an empty queue and blocks, and thread B calls `close()` on the connection or consumer
- **THEN** thread A immediately returns with a `jakarta.jms.IllegalStateException` indicating the consumer is closed

#### Scenario: Closing a connection leaves queued messages intact
- **WHEN** a producer sends a message and its connection is then closed, and a new connection is created from a factory resolving the same Broker ID
- **THEN** a consumer on the new connection SHALL receive that message
