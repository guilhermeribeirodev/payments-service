# 2. Contract-first OpenAPI
Status: accepted

**Context.** Code-first specs drift and hide breaking changes; frontend and other consumers need a stable reference.
**Decision.** `payments-api.yaml` is hand-written. `openapi-generator` (kotlin-spring, interfaces only) produces the Spring
interfaces and models; controllers implement them. Integration tests validate every response against the spec; the frontend
types are generated from it; CI lints it.
**Consequences.** API changes are reviewed as contract diffs; the compiler flags unimplemented operations. Generated code lives in `build/` and is never edited.
