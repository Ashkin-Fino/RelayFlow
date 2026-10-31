# RelayFlow
AI Workflow Orchestration Platform

## Local development

### Prerequisites

- Java 25
- Maven 3.10 or the included Maven Wrapper
- PostgreSQL 18.6, running locally
- Python 3 for the supplied mock world and verification scripts

### Create the local database

Connect to the local PostgreSQL cluster as an administrator and create a dedicated application role and database. In `psql`, run:

```sql
CREATE ROLE relayflow LOGIN;
\password relayflow
CREATE DATABASE relayflow OWNER relayflow;
```

The `\password` command prompts for the role password. Do not commit that password or put it in source control.

### Configure and start

In PowerShell, set the database values and demo configuration for the current session. Replace the password and token placeholders locally:

```powershell
$env:RELAY_DB_URL = 'jdbc:postgresql://localhost:5432/relayflow'
$env:RELAY_DB_USER = 'relayflow'
$env:RELAY_DB_PASSWORD = '<local role password>'
$env:RELAY_DEMO_TOKEN = '<local demo token>'
$env:MOCK_WORLD_URL = 'http://localhost:9210'
$env:RELAY_AI_ADAPTER = 'mock'
$env:RELAY_AI_BASE_URL = 'http://localhost:9001/v1'
$env:RELAY_AI_MODEL = 'alpha-small'
# Set for a real provider; a future HTTP adapter can use any dummy bearer key with mock_provider.py.
$env:RELAY_AI_API_KEY = '<provider or local mock key>'
.\mvnw.cmd clean package
java -jar target\relayflow-0.0.1-SNAPSHOT.jar
```

The AI settings are currently configuration placeholders. AI node execution and provider calls are not implemented yet. Keep real provider keys in environment variables only. The app applies `src/main/resources/db/migration` through Flyway and inserts the four canonical seed workflows as Published. Seed inserts are conflict-safe and do not overwrite an existing workflow on restart. Set `RELAY_SEED_ENABLED=false` to disable seed insertion. `SERVER_PORT` overrides the default API port `8080`.

Run the mock world in another terminal:

```powershell
python scripts/mock_world.py --port 9210
```

Spring Boot health is available at `http://localhost:8080/actuator/health`.

### Workflow authoring API

The Phase 2 endpoints require `Authorization: Bearer <RELAY_DEMO_TOKEN>`:

- `GET /workflows` lists workflow `id`, `name`, and `status`.
- `POST /workflows` accepts the canonical workflow JSON definition, validates it against the node catalog, and creates a Draft.
- `POST /workflows/{workflowId}/publish` validates the stored definition and publishes it.

Definitions are validated for catalog node types, required parameters, parameter types/enums, trigger configuration, `limits.max_steps`, entry-node existence, and outgoing node pointers. Backward pointers are allowed. Invalid definitions return a structured 4xx error; missing/invalid bearer tokens return 401. The workflow-detail route is not part of this implemented API surface.

### Current implementation status

Phases 1 and 2 are implemented. Phase 3 manual/webhook trigger, PostgreSQL queue, worker execution, durable delay, and max_steps enforcement are implemented and the local scenarios are documented in `dev_report/phase_3.md`. Phase 4 lease recovery and idempotency have passed the reported local crash/restart drills; durable transient retry/backoff is still not implemented. AI execution, approvals, order_action, cancellation, complete run-trace APIs, and the React console remain outstanding. The full smoke-test and duplication-check acceptance results are not yet recorded as passing.


## Worker runtime modes

The worker is enabled by default. The normal JAR command starts API and worker together. 

java -jar target\relayflow-0.0.1-SNAPSHOT.jar --relay.worker.id=worker-m1 --server.port=8080


To run API-only:

```powershell
java -jar target\relayflow-0.0.1-SNAPSHOT.jar --relay.worker.enabled=false
```

To run worker-only (same JAR and same PostgreSQL database):

```powershell
java -jar target\relayflow-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --relay.worker.enabled=true --relay.worker.id=worker-1
```

The worker polls every 1,000 ms by default and uses a 60-second queue lease. These can be configured with RELAY_WORKER_POLL_INTERVAL_MS and RELAY_WORKER_LEASE_SECONDS. SERVER_PORT overrides the API port.

## Phase reports

- [Phase 3 — Trigger, durable queue, and basic execution](dev_report/phase_3.md)
- [Phase 4 — Crash recovery, retries, and idempotency](dev_report/phase_4.md)

Phase 4 is partially complete: lease recovery and idempotency are tested, but bounded transient retry/backoff and retry-exhaustion behavior are not implemented. The queue attempts counter records claims/reclaims and must not be interpreted as a per-node retry counter.
