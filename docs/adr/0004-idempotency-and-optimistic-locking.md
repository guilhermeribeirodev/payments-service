# 4. Idempotency keys and optimistic locking
Status: accepted

**Decision.** Creates are idempotent via a unique `idempotency_key` column + `ON CONFLICT DO NOTHING` (race-free, no pre-check).
Updates use a `version` column; a failed `UPDATE ... WHERE version = ?` surfaces as `409`.
**Consequences.** Safe client retries, no long-held row locks. Keys are kept forever in this template; add a retention policy if volume requires it.
