# Runbook

| Symptom | Look at | Likely cause / action |
|---|---|---|
| Readiness `DOWN` | `/actuator/health` | DB unreachable or Flyway failed; check `DB_URL`, credentials, migration logs |
| Events not arriving downstream | `SELECT count(*) FROM outbox_events WHERE published_at IS NULL` | Relay failing (look for `Failed to publish event` warnings) or `payments.outbox.relay.enabled=false`. Backlog is safe: delivery resumes in order |
| Duplicate events downstream | consumer logs | Expected at-least-once behaviour; consumer must de-duplicate on event `id` |
| `409 concurrent-update` spikes | trace of the failing request | Two writers on one payment; clients should retry |
| Slow requests | Jaeger trace, `http.server.requests` histogram | Check the DB span; add an index in a new Flyway migration |

## Configuration (env vars)

`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `OTLP_TRACING_ENDPOINT`, `TRACING_SAMPLING_PROBABILITY`,
`SPRING_PROFILES_ACTIVE` (`local` | `prod`).

## Deploy / rollback

Container image is stateless; Flyway runs at startup. Migrations must be backwards compatible with the previous release
(expand → migrate → contract) so a rollback never needs a down-migration.

## Before production (not included in the template)

* Authentication/authorisation on `/v1/**`, rate limiting
* Real broker adapter + schema for events, dead-letter handling, outbox retention job
* Secrets from a vault, TLS termination, network policies
* Alerts on outbox backlog age, 5xx rate, p99 latency
