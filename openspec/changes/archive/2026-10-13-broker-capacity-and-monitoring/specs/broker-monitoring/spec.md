## ADDED Requirements

### Requirement: On-Demand JMX Monitoring
The system SHALL allow JMX monitoring of an `InMemoryBroker` to be enabled and disabled while the broker is running, without recreating the broker, its connections, its sessions or its queues. Enabling SHALL register one MBean for the broker in the platform MBean server, so that two brokers never collide. Both enabling and disabling SHALL be idempotent: repeating either SHALL have no further effect and SHALL NOT fail. A broker SHALL NOT be monitored unless monitoring has been enabled for it.

The object name SHALL be `it.baccan.vmjms:type=Broker,id=<quoted Broker ID>`, where the Broker ID is always passed through `javax.management.ObjectName.quote`. Quoting SHALL be unconditional, so that the form of the object name never depends on the content of the Broker ID, and a Broker ID containing characters reserved by JMX (`:`, `,`, `=`, `"`, `*`, `?`) registers successfully instead of failing with `MalformedObjectNameException`. The Broker ID SHALL remain available unquoted as an attribute of the MBean.

Destroying a broker SHALL unregister its MBean, and `InMemoryBrokerRegistry.clear()` SHALL unregister the MBeans of every broker it holds, so that repeated use leaves none behind.

#### Scenario: Monitoring is off by default
- **WHEN** a broker is created and no monitoring is requested
- **THEN** no MBean for that broker SHALL be registered in the platform MBean server

#### Scenario: Enabling while the broker is in use
- **WHEN** monitoring is enabled on a broker that already holds messages and has open connections
- **THEN** the MBean SHALL become available and SHALL report the messages already waiting, and existing connections, sessions, producers and consumers SHALL continue to work

#### Scenario: Disabling while the broker is in use
- **WHEN** monitoring is disabled on a broker that is sending and receiving messages
- **THEN** the MBean SHALL be unregistered, and sends and receives SHALL continue to succeed

#### Scenario: Re-enabling after disabling
- **WHEN** monitoring is enabled, disabled and enabled again on the same broker
- **THEN** the MBean SHALL be registered again and SHALL report the current state of the broker

#### Scenario: Enabling twice does not fail
- **WHEN** monitoring is enabled on a broker for which it is already enabled
- **THEN** the call SHALL succeed and exactly one MBean SHALL remain registered

#### Scenario: Two brokers are monitored independently
- **WHEN** monitoring is enabled on two brokers with different Broker IDs
- **THEN** each SHALL be registered under its own object name and SHALL report only its own queues

#### Scenario: Broker ID containing characters reserved by JMX
- **WHEN** monitoring is enabled on a broker whose Broker ID is `orders:test,v=1`
- **THEN** enabling SHALL succeed, the MBean SHALL be registered and reachable under its quoted object name, and no `MalformedObjectNameException` SHALL be raised

#### Scenario: Object name form does not depend on the Broker ID
- **WHEN** monitoring is enabled on a broker whose Broker ID contains no reserved character
- **THEN** its object name SHALL use the same quoted form as any other Broker ID

#### Scenario: Destroying a broker unregisters its MBean
- **WHEN** monitoring is enabled on a broker and that broker is then destroyed
- **THEN** its MBean SHALL no longer be registered in the platform MBean server

#### Scenario: Monitoring leaves nothing registered after cleanup
- **WHEN** monitoring has been enabled on several brokers and `InMemoryBrokerRegistry.clear()` is called
- **THEN** no MBean registered by the system SHALL remain in the platform MBean server

### Requirement: Broker Metrics Exposed Over JMX
While monitoring is enabled, the MBean SHALL expose, as read-only attributes, the Broker ID as it was supplied, the configured maximum number of messages, the number of messages currently waiting across all queues, the number of queues, the depth of each queue, and lifetime counters for messages enqueued, delivered to consumers, reclaimed as expired, removed by an administrative purge and rejected for exceeding the capacity limit. The configured maximum SHALL NOT be writable.

The counters SHALL account for every message exactly once, so that the number of messages currently waiting equals the number enqueued minus those delivered, reclaimed as expired and purged. Messages rejected for capacity SHALL NOT be counted as enqueued, since they never entered a queue. A purged message SHALL be counted as purged and SHALL NOT be counted as expired, so that the expired counter remains usable as the signal that time-to-live values are too short or consumers too slow.

The MBean SHALL also expose the number of reclamation sweeps that have failed for the broker and a description of the most recent failure, so that a reclamation problem is observable without a logging framework.

The MBean SHALL expose operations to purge a queue and to trigger an expiry sweep; these SHALL account for the messages they remove exactly as delivery and automatic reclamation do.

#### Scenario: Current depth reflects traffic
- **WHEN** three messages are sent to queue "orders" and one is received, with monitoring enabled
- **THEN** the MBean SHALL report two messages waiting in total and a depth of two for "orders"

#### Scenario: Rejections are counted
- **WHEN** two sends are rejected because the broker is at its configured maximum
- **THEN** the rejected counter SHALL report two, and the enqueued counter SHALL NOT include the rejected messages

#### Scenario: Reclaimed messages are counted
- **WHEN** messages expire and are reclaimed
- **THEN** the expired counter SHALL report the number reclaimed, and those messages SHALL NOT be counted as delivered

#### Scenario: Purged messages are counted as purged
- **WHEN** a queue holding three live messages is purged through the MBean
- **THEN** the purged counter SHALL report three, and the expired and delivered counters SHALL be unchanged

#### Scenario: Counters reconcile with the current depth
- **WHEN** a broker has accepted messages, delivered some, had some reclaimed as expired, had some purged and rejected further sends for capacity
- **THEN** the number of messages reported as waiting SHALL equal enqueued minus delivered minus expired minus purged, and the rejected count SHALL NOT appear in that reconciliation

#### Scenario: The configured limit is read-only
- **WHEN** a JMX client attempts to write the maximum-messages attribute
- **THEN** the attempt SHALL fail and the limit of the broker SHALL be unchanged

#### Scenario: Broker ID is reported as supplied
- **WHEN** the Broker ID attribute is read on a broker whose Broker ID contains characters reserved by JMX
- **THEN** it SHALL report the Broker ID exactly as supplied, without the quoting applied to the object name

#### Scenario: Purging a queue frees capacity
- **WHEN** a queue holding messages on a broker at its configured maximum is purged through the MBean
- **THEN** the queue SHALL become empty, the total waiting count SHALL drop accordingly, and a subsequent send SHALL succeed

#### Scenario: A failed sweep is visible over JMX
- **WHEN** a reclamation sweep fails for a broker
- **THEN** the MBean SHALL report at least one failed sweep and a description of the most recent failure
