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
# Supply a provider key (or a non-empty local mock key) through the environment only.
$env:RELAY_AI_API_KEY = '<provider or local mock key>'
$env:RELAY_AI_TIMEOUT_SECONDS = '30'
.\mvnw.cmd clean package
java -jar target\relayflow-0.0.1-SNAPSHOT.jar
```

AI nodes use an OpenAI-compatible chat-completions endpoint at `<RELAY_AI_BASE_URL>/chat/completions`. The configured model must return JSON in the assistant message. Provider calls have a configurable timeout, and credentials are sent only as bearer authentication; prompts and keys are not written to logs or traces. `RELAY_AI_ADAPTER` accepts `mock` or `real`; both use the same HTTP protocol and differ only by the endpoint/key you configure. Keep real provider keys in environment variables only. The app applies `src/main/resources/db/migration` through Flyway and inserts the four canonical seed workflows as Published. Seed inserts are conflict-safe and do not overwrite an existing workflow on restart. Set `RELAY_SEED_ENABLED=false` to disable seed insertion. `SERVER_PORT` overrides the default API port `8080`.

Run the mock world in another terminal:

```powershell
python scripts/mock_world.py --port 9210
```

Spring Boot health is available at `http://localhost:8080/actuator/health`.

### Workflow authoring API

These endpoints require `Authorization: Bearer <RELAY_DEMO_TOKEN>`:

- `GET /workflows` lists workflow `id`, `name`, and `status`.
- `POST /workflows` accepts the canonical workflow JSON definition, validates it against the node catalog, and creates a Draft.
- `POST /workflows/{workflowId}/publish` validates the stored definition and publishes it.

Definitions are validated for catalog node types, required parameters, parameter types/enums, trigger configuration, `limits.max_steps`, entry-node existence, and outgoing node pointers. Backward pointers are allowed. Invalid definitions return a structured 4xx error; missing/invalid bearer tokens return 401. The workflow-detail route is not part of this implemented API surface.

### Worklfow Triggering API

These endpoints require `Authorization: Bearer <RELAY_DEMO_TOKEN>`:

- `POST /workflows/{workflowId}/trigger` triggers a published workflow which support manual trigger
- `POST /hooks/{workflowId}` triggers a published which supports webhook trigger. Also requires a `X-Relay-Secret` header.

### Current implementation status

Phases 1–4 are implemented; Phase 4 lease recovery, retry/backoff, and idempotency status is documented in [dev_report/phase_4.md](dev_report/phase_4.md). Phase 5 adds nested template resolution, AI provider calls, JSON Schema validation with exactly one repair call, and persisted prompt/completion token usage. See [dev_report/phae_5.md](dev_report/phae_5.md) for implementation and verification details.

Approval-node lifecycle and APIs, cancellation, run-trace APIs, the console, and end-to-end live AI/injection verification remain outstanding. Sensitive `requires_approval` nodes are blocked by the worker unless an earlier successful approval step explicitly records `decision: approved`; full approval workflow support is Phase 6. The full smoke-test and duplication-check acceptance results are not yet recorded as passing.


## Worker runtime modes

The worker is enabled by default. The normal JAR command starts API and worker together. 

```cmd
java -jar target\relayflow-0.0.1-SNAPSHOT.jar --relay.worker.id=worker-m1 --server.port=8080
```


To run API-only:

```cmd
java -jar target\relayflow-0.0.1-SNAPSHOT.jar --relay.worker.enabled=false
```

To run worker-only (same JAR and same PostgreSQL database):

```cmd
java -jar target\relayflow-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --relay.worker.enabled=true --relay.worker.id=worker-1
```

The worker polls every 1,000 ms by default and uses a 60-second queue lease. These can be configured with RELAY_WORKER_POLL_INTERVAL_MS and RELAY_WORKER_LEASE_SECONDS. SERVER_PORT overrides the API port.

Per-node retries are configured with `RELAY_WORKER_RETRY_MAX_ATTEMPTS` (default 3 total attempts), `RELAY_WORKER_RETRY_INITIAL_DELAY_SECONDS` (default 2), and `RELAY_WORKER_RETRY_MAX_DELAY_SECONDS` (default 30). Backoff doubles after each failed attempt and is stored using the PostgreSQL queue availability timestamp.
