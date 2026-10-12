## 1. Implement Shared Registry

- [x] 1.1 Implement `InMemoryBrokerRegistry` in `it.baccan.vmjms.broker` to map `brokerId` strings to shared `InMemoryBroker` instances.
- [x] 1.2 Add a static `clear()` method to `InMemoryBrokerRegistry` to release memory between tests.

## 2. Refactor Broker ID Mapping

- [x] 2.1 Refactor `InMemoryBroker`'s constructor to accept a `String brokerId` and use it instead of generating a new hex timestamp inside.
- [x] 2.2 Update `InMemoryBroker.generateNextMessageId()` to format IDs as `ID:<brokerId>:<sequence>`.
- [x] 2.3 Refactor `InMemoryQueue` to parse query parameters (e.g., `?param1=val1&param2=val2`) from the queue name, separating it into the base queue name and exposing parsed parameters via `public Map<String, String> getParameters()`.

## 3. Extend ConnectionFactory Constructors

- [x] 3.1 Update `InMemoryConnectionFactory` to add constructor `InMemoryConnectionFactory(String brokerId)` resolving its broker from the registry.
- [x] 3.2 Update default no-argument constructor `InMemoryConnectionFactory()` to delegate to `this(Long.toHexString(System.currentTimeMillis()).toLowerCase())` for isolation by default.

## 4. Verification and Unit Tests

- [x] 4.1 Update `InMemoryJMSTest.java` to add a test case verifying that connection factories with the same Broker ID share queue storage and messages, while factories with different IDs remain isolated.
- [x] 4.2 Update `InMemoryJMSTest.java` to add a test case verifying `InMemoryQueue` parses base queue names and exposes query parameters correctly.
- [x] 4.3 Run test suite to ensure all tests pass successfully.
