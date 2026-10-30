# Guidance for AI assistants working in this repo

* Stack: Kotlin, Spring Boot 3, Gradle (Kotlin DSL), PostgreSQL, Flyway, React/TypeScript in `web/`.
* The OpenAPI file `src/main/resources/openapi/payments-api.yaml` is the source of truth. Never edit generated code in `build/`.
* Respect the hexagonal layout; `./gradlew test` includes ArchUnit rules that must stay green.
* Write or update tests with every change (unit first; integration tests for persistence/HTTP behaviour).
* Schema changes only through new Flyway migrations; never edit applied ones.
* Money is `Long` minor units. No secrets or PII in code, logs or prompts.
* Before finishing: `./gradlew spotlessApply check` and, if the contract changed, `cd web && npm run gen:api && npm test`.
* See `docs/ai-assisted-engineering.md` for the validation checklist.
