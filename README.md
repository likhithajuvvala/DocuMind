# DocuMind

**An AI-Powered Document Intelligence Platform**

DocuMind is a multi-tenant, retrieval-augmented generation (RAG) platform that allows users to upload documents (contracts, reports, internal knowledge bases) and query them using natural language. The system returns grounded, streaming answers with inline citations that point back to the exact source document and page, rather than relying on an LLM's raw, unverifiable output.

The project is designed to demonstrate production-grade software engineering practices — not a proof-of-concept chatbot wrapper — by combining an enterprise Java backend (Spring Boot, Spring AI) with a modern frontend (Next.js) and a fully containerized, horizontally scalable deployment model (Docker, Kubernetes).

---

## Table of Contents

1. [Overview](#overview)
2. [Problem Statement](#problem-statement)
3. [Core Features](#core-features)
4. [System Architecture](#system-architecture)
5. [Technology Stack](#technology-stack)
6. [Database Schema](#database-schema)
7. [API Reference](#api-reference)
8. [Retrieval-Augmented Generation Flow](#retrieval-augmented-generation-flow)
9. [Security and Multi-Tenancy](#security-and-multi-tenancy)
10. [Getting Started](#getting-started)
11. [Configuration](#configuration)
12. [Project Structure](#project-structure)
13. [Testing Strategy](#testing-strategy)
14. [Observability](#observability)
15. [Deployment](#deployment)
16. [CI/CD Pipeline](#cicd-pipeline)
17. [Roadmap](#roadmap)
18. [Design Decisions and Trade-offs](#design-decisions-and-trade-offs)
19. [License](#license)

---

## Overview

Organizations accumulate large volumes of unstructured documents — contracts, policy documents, technical reports, meeting notes — that are difficult to search using keyword-based tools and impossible for most employees to read in full. DocuMind addresses this by combining semantic search with large language model reasoning, allowing users to ask direct questions ("What is the termination clause in the vendor agreement?") and receive precise, cited answers instead of a list of documents to search through manually.

The platform is built as a set of cooperating Spring Boot services behind a single gateway, with an asynchronous ingestion pipeline that processes uploaded files independently of the user-facing request path. The frontend is a Next.js application that provides document upload, a streaming chat interface, and an administrative dashboard for usage and system health.

---

## Problem Statement

Generic LLM chat interfaces cannot reliably answer questions about private, domain-specific documents because the model has no access to that content and has no mechanism to avoid fabricating an answer when it does not know one. Simply pasting documents into a prompt does not scale beyond a handful of pages and provides no way to trace an answer back to its source, which makes it unsuitable for any context where accuracy needs to be verifiable, such as legal, financial, or compliance-related work.

DocuMind solves this by:

- Storing documents in a searchable, semantically indexed form rather than relying on prompt-stuffing
- Restricting the model's context to retrieved, relevant passages only
- Returning citations alongside every answer so the source can be independently verified
- Explicitly declining to answer when no relevant content is found, rather than allowing the model to guess
- Enforcing workspace-level data isolation so no tenant can retrieve another tenant's content

---

## Core Features

**Document ingestion**
Support for PDF, DOCX, and plain text uploads. Files are stored in an object store and processed asynchronously through a pipeline that extracts text, splits it into overlapping chunks, generates vector embeddings, and indexes the result for retrieval. Document status is tracked and exposed to the frontend in real time.

**Conversational query interface**
Users interact with their documents through a chat interface. Responses are streamed token-by-token rather than returned as a single blocking response, and each response includes citations identifying the source document, page number, and chunk offset that informed the answer.

**Multi-tenancy**
All data — documents, embeddings, chat history, usage records — is scoped to a workspace. Every retrieval query is filtered by workspace identifier at the database and vector-store level, not only in application logic, to prevent cross-tenant data leakage.

**Usage and cost tracking**
Token consumption and estimated cost are recorded per request and aggregated per workspace and per user, exposed through an administrative dashboard.

**Guardrails against hallucination**
When a query has no sufficiently relevant retrieved content, the system returns an explicit "not found in your documents" response instead of allowing the model to answer from general knowledge.

**Operational readiness**
Structured logging, health check endpoints, metrics export, and a fully reproducible local development environment via Docker Compose, with a parallel Kubernetes deployment path for production.

---

## System Architecture

DocuMind is composed of four backend services behind a single API gateway, a Next.js frontend, and supporting infrastructure for storage, messaging, and vector search.

```
                         +----------------------------+
                         |         Next.js UI          |
                         |  (upload, chat, admin)      |
                         +--------------+---------------+
                                        | REST / SSE
                                        v
                         +----------------------------+
                         |   Spring Boot API Gateway   |
                         |  authentication, routing,   |
                         |  rate limiting               |
                         +--------------+---------------+
                                        |
            +---------------------------+---------------------------+
            v                           v                           v
  +---------------------+   +--------------------------+  +-----------------------+
  |  Document Service     |   |   Ingestion Worker         |  |  Query / Chat Service  |
  |  upload handling,     |   |   Kafka consumer,           |  |  Spring AI, RAG         |
  |  metadata management  |   |   text extraction,          |  |  orchestration,         |
  |                        |   |   chunking, embedding       |  |  streaming responses    |
  +----------+------------+   +-------------+--------------+  +-----------+------------+
             |                              |                             |
             v                              v                             v
     +----------------+          +----------------------+       +-----------------------+
     |  PostgreSQL      |          |   Vector Store          |       |   LLM Provider          |
     |  metadata, jobs, |          |   Qdrant / pgvector     |       |   OpenAI, Anthropic,   |
     |  users, chat log |          |                        |       |   or local via Ollama  |
     +----------------+          +----------------------+       +-----------------------+
             |
             v
     +----------------+
     |  Object Storage  |
     |  MinIO / S3       |
     +----------------+
```

Document upload and ingestion are decoupled through a message queue so that large or slow-processing files do not block the request path or degrade query latency for other users. The query service is kept separate from the ingestion pipeline so that each can be scaled independently based on its own load characteristics — ingestion is CPU and I/O bound, while querying is latency-sensitive and depends on external LLM API response times.

A modular monolith with clearly separated internal packages (`document`, `ingestion`, `query`, `auth`) is an equally valid starting point and is recommended if the microservice topology above is not required immediately; the service boundaries can be extracted later once the system's real scaling bottlenecks are understood.

---

## Technology Stack

| Layer | Technology | Purpose |
|---|---|---|
| Backend framework | Spring Boot 3.x, Java 21 | Core application services |
| AI orchestration | Spring AI | Embedding generation, chat client abstraction, vector store integration |
| Frontend | Next.js 14 (App Router), TypeScript | Chat UI, upload flow, admin dashboard |
| Authentication | Spring Security, JWT (optionally OAuth2/Keycloak) | User authentication and workspace-scoped authorization |
| Relational database | PostgreSQL | Users, workspaces, documents, jobs, chat history, usage logs |
| Vector store | pgvector or Qdrant | Embedding storage and similarity search |
| Object storage | MinIO (local), Amazon S3 (production) | Raw document storage |
| Message broker | Apache Kafka | Decoupling upload from asynchronous ingestion |
| LLM provider | OpenAI or Anthropic API, with Ollama as a local/offline fallback | Answer generation |
| Text extraction | Apache Tika, PDFBox | Extracting text from PDF and DOCX files |
| Containerization | Docker, Docker Compose | Local development and reproducible builds |
| Orchestration | Kubernetes, Helm | Production deployment |
| Observability | Micrometer, Prometheus, Grafana, structured JSON logging | Metrics, dashboards, log aggregation |
| CI/CD | GitHub Actions | Build, test, security scan, deploy |
| Testing | JUnit 5, Testcontainers, Mockito | Unit, integration, and containerized integration testing |

---

## Database Schema

```sql
users            (id, email, password_hash, workspace_id, role, created_at)
workspaces       (id, name, plan, created_at)
documents        (id, workspace_id, filename, storage_path, status, uploaded_by, created_at)
document_chunks  (id, document_id, chunk_text, page_number, embedding_id, created_at)
chat_sessions    (id, workspace_id, user_id, document_id NULLABLE, created_at)
chat_messages    (id, session_id, role, content, citations JSONB, created_at)
ingestion_jobs   (id, document_id, status, error_message, started_at, finished_at)
usage_logs       (id, workspace_id, user_id, tokens_used, cost_estimate, created_at)
```

The vector store holds each embedding keyed by `embedding_id`, with `document_id` and `page_number` stored as payload metadata so that a retrieved chunk can be mapped back to its exact source location for citation purposes.

---

## API Reference

```
Authentication
POST   /api/auth/register
POST   /api/auth/login

Documents
POST   /api/documents/upload              Upload a document; returns document_id, status = PENDING
GET    /api/documents                     List documents in the current workspace
GET    /api/documents/{id}/status         Poll or subscribe to ingestion progress

Chat
POST   /api/chat/sessions                 Create a chat session, optionally scoped to a single document
POST   /api/chat/sessions/{id}/messages   Send a message; streams the response via Server-Sent Events
GET    /api/chat/sessions/{id}            Retrieve chat history for a session

Administration
GET    /api/admin/usage                   Token and cost usage by workspace and user
GET    /api/admin/documents/status        Ingestion pipeline health and failure rates
```

All endpoints other than authentication require a valid JWT and are scoped to the workspace encoded in the token.

---

## Retrieval-Augmented Generation Flow

1. The user submits a question through the chat interface.
2. The query service embeds the question using the same embedding model used during ingestion.
3. A similarity search is executed against the vector store, restricted to the requesting workspace's document set, returning the top-k most relevant chunks (typically five to eight).
4. An optional re-ranking step reorders the retrieved chunks by relevance before they are included in the prompt.
5. A grounded prompt is constructed from system instructions, the retrieved chunks with their source labels, recent chat history, and the user's question.
6. The prompt is sent to the LLM provider with streaming enabled, and tokens are forwarded to the frontend as they are generated.
7. Once the response is complete, citation metadata is attached by mapping the chunk identifiers used in the answer back to their source document and page.
8. The full exchange, including citations, is persisted to the chat history table.

If no retrieved chunk meets a minimum relevance threshold, the pipeline short-circuits and returns a response indicating that the answer was not found in the workspace's documents, rather than passing an empty or weak context to the model.

---

## Security and Multi-Tenancy

- All persisted records include a `workspace_id`, and every query — both relational and vector — is filtered by the workspace derived from the authenticated user's JWT, not from any client-supplied parameter.
- Passwords are hashed using a strong adaptive algorithm (bcrypt or Argon2); JWTs are short-lived and refreshed via a separate refresh-token flow.
- File uploads are validated by content type and size before being accepted into the ingestion pipeline.
- An optional PII redaction step can be applied to extracted text before it is sent to a third-party LLM provider, which is relevant for any deployment handling sensitive or regulated documents.
- Rate limiting is applied at the gateway layer, scoped per workspace, to prevent a single tenant from exhausting shared LLM API quota.

---

## Getting Started

### Prerequisites

- Docker and Docker Compose
- Java 21 or later, for backend development outside of containers
- Node.js 20 or later, for frontend development outside of containers
- An API key for the chosen LLM provider (OpenAI or Anthropic), or a locally running Ollama instance for fully offline operation

### Running locally

```bash
git clone https://github.com/<your-username>/documind.git
cd documind
cp .env.example .env
docker compose up --build
```

This starts the following services:

| Service | Address |
|---|---|
| Next.js frontend | http://localhost:3000 |
| Spring Boot API gateway | http://localhost:8080 |
| PostgreSQL | localhost:5432 |
| MinIO console | http://localhost:9001 |
| Qdrant | localhost:6333 |
| Kafka | localhost:9092 |
| Grafana | http://localhost:3001 |

A seeded demo workspace with sample documents is created on first startup for evaluation without requiring a manual signup and upload cycle.

---

## Configuration

Environment variables are defined in `.env.example`. The most relevant are:

```
LLM_PROVIDER=openai              # openai | anthropic | ollama
OPENAI_API_KEY=
ANTHROPIC_API_KEY=
DATABASE_URL=jdbc:postgresql://postgres:5432/documind
JWT_SECRET=
JWT_EXPIRY_MINUTES=60
S3_ENDPOINT=http://minio:9000
S3_ACCESS_KEY=
S3_SECRET_KEY=
VECTOR_STORE=pgvector             # pgvector | qdrant
QDRANT_URL=http://qdrant:6333
KAFKA_BOOTSTRAP_SERVERS=kafka:9092
```

Secrets should never be committed to source control; `.env` is included in `.gitignore` and `.env.example` documents the required keys without values.

---

## Project Structure

```
documind/
    backend/
        gateway-service/        authentication, routing, rate limiting
        document-service/       upload handling, metadata, storage
        ingestion-worker/       asynchronous chunking and embedding pipeline
        query-service/          RAG orchestration and streaming chat
    frontend/                   Next.js application
    infra/
        docker-compose.yml
        k8s/                     Kubernetes manifests and Helm chart
    docs/
        project-brief.md        full design document
    .github/
        workflows/               CI/CD pipeline definitions
```

---

## Testing Strategy

Backend services are tested at the unit level with JUnit 5 and Mockito, and at the integration level with Testcontainers, which starts real PostgreSQL and Kafka instances in isolated containers for each test run rather than relying on mocks for infrastructure dependencies. Calls to external LLM providers are mocked in automated tests to keep the suite deterministic and free of external API cost; a smaller set of manual or scheduled tests exercises the real provider integration.

```bash
# Backend
cd backend && ./gradlew test

# Frontend
cd frontend && npm run test
```

---

## Observability

Each backend service exposes a Spring Boot Actuator health endpoint and Micrometer metrics in Prometheus format. A Grafana dashboard, provisioned automatically in the local Docker Compose environment, visualizes request latency, ingestion pipeline throughput and failure rate, and per-workspace token usage. Logs are emitted in structured JSON to support aggregation in any standard log pipeline (for example, the ELK stack or a hosted equivalent) in production.

---

## Deployment

The local development environment is fully described by `infra/docker-compose.yml` and requires no external dependencies beyond an LLM provider API key. For production, Kubernetes manifests (or an equivalent Helm chart) under `infra/k8s/` define deployments, services, and horizontal pod autoscaling configuration for each backend service independently, reflecting their different scaling characteristics. Container images are built from multi-stage Dockerfiles to keep production images minimal, and are scanned for known vulnerabilities before being pushed to a registry.

---

## CI/CD Pipeline

The GitHub Actions workflow, defined under `.github/workflows/`, runs on every push and pull request and performs the following stages in order: dependency installation, static analysis and linting, the full unit and integration test suite, container image build, vulnerability scanning, and — on merges to the main branch — a push to the container registry followed by deployment to the target environment.

---

## Roadmap

Completed:
- Authentication and workspace-based multi-tenancy
- Document upload and asynchronous ingestion pipeline
- Retrieval-augmented query flow with streaming responses and citations

Planned:
- Cross-document comparison queries (for example, comparing a clause across multiple contracts)
- Agent mode with tool-calling capability, allowing the model to take actions such as fetching a document's latest version
- Optical character recognition support for scanned PDF documents
- Chat-platform integration (Slack, Microsoft Teams) for querying documents outside the web interface
- Document-level, rather than only workspace-level, access permissions

---

## Design Decisions and Trade-offs

The choice of a service-oriented backend over a single monolithic application was made to allow the ingestion pipeline and the query path to scale independently, since their resource profiles differ substantially; the trade-off is additional operational complexity, which is why a modular monolith is presented as an equally acceptable starting point in the architecture section above. PostgreSQL with the pgvector extension is offered as the default vector store to minimize the number of infrastructure components required to run the system locally, with Qdrant available as a drop-in alternative for deployments requiring higher-throughput vector search at larger scale. The LLM provider is abstracted behind a single interface specifically so that the system can run entirely offline against a local model via Ollama, which removes any dependency on a paid API for development, testing, or demonstration purposes.

---

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.
