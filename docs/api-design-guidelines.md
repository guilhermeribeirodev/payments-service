# API design guidelines

1. **Contract first.** `payments-api.yaml` is the source of truth; review API changes as contract diffs.
2. **Resources and verbs.** Nouns in plural (`/v1/payments`), HTTP verbs for semantics. State transitions that are not CRUD
   are sub-resource actions (`POST /v1/payments/{id}/capture`).
3. **Versioning.** Major version in the path (`/v1`). Within a version only additive, backwards-compatible changes:
   new optional fields, new endpoints, new enum values *only if* clients are told to tolerate unknown values.
4. **Idempotency.** Unsafe creates require `Idempotency-Key`. Same key + same body → original response (`200`);
   same key + different body → `409`.
5. **Errors.** RFC 9457 `application/problem+json` with a stable `type` (`urn:problem:<slug>`), human `detail`. Never expose stack traces.
   Status codes: `400` malformed/validation, `404` unknown id, `409` state conflict, `422` well-formed but semantically invalid.
6. **Money & time.** Integer minor units + ISO 4217; RFC 3339 UTC timestamps.
7. **Pagination.** `limit` (max 100) + `offset`, response has `items`, `limit`, `offset`, `total`. Switch to cursors if data grows large.
8. **Naming.** camelCase JSON, UPPER_SNAKE enums, `operationId` is verb+noun (`createPayment`) — it becomes the Kotlin method name.
9. **Documentation.** Every operation has a summary; non-obvious behaviour goes in `description`. Examples on request schemas.
10. **Lint.** CI runs Redocly lint on the contract.
