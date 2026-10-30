# 3. Transactional outbox for events
Status: accepted

**Context.** Publishing to a broker and committing to the DB cannot be atomic; dual writes lose or invent events.
**Decision.** Events are inserted into `outbox_events` in the same transaction as the state change. A poller (`OutboxRelay`) publishes
and marks them using `FOR UPDATE SKIP LOCKED`.
**Consequences.** At-least-once, ordered per poll batch; consumers de-duplicate by event id. Adds polling latency (default 1s) and needs a retention job.
To use Kafka/SNS/Service Bus, implement `EventPublisher` and remove `LoggingEventPublisher`. Alternatives considered: CDC (Debezium) — operationally heavier.
