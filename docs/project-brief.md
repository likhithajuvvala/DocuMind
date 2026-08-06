# DocuMind Project Brief

## Purpose of this document

The README describes what DocuMind is. This brief records how the starter template is put together, what is already wired up, and what is deliberately left open, so that the next change can be made without re-deriving the design.

## Module layout

| Module | Responsibility |
|---|---|
| `backend/common` | Entities, repositories, JWT issuing and verification, workspace context, object storage, Kafka event contracts, Flyway migrations |
| `backend/gateway-service` | Registration, login, token refresh, routing to downstream services, per-workspace rate limiting |
| `backend/document-service` | Upload validation, object storage writes, document metadata, ingestion status queries |
| `backend/ingestion-worker` | Kafka consumer that extracts text, chunks it, embeds it, and writes to the vector store |
| `backend/query-service` | Retrieval, grounded prompt construction, streaming chat, citation mapping, usage and admin endpoints |
| `frontend` | Next.js App Router UI for upload, chat, and the administrative dashboard |

The shared module owns the schema so that a single Flyway history applies to all services. Only `gateway-service` runs migrations; every other service validates against the existing schema.

## Request and event flow

1. The frontend authenticates against `gateway-service` and stores the returned access token.
2. Every subsequent call carries the token; each service resolves it into an `AuthenticatedUser` and binds the workspace into `WorkspaceContext` for the duration of the request.
3. `document-service` stores the uploaded file in object storage, writes a `PENDING` document row, and publishes `documind.document.uploaded`.
4. `ingestion-worker` consumes that event, tracks progress in `ingestion_jobs`, indexes chunks into the vector store, and publishes `documind.document.indexed` or `documind.document.failed`.
5. `query-service` embeds the question, runs a workspace-filtered similarity search, builds a grounded prompt, streams tokens over Server-Sent Events, and emits citations followed by a completion event.

## Deliberate starting points

- The vector store is pgvector, configured through Spring AI. Qdrant runs in Docker Compose so it can be swapped in without adding infrastructure later.
- Token usage is estimated from character counts rather than provider-reported usage. Replace `UsageRecorder` with provider metadata when the chat response exposes it.
- Rate limiting is an in-memory per-workspace counter in the gateway. It must move to a shared store before the gateway runs with more than one replica.
- Page numbers come from form-feed boundaries in the extracted text, which holds for PDFs but degrades for other formats. Per-format extraction is the natural next refinement.
- Re-ranking of retrieved chunks, described in the README, is not implemented; `ChunkRetriever` is the insertion point.

## Local development

```bash
cp .env.example .env
docker compose up --build
```

The root `compose.yaml` includes `infra/docker-compose.yml`, so the stack can be started from either directory.

Backend tests run with `cd backend && ./gradlew test`, frontend tests with `cd frontend && npm run test`.

## CORS is answered only at the gateway

`documind.cors.enabled` defaults to false and is switched on only in `gateway-service`. That is deliberate: the browser only ever talks to the gateway, and downstream services are reached through it.

When the shared security configuration enabled CORS everywhere, a proxied response carried the header from both the downstream service and the gateway:

```
Access-Control-Allow-Origin: http://localhost:3000, http://localhost:3000
```

Browsers reject a duplicated value outright, so `/api/documents` failed in the UI while `/api/auth/login` worked, because login is served by the gateway itself and never proxied. Command line clients ignore the duplicate, so this only reproduces in a browser or by counting response headers.

If a downstream service ever needs to be called directly from a browser, enable CORS on that service alone and keep it off at the gateway for those routes, rather than turning it on in both.

## Demo workspace seeding

`document-service` seeds a demo workspace once, on the first startup where `documind.demo.enabled` is true. It creates the workspace, an admin user, and uploads the sample documents bundled in `document-service/src/main/resources/demo-documents/` through the same ingestion path a real upload takes, so the seeded files are stored in object storage and published to Kafka like any other document.

Seeding is off by default and enabled only in Docker Compose, so a production deployment never creates the demo account unless it is asked to. The default credentials are `demo@documind.test` / `demo-password-2026`, overridable with `DEMO_USER_EMAIL` and `DEMO_USER_PASSWORD`.

The seeder skips silently when the demo user already exists, which covers both restarts and a second replica starting at the same time. Seeded documents remain `PENDING` until `ingestion-worker` runs with an embedding provider configured; the seeder's job ends once the upload events are published.

## Dashboards and metrics

Grafana provisions both a Prometheus datasource (uid `documind-prometheus`) and the **DocuMind Overview** dashboard from `infra/observability/`, so a fresh `docker compose up` lands on a populated dashboard with no manual import.

The panels cover what the README promises — request latency, ingestion throughput and failure rate, and per-workspace token usage — which required instrumenting the application, because none of those metrics existed before:

| Metric | Emitted by | Tags |
|---|---|---|
| `documind.ingestion.documents` | `IngestionMetrics` | `result`, `failure` |
| `documind.ingestion.chunks` | `IngestionMetrics` | — |
| `documind.ingestion.duration` | `IngestionMetrics` | histogram |
| `documind.llm.tokens` | `UsageRecorder` | `workspace`, `model`, `kind` |
| `documind.llm.cost` | `UsageRecorder` | `workspace`, `model` |
| `documind.chat.answers` | `AnswerStreamService` | `outcome` |

Every meter sharing a name must carry the same tag keys, or Micrometer silently drops the inconsistent ones — which is why the indexed counter carries `failure="none"`. `IngestionMetricsTest` and `UsageRecorderTest` assert the exact Prometheus series names the dashboard queries, so renaming a metric without updating the dashboard fails the build rather than producing an empty panel.

Latency percentiles depend on `management.metrics.distribution.percentiles-histogram.http.server.requests`, enabled in every service; without it the p95 panel has no buckets to query.

The `workspace` tag on token metrics is per-tenant by design. It is fine at demo scale but is unbounded cardinality, so a large deployment should aggregate or drop that tag before shipping these metrics to long-term storage.

## Java toolchain

The Gradle wrapper is pinned to 9.1.0 and every module targets a Java 21 toolchain, so a real JDK 21 must be resolvable. A JRE is not sufficient, because the build needs a compiler. When the only Java on the machine is a JRE, Gradle fails with `does not provide the required capabilities: [JAVA_COMPILER]`, the IDE's Gradle sync fails with it, and every import in the project is then reported as unresolved.

Gradle resolves the toolchain in one of three ways: a JDK 21 installed system-wide, a path listed under `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`, or an automatic download through the Foojay resolver configured in `settings.gradle.kts`.

Fedora 44 ships no `java-21-openjdk` package, so on that distribution the JDK comes from the Adoptium repository with `sudo dnf install temurin-21-jdk`, which installs to `/usr/lib/jvm/temurin-21-jdk`. The system default `java` can remain a newer release; only the toolchain lookup needs to find a JDK 21.

Only the subprojects declare a toolchain, so the root project deliberately does not apply the `java` plugin. Applying it there would give the root a `compileJava` task with no toolchain, which falls back to whichever JVM Gradle itself runs on and fails when that JVM is a JRE.
