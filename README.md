# Mini WSA

Mini Web Security Analytics pipeline — a simplified Akamai WSA service that ingests security events (DLRs), classifies and enriches them, and exposes analytics APIs.

Context: coordinated campaign **Operation Shadow Flood** (SQL injection probes, credential stuffing bots, slow-rate DDoS). SOCs are flooded with raw Data Log Records; this service turns them into scored, queryable analytics.

## Tech stack

- Java 21
- Spring Boot 3.4
- Spring Web, Spring Data JPA, Bean Validation
- PostgreSQL 16 (primary store)
- H2 (tests)
- Springdoc OpenAPI (Swagger UI)
- Docker / Docker Compose

## Prerequisites

- JDK 21
- Maven 3.9+ (or the included `./mvnw` wrapper)
- Docker Desktop (for `docker compose up`)

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on pull requests targeting `main` / `master`.

## How to build & run

### Local (Maven + Postgres)

1. Start Postgres (or use Compose for the database only):

```bash
docker compose up -d postgres
```

2. Build and run the app:

```bash
./mvnw verify
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`.

### Full stack (Docker Compose)

```bash
docker compose up --build
```

If host ports `8080` or `5432` are already in use:

```bash
APP_PORT=18080 POSTGRES_PORT=5434 docker compose up --build
```

Make aliases: `make up`, `make down`, `make logs`.

## API docs

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Ingest — `POST /v1/events/ingest`

Accepts a single event object or an array of events. Always returns **201** with a JSON **array** of stored events (a single ingest is `[one]`). The server sets `receivedAt` and ignores client-supplied `receivedAt` / enrichment fields. `attackType` and `threatScore` are null until v0.2.

Validation failures return **400** RFC 7807 Problem Details. Duplicate `eventId` returns **409**.

Single event:

```bash
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "evt-00132",
    "timestamp": "2026-05-20T14:32:10Z",
    "configId": 14227,
    "policyId": "pol_web1",
    "clientIp": "203.0.113.42",
    "hostname": "www.example.com",
    "path": "/api/v1/login",
    "method": "POST",
    "statusCode": 403,
    "userAgent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
    "rule": {
      "id": "950001",
      "name": "SQL_INJECTION",
      "message": "SQL Injection Attack Detected",
      "severity": "CRITICAL",
      "category": "INJECTION"
    },
    "action": "DENY",
    "geoLocation": { "country": "CN", "city": "Beijing" },
    "requestSize": 1024,
    "responseSize": 256
  }'
```

Batch:

```bash
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' \
  -d '[{ "eventId": "evt-00133", "timestamp": "2026-05-20T14:32:10Z", "configId": 14227, "clientIp": "203.0.113.42", "path": "/login", "rule": { "severity": "HIGH", "category": "BOT" }, "action": "ALERT" }]'
```

## Architecture

Layered Spring Boot service: `Controller` → `Service` → `Repository` → PostgreSQL.

Packages: `config`, `controller`, `dto`, `model`, `repository`, `service`, `enrichment`, `exception`, `datagen`.

A diagram will be added once ingestion, enrichment, and analytics APIs are in place.

## Storage justification

PostgreSQL is the primary store: relational queries, JPQL aggregations for stats, and indexes on `configId`, `timestamp`, `clientIp`, and `path` keep top-N attacker/path queries efficient at assignment scale. H2 is used only for unit/slice tests so CI does not require Docker.

## Git tags / milestones

| Tag | Meaning |
| --- | --- |
| `v0.0-infrastructure` | Project scaffold, Docker/Postgres, base config |
| `v0.1-ingestion` | `POST /v1/events/ingest` + validation + `receivedAt` |
| `v0.2-enrichment` | Attack classification + `ThreatScoreCalculator` |
| `v0.3-stats` | `GET /v1/stats/summary` (JPQL aggregations) |
| `v0.4-samples` | `GET /v1/events/samples` pagination and filters |

## What I’d improve given more time

_TBD after implementation._

## Challenges and how they were solved

_TBD after implementation._

## Bonus ideas

Not in scope for `v0.0-infrastructure`:

- Alerting: `POST /v1/alerts/define`, `GET /v1/alerts/evaluate`
- Time-series: `GET /v1/stats/timeseries`
- Rate limiting on stats/samples APIs (429)
