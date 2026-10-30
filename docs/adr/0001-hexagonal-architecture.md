# 1. Hexagonal architecture with executable rules
Status: accepted

**Context.** We want business rules testable without Spring or a database, and infrastructure swappable.
**Decision.** Packages `domain` → `application` (ports) ← `adapter.*`. ArchUnit tests enforce the dependency direction.
**Consequences.** More files than a layered CRUD app, but fast unit tests, and replacing JDBC or the broker touches one adapter.
