## MODIFIED Requirements

### Requirement: In-Memory Broker Isolation
The system SHALL ensure that by default, each instance of `InMemoryConnectionFactory` creates and owns a completely isolated, private instance of `InMemoryBroker` in RAM. However, the system MUST also support sharing of `InMemoryBroker` instances across separate `InMemoryConnectionFactory` instances using a shared string identifier (Broker ID).

#### Scenario: Isolated factories
- **WHEN** a client instantiates two separate `InMemoryConnectionFactory` instances without a Broker ID and creates connections/sessions from both
- **THEN** queues created and messages sent on one factory SHALL NOT be visible or accessible to sessions/consumers created from the other factory

#### Scenario: Shared factories with matching Broker ID
- **WHEN** a client instantiates two separate `InMemoryConnectionFactory` instances with the same Broker ID, and sends a message to queue "sharedQueue" from a connection of the first factory
- **THEN** a consumer from a connection of the second factory SHALL be able to receive that message from "sharedQueue"

## ADDED Requirements

### Requirement: Queue Name Parameter Parsing
The system SHALL parse queue names containing URI-like query parameters (e.g. `baseQueueName?param1=value1&param2=value2`). The system MUST separate the base queue name from its query parameters, storing the parameters in a key-value map.

#### Scenario: Queue name with query parameters
- **WHEN** a session creates a queue with name "myQueue?delay=10&retry=true"
- **THEN** the queue's `getQueueName()` returns "myQueue", and the queue's parameters map contains the keys "delay" with value "10" and "retry" with value "true"

