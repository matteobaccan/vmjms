## ADDED Requirements

### Requirement: In-Memory Broker Isolation
The system SHALL ensure that each instance of `InMemoryConnectionFactory` creates and owns a completely isolated, private instance of `InMemoryBroker` in RAM.

#### Scenario: Isolated factories
- **WHEN** a client instantiates two separate `InMemoryConnectionFactory` instances and creates connections/sessions from both
- **THEN** queues created and messages sent on one factory SHALL NOT be visible or accessible to sessions/consumers created from the other factory

### Requirement: Thread-Safe FIFO Queueing
The system SHALL store messages in RAM using a thread-safe Queue structure (`LinkedBlockingQueue`) that guarantees strict First-In, First-Out (FIFO) delivery of messages.

#### Scenario: FIFO Order of delivery
- **WHEN** two messages (Message A then Message B) are sent to the same queue in sequence
- **THEN** a consumer receiving from that queue SHALL receive Message A first, followed by Message B

### Requirement: Hex-Timestamp Progressive Message ID Generation
The system SHALL automatically assign a unique `JMSMessageID` to every sent message. The ID structure MUST follow the format `ID:<broker-creation-timestamp-hex>:<atomic-sequence-number>`.

#### Scenario: Unique ordered message ID
- **WHEN** a message is sent via a producer
- **THEN** the message's `JMSMessageID` header is populated with a string prefix containing the broker's creation timestamp in hexadecimal and a sequentially incremented integer sequence number (starting from 1)

### Requirement: TextMessage and BytesMessage Support
The system SHALL support both `jakarta.jms.TextMessage` (for string payloads) and `jakarta.jms.BytesMessage` (for binary payloads) with standard state transition logic (read-only and write-only modes for BytesMessage).

#### Scenario: Send and read text message
- **WHEN** a `TextMessage` is created with string content "hello", sent to a queue, and received
- **THEN** the received message's `getText()` method returns "hello"

#### Scenario: Send and read bytes message
- **WHEN** a `BytesMessage` is created, written with bytes `[0x01, 0x02]`, reset to read-only, sent to a queue, and received
- **THEN** the received message's `readBytes(...)` method successfully reads `[0x01, 0x02]`

### Requirement: Synchronous Consumption with Timeout
The system SHALL support synchronous message consumption using `receive()`, `receive(timeout)`, and `receiveNoWait()`, blocking the calling thread until a message is available or the timeout expires.

#### Scenario: Receive with timeout returning null
- **WHEN** a consumer calls `receive(500)` on an empty queue
- **THEN** it blocks for 500 milliseconds and then returns `null`

### Requirement: Robust Cascading Close with Exception
The system SHALL support cascading resource cleanup: closing a `Connection` MUST close all its associated `Session`s, which in turn MUST close all their associated `MessageConsumer`s and `MessageProducer`s. If a consumer is closed while a thread is blocked on `receive()`, the blocked thread MUST immediately unblock and throw a `jakarta.jms.IllegalStateException`.

#### Scenario: Active consumer receive throws exception on close
- **WHEN** thread A calls `receive()` on an empty queue and blocks, and thread B calls `close()` on the connection or consumer
- **THEN** thread A immediately returns with a `jakarta.jms.IllegalStateException` indicating the consumer is closed
