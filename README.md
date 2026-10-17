# Mini WSA

Mini Web Security Analytics pipeline — a simplified Akamai WSA service that ingests security events (DLRs), classifies and enriches them, and exposes analytics APIs.

Context: coordinated campaign **Operation Shadow Flood** (SQL injection probes, credential stuffing bots, slow-rate DDoS). SOCs are flooded with raw Data Log Records; this service turns them into scored, queryable analytics.

## Tech stack

- Java 21
- Spring Boot 3.4
- Spring Web, Spring Data JPA, Bean Validation
- PostgreSQL 16 (primary store)
- H2 (ingest slice tests)
- Testcontainers PostgreSQL (stats and samples API integration tests)
- Springdoc OpenAPI (Swagger UI)
- Docker / Docker Compose

## Prerequisites

- JDK 21
- Maven 3.9+ (or the included `./mvnw` wrapper)
- Docker Desktop (for `docker compose up` and Testcontainers during `./mvnw verify`)

## CI

GitHub Actions (`.github/workflows/ci.yml`) runs `./mvnw -B verify` on pull requests targeting `main` / `master`.

## How to build & run

### Local (Maven + Postgres)

`./mvnw spring-boot:run` uses `localhost:5432` from `application.yml`. Use any Postgres with database/user/password `miniwsa`. Compose Postgres is internal-only (no host `5432` mapping); `psql` from the host is `docker compose exec postgres psql -U miniwsa -d miniwsa`.

```bash
./mvnw verify
./mvnw spring-boot:run
```

The API listens on `http://localhost:8080`.

### Full stack (Docker Compose)

```bash
docker compose up --build
```

Postgres stays on the Compose network only (`postgres:5432`). The host mapping that used to fail when 5432 was already allocated is gone. The app still publishes **8080**. If that port is taken:

```bash
APP_PORT=18080 docker compose up --build
```

Make aliases: `make up`, `make down`, `make logs`.

The demo page is [http://localhost:8080/](http://localhost:8080/). **Generate and ingest** calls `GET /v1/dev/events`, then posts each chunk to ingest. **Download JSON (no ingest)** is that same GET only — nothing is stored. **Load stats summary** and **Load samples** call `GET /v1/stats/summary` and `GET /v1/events/samples` on the last ingest window (or the last 24 hours).

### Generate a JSON file (CLI)

The generator has no Spring dependencies. Same `--seed` and `--end` produce byte-identical output. Default seed is **42**. Progress and the attacker summary go to stderr; the JSON array goes to stdout or `-o`.

```bash
./mvnw -q compile exec:java -Dexec.args='--count 10000 --seed 42 --span 24h --end 2026-06-02T00:00:00Z -o events.json'
```

Load a file at or under the live ingest cap (default **1000**, override with `APP_INGEST_MAX_BATCH_SIZE`):

```bash
curl -sS -X POST http://localhost:8080/v1/events/ingest \
  -H 'Content-Type: application/json' \
  --data-binary @events.json
```

Larger files must be sliced. Read the cap from `X-Max-Batch-Size` (do not assume 1000):

```bash
CAP=$(curl -sS -D - -o /tmp/events.json \
  'http://localhost:8080/v1/dev/events?count=10000&seed=42&span=24h&end=2026-06-02T00:00:00Z' \
  | awk 'tolower($1)=="x-max-batch-size:" {print $2}' | tr -d '\r')
i=0
len=$(jq 'length' /tmp/events.json)
while [ "$i" -lt "$len" ]; do
  jq -c ".[$i:$((i + CAP))]" /tmp/events.json \
    | curl -sS -X POST http://localhost:8080/v1/events/ingest \
        -H 'Content-Type: application/json' \
        --data-binary @-
  i=$((i + CAP))
done
```

## Sample data (assignment Part 5)

| Part 5 need | Where it lives |
| --- | --- |
| Realistic mix of categories, actions, and paths | Weighted background events plus pinned attack waves |
| Repeat-offender / campaign waves | Showcase IPs `203.0.113.10`–`.12` and ~20% returning wave IPs |
| Reproducible dataset | Seeded RNG; `eventId` is `evt-s{seed}-{index}` |
| Configurable volume | `--count` / page count (CLI and GET: 148–100000; page caps at 20000) |
| Load through the real ingest API | Demo page chunks on `X-Max-Batch-Size`; CLI writes a file for curl |
| Demo without extra Compose services | Static `index.html` on `:8080` |

A default 10k-event run has 50 wave IPs. Ranks 1–3 of `topAttackers` are always 40, 36, then 30 events (`203.0.113.10` two bursts on `/admin/users`, `.11` one burst on `/api/v1/login`, `.12` two bursts on `/api/v1/login`). `topTargetedPaths` starts at `/api/v1/login` (66) then `/admin/users` (40). Background IPs stay at most 4 events so they never enter the top 10.

## API docs

Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

OpenAPI JSON: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)

### Generate — `GET /v1/dev/events`

Returns a generated JSON **array** of ingest payloads and writes nothing. Gated by `app.generator.enabled` (default **true**); **404** when disabled. Query parameters: `count` (default 10000, range 148–100000), `seed` (default 42), `span` (default `24h`, 69m–31d; also accepts `PT24H`), `end` (default now, must not be in the future). Success sets `X-Max-Batch-Size` to the live ingest cap and `X-Generator-Summary` to the pinned and returning attackers. Invalid arguments return **400** problem details.

```bash
curl -sS 'http://localhost:8080/v1/dev/events?count=148&seed=42&span=24h&end=2026-06-02T00:00:00Z'
```

### Ingest — `POST /v1/events/ingest`

Accepts a single event object or an array of events. Always returns **201** with a JSON **array** of stored events (a single ingest is `[one]`). The server sets `receivedAt` and computes `attackType` and `threatScore`. Client-supplied `receivedAt` / enrichment fields are ignored.

Arrays larger than `app.ingest.max-batch-size` (default **1000**) return **400**; nothing is written. Validation failures return **400** RFC 7807 Problem Details. Duplicate `eventId` returns **409**.

There is no JSON body-size filter: Tomcat `maxPostSize` only limits form-encoded parsing, not a JSON body Spring reads from the input stream. A real cap would check `Content-Length` and wrap the input stream.

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

### Enrichment

Every ingested event is classified and scored before persist. `attackType` is a display string from `rule.category`:

| `rule.category` | `attackType` |
| --- | --- |
| `INJECTION` | SQL/Command Injection |
| `XSS` | Cross-Site Scripting |
| `PROTOCOL_VIOLATION` | Protocol Anomaly |
| `DATA_LEAKAGE` | Data Exfiltration |
| `BOT` | Bot Activity |
| `DOS` | Denial of Service |
| `RATE_LIMIT` | Rate Limiting |

`threatScore` is the sum of:

- Severity base: CRITICAL 40, HIGH 30, MEDIUM 20, LOW 10
- Action bonus: DENY +20, ALERT +10, MONITOR +0
- Path bonus: path contains `/admin` or `/login` → +15
- Repeat-offender bonus: +15 when `priorEventCount + 1 > 5` (more than 5 events from the same `clientIp` in the window)

The repeat window is inclusive `[timestamp − 10 minutes, timestamp]`. Priors are stored rows for that IP plus **earlier siblings in the same batch** (stable-sorted by timestamp; response order stays the request order). Ingest loads history in **one** query: each distinct IP contributes its own `[minTs − 10m, maxTs]` predicate, OR-ed together so `idx_se_ip_ts` can BitmapOr the ranges. Results are ordered by IP then timestamp and binary-searched per event in memory. A later step is a per-event SQL count; a Redis/stream-state sliding window remains the option at real scale.

Scores are capped at 100 (`Math.min(100, raw)`). Today's formula max is 90 (CRITICAL + DENY + path + repeat); 100 is reserved for later bonuses.

### Stats — `GET /v1/stats/summary`

Aggregates stored events for an optional `configId` and a required half-open time window on event `timestamp` (`[from, to)`). Omit `configId` to aggregate across all configurations; the response still includes `configId: null`.

`from` and `to` are required ISO-8601 instants with an offset. `from` must be strictly before `to`. The window may be at most `app.stats.max-range` (default **31d**, exact `Duration.ofDays(31)`); longer ranges return **400**. Empty matches return **200** with zero-filled maps, `avgThreatScore: null`, and empty top lists.

`byCategory` always contains all seven `RuleCategory` keys and `byAction` all three `Action` keys (stable dashboard shape). Averages are rounded to one decimal, half-up; an average of zero events is `null`, not `0`. Top attackers are the top 10 by count, then avg threat score descending, then `clientIp` ascending. Top paths are the top 10 by count, then `path` ascending.

```bash
curl -sS 'http://localhost:8080/v1/stats/summary?configId=14227&from=2026-06-01T00:00:00Z&to=2026-06-02T00:00:00Z'
```

Example:

```json
{
  "configId": 14227,
  "timeRange": { "from": "2026-06-01T00:00:00Z", "to": "2026-06-02T00:00:00Z" },
  "totalEvents": 4,
  "avgThreatScore": 56.3,
  "byCategory": {
    "INJECTION": { "count": 2, "avgThreatScore": 75.0 },
    "XSS": { "count": 1, "avgThreatScore": 35.0 },
    "PROTOCOL_VIOLATION": { "count": 0, "avgThreatScore": null },
    "DATA_LEAKAGE": { "count": 0, "avgThreatScore": null },
    "BOT": { "count": 1, "avgThreatScore": 40.0 },
    "DOS": { "count": 0, "avgThreatScore": null },
    "RATE_LIMIT": { "count": 0, "avgThreatScore": null }
  },
  "byAction": { "DENY": 2, "ALERT": 1, "MONITOR": 1 },
  "topAttackers": [
    { "clientIp": "203.0.113.42", "count": 3, "avgThreatScore": 61.7 }
  ],
  "topTargetedPaths": [
    { "path": "/api/v1/login", "count": 2 }
  ]
}
```

Three JPQL aggregations (category×action, top attackers, top paths) run in parallel, so latency is the slowest query rather than the sum; that triples concurrent database load per request and helps latency, not throughput. The three scans share `app.stats.query-threads` workers (default **6**) and a queue of `app.stats.query-queue-capacity` (default **6**) with samples. Hikari’s `maximum-pool-size` is **10**, so ingest keeps the remaining connections. When the worker pool and queue are full, the request returns **503** with `Retry-After: 2` instead of running the scan on the Tomcat thread. A present `configId` uses `e.configId = :configId` so Postgres can pick `idx_se_config_ts`; omit `configId` to scan `idx_se_ts`. Category and action counts come from one `GROUP BY e.rule.category, e.action` (at most 21 buckets), then `StatsService` sums those buckets. Totals and the root average are derived from the category sums, not a fourth query. Top-N uses Spring Data `Limit.of(10)` and returns a `List` (never `Page`, which would emit a derived count query).

### Samples — `GET /v1/events/samples`

Returns individual enriched events matching optional filters, newest first. Every query parameter is optional; present filters are combined with AND. An omitted filter is left out of the `WHERE` clause.

| Parameter | Meaning |
| --- | --- |
| `configId` | equality on `configId` |
| `from` | `timestamp >= from` |
| `to` | `timestamp < to` |
| `category` | equality on `rule.category` |
| `action` | equality on `action` |
| `limit` | page size, default **20**, range **1..100** |
| `offset` | skip count, default **0**, range **0..10000** |

`from` and `to` are independent ISO-8601 instants with an offset. Both together are the same half-open window as stats (`[from, to)`). There is no 31-day cap. Order is `timestamp DESC, id DESC`.

The `SELECT` always runs. `total` is then either inferred or counted, and never walks more than **10_100** rows (`MAX_OFFSET + MAX_LIMIT`). A default unfiltered call does not `COUNT(*)` the table.

- `n < limit` (including an empty first page) → skip the count, `total = offset + n`, `totalExact: true`
- `n == limit` or `n == 0` with `offset > 0` → `SELECT id` with the same predicates, `LIMIT 10101`
  - `<= 10_100` rows → that count, `totalExact: true`
  - `10_101` rows → `total: 10100`, `totalExact: false` (at least this many; nobody can page past that)

`limit` and `offset` in the body are the applied values (defaults included). Empty matches return **200** with `"events": []`.

```bash
curl -sS 'http://localhost:8080/v1/events/samples?configId=14227&from=2026-06-01T00:00:00Z&to=2026-06-02T00:00:00Z&category=INJECTION&action=DENY&limit=20&offset=0'
```

Example:

```json
{
  "events": [
    {
      "id": 2,
      "eventId": "evt-00132",
      "timestamp": "2026-06-01T14:32:10Z",
      "configId": 14227,
      "path": "/api/v1/login",
      "action": "DENY",
      "attackType": "SQL/Command Injection",
      "threatScore": 75
    }
  ],
  "total": 1,
  "totalExact": true,
  "limit": 20,
  "offset": 0
}
```

Domain rejects return **400** with the ingest `errors` array (`detail` is `Validation failed`). All violations are listed: `limit` outside 1..100, `offset` outside 0..10000, and both bounds present with `from >= to`. A value that does not parse (`limit=abc`, an unknown enum, an instant with no offset) keeps the shared type-mismatch body (`Invalid value for parameter '…'`, no `errors` array) and never reaches the service.

The page and capped-id count run as one task on `statsQueryExecutor` and share its 6 workers and queue with stats. When the executor is full, the request returns the same **503** with `Retry-After: 2`. Present `configId` can use `idx_se_config_ts`; omit it to scan `idx_se_ts`. `category` and `action` stay unindexed. The count selects `id` and stops after 10_101 rows, so it can use those indexes instead of a full-table `COUNT(*)`.

## Architecture

Layered Spring Boot service: `Controller` → `Service` → `Repository` → PostgreSQL.

Packages: `config`, `controller`, `dto`, `model`, `repository`, `service`, `enrichment`, `exception`, `generator`.

A diagram will be added once ingestion, enrichment, and analytics APIs are in place.

## Storage justification

PostgreSQL is the primary store: relational queries, JPQL aggregations for stats, and composite indexes `(config_id, event_timestamp, id)`, `(event_timestamp, id)`, and `(client_ip, event_timestamp)` keep time-bounded scans and the ingest repeat-offender check efficient at assignment scale. `path` is deliberately **not** indexed — top-path `GROUP BY` runs after the time-range filter. H2 is used for ingest API tests; stats summary and samples pagination are proven against Postgres via Testcontainers.

## Git tags / milestones

| Tag | Meaning |
| --- | --- |
| `v0.0-infrastructure` | Project scaffold, Docker/Postgres, base config |
| `v0.1-ingestion` | `POST /v1/events/ingest` + validation + `receivedAt` |
| `v0.2-enrichment` | Attack classification + `ThreatScoreCalculator` |
| `v0.3-stats` | `GET /v1/stats/summary` (JPQL aggregations) |
| `v0.4-samples` | `GET /v1/events/samples` pagination and filters |
| `v0.5-generator` | Seeded event generator, CLI, demo page, `GET /v1/dev/events` |

## What I’d improve given more time

- Per-event SQL counts for the repeat-offender window, then a Redis sliding window at real scale. History is already one OR-of-windows query; those steps drop the timestamp materialization.
- Partitioning `security_events` by `event_timestamp` cannot keep a global `UNIQUE(event_id)`: Postgres unique keys on partitions must include the partition column, so the same `eventId` could be inserted again at another timestamp and **409** would no longer hold. A retention job would drop old rows and weaken 409 after the retention window. The spec does not ask for either.

## Challenges and how they were solved

_TBD after implementation._

## Bonus ideas

Not in scope for `v0.0-infrastructure`:

- Alerting: `POST /v1/alerts/define`, `GET /v1/alerts/evaluate`
- Time-series: `GET /v1/stats/timeseries`
- Rate limiting on stats/samples APIs (429)
