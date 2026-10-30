# payments-service — Kotlin / Spring Boot service template

Production-shaped starter for a JVM backend + React/TypeScript UI. The sample domain is **payments**
(authorise → capture / cancel) because it exercises the things that matter in regulated environments:
idempotency, money handling, state machines, audit-friendly events and observability.

| Concern | What you get |
|---|---|
| Language / runtime | Kotlin 2.1, JDK 21, Spring Boot 3.5 |
| API | **Contract-first** OpenAPI 3 (`src/main/resources/openapi/payments-api.yaml`); Spring interfaces + models are generated, controllers implement them. RFC 9457 problem+json errors, `Idempotency-Key`, pagination. Swagger UI at `/docs` |
| Architecture | Hexagonal (domain / application / adapters), enforced by ArchUnit |
| Persistence | PostgreSQL, Flyway migrations, `JdbcClient`, optimistic locking |
| Events | Transactional outbox + relay (at-least-once, `SKIP LOCKED`), broker adapter is a one-class swap |
| Tests | Unit (fakes, no mocks), web slice, architecture, integration with Testcontainers + **OpenAPI contract validation** of every response, Awaitility for async |
| Observability | Structured JSON logs (ECS, `prod` profile), Micrometer metrics (`/actuator/prometheus`), OpenTelemetry traces (OTLP), `@Observed`, health probes |
| Delivery | Dockerfile (non-root), `compose.yaml` (Postgres + Jaeger), GitHub Actions (format, tests, OpenAPI lint, frontend, image) |
| Frontend | `web/` Vite + React + TypeScript; API types **generated from the same OpenAPI file** |
| IntelliJ | Shared run configurations in `.idea/runConfigurations`, HTTP Client file in `http/` |
| Docs | `docs/` architecture, API guidelines, runbook, ADRs, AI-assisted engineering guide |

## Quick start

```bash
gradle wrapper --gradle-version 8.14.3   # once; commit gradlew + gradle/wrapper (IntelliJ can also do this)
docker compose up -d db jaeger           # Postgres :5432, Jaeger UI :16686
./gradlew bootRun --args='--spring.profiles.active=local'
```

* Swagger UI: http://localhost:8080/docs
* Health: http://localhost:8080/actuator/health · Metrics: `/actuator/prometheus`
* Traces: http://localhost:16686
* Try requests from `http/payments.http` in IntelliJ.

Frontend: `cd web && npm install && npm run dev` → http://localhost:5173 (proxies `/v1` to :8080).

## Opening in IntelliJ IDEA

1. **File ▸ Open** the folder; import as a Gradle project (JDK 21).
2. Enable *Settings ▸ Build ▸ Compiler ▸ Annotation Processors* is **not** needed (Kotlin, no kapt).
3. Run `./gradlew openApiGenerate` (or just build) once so generated sources appear.
4. Use the shared run configs: **Run service (local)** (starts compose infra first), **Unit tests**,
   **Integration tests (Docker)**, **Full check**, **Format code**.

To make it a reusable *project template*: push to GitHub and tick **Template repository**, then use
*File ▸ New ▸ Project from Version Control*; or run `scripts/rename-project.sh ledger-service com.acme.ledger`
after copying it.

## Everyday commands

| Task | Command |
|---|---|
| Fast tests | `./gradlew test` |
| Integration tests (needs Docker) | `./gradlew integrationTest` |
| Everything + coverage (`build/reports/jacoco`) | `./gradlew check` |
| Format | `./gradlew spotlessApply` (then set `isEnforceCheck = true` in `build.gradle.kts`) |
| Regenerate API code | `./gradlew openApiGenerate` (runs automatically before compile) |
| Regenerate TS types | `cd web && npm run gen:api` |
| Lint the contract | `npx @redocly/cli lint src/main/resources/openapi/payments-api.yaml` |

## Changing the API (workflow)

1. Edit `payments-api.yaml` (backwards compatible unless you are versioning — see `docs/api-design-guidelines.md`).
2. Write/extend a test (controller slice or integration test; integration tests assert the response matches the contract).
3. Build: the compiler tells you which controller methods no longer match the generated interface.
4. `npm run gen:api` in `web/` and fix the UI types.

## Layout

```
src/main/kotlin/.../domain        pure model: Payment, Money, events, exceptions (no framework imports)
src/main/kotlin/.../application   use cases (PaymentService) + ports (repository, outbox, publisher)
src/main/kotlin/.../adapter       web (controllers, error mapping), persistence (JDBC), messaging (relay)
src/main/kotlin/.../config        Clock, observability aspect, static contract serving
src/main/resources/openapi        the contract (source of truth)
src/main/resources/db/migration   Flyway SQL
src/test                          unit, web slice, architecture, integration (tag: integration)
web/                              React + TypeScript UI
docs/                             architecture, guidelines, runbook, ADRs
```

## Status of this template

It was assembled without access to Maven Central, so the Gradle build has **not been compiled yet**
(the frontend build and tests were run). Run `./gradlew check` first; expect to fix small things such as
generated-signature mismatches. Dependency versions are pinned in `gradle/libs.versions.toml` — bump them
to current releases.
