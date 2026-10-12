## MODIFIED Requirements

### Requirement: In-Memory Broker Isolation
The system SHALL ensure that by default, each instance of `InMemoryConnectionFactory` creates and owns a completely isolated, private instance of `InMemoryBroker` in RAM. The Broker ID generated for a factory built without an explicit identifier MUST be unique within the JVM, so that isolation does not depend on clock granularity. The system MUST also support sharing of `InMemoryBroker` instances across separate `InMemoryConnectionFactory` instances using a shared string identifier (Broker ID), which is used verbatim.

#### Scenario: Isolated factories
- **WHEN** a client instantiates two separate `InMemoryConnectionFactory` instances without a Broker ID and creates connections/sessions from both
- **THEN** queues created and messages sent on one factory SHALL NOT be visible or accessible to sessions/consumers created from the other factory

#### Scenario: Anonymous factories created within the same millisecond
- **WHEN** a client instantiates two `InMemoryConnectionFactory` instances without a Broker ID in immediate succession, within the same clock millisecond
- **THEN** the two factories SHALL resolve to different `InMemoryBroker` instances

#### Scenario: Shared factories with matching Broker ID
- **WHEN** a client instantiates two separate `InMemoryConnectionFactory` instances with the same Broker ID, and sends a message to queue "sharedQueue" from a connection of the first factory
- **THEN** a consumer from a connection of the second factory SHALL be able to receive that message from "sharedQueue"
