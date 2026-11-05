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

AI nodes use an OpenAI-compatible chat-completions endpoint at `<RELAY_AI_BASE_URL>/chat/completions`. For OpenRouter, set `RELAY_AI_BASE_URL=https://openrouter.ai/api/v1`, `RELAY_AI_API_KEY` to your OpenRouter key, and `RELAY_AI_MODEL` to an exact model ID available to your OpenRouter account (for example, `provider/model`). The selected model/provider must support the requested JSON response format and return JSON in the assistant message. Provider calls have a configurable timeout, and credentials are sent only as bearer authentication; prompts and keys are not written to logs or traces. The same HTTP client handles OpenAI-compatible services; no OpenRouter-specific client is required. `RELAY_AI_ADAPTER` is a configuration label and does not currently change the HTTP client behavior. Keep real provider keys in environment variables or the untracked local `keys.env` file only. The app applies `src/main/resources/db/migration` through Flyway and inserts the four canonical seed workflows as Published. Seed inserts are conflict-safe and do not overwrite an existing workflow on restart. Set `RELAY_SEED_ENABLED=false` to disable seed insertion. `SERVER_PORT` overrides the default API port `8080`.

Run the mock world in another terminal:

```powershell
python scripts/mock_world.py --port 9210
```

Spring Boot health is available at `http://localhost:8080/actuator/health`.

### Workflow authoring API

These endpoints require `Authorization: Bearer <RELAY_DEMO_TOKEN>`:

- `GET /workflows` lists workflow `id`, `name`, and `status`.
- `GET /workflows/{workflowId}` returns workflow metadata, timestamps, and the full definition.
- `POST /workflows` accepts the canonical workflow JSON definition, validates it against the node catalog, and creates a Draft.
- `PUT /workflows/{workflowId}` replaces an existing Draft definition. The path ID and definition ID must match; published workflows cannot be edited.
- `POST /workflows/{workflowId}/publish` validates the stored definition and publishes it.

Definitions are validated for catalog node types, required parameters, parameter types/enums, trigger configuration, `limits.max_steps`, entry-node existence, and outgoing node pointers. Backward pointers are allowed. Invalid definitions return a structured 4xx error; missing/invalid bearer tokens return 401.

### Worklfow Triggering API

These endpoints require `Authorization: Bearer <RELAY_DEMO_TOKEN>`:

- `POST /workflows/{workflowId}/trigger` triggers a published workflow which support manual trigger
- `POST /hooks/{workflowId}` triggers a published which supports webhook trigger. Also requires a `X-Relay-Secret` header.
- `GET /runs` lists all runs, newest first. Optional `workflowId` and `status` filters can be used separately or together (for example, `?status=failed`); supported statuses are `queued`, `running`, `waiting_approval`, `succeeded`, `failed`, and `cancelled`. Blank or unsupported filters return 400, and an unknown workflow returns 404.
- `GET /runs/{runId}` returns run state and ordered step trace details.

### Approval and cancellation API

- `GET /approvals?status=pending` lists pending approval requests. Other supported statuses are `approved`, `rejected`, and `cancelled`.
- `POST /approvals/{approvalId}/approve` records the decision and resumes the run.
- `POST /approvals/{approvalId}/reject` records the rejection and cancels the run. An optional JSON body may include a `reason`.
- `POST /runs/{runId}/cancel` cancels a queued or approval-waiting run immediately, or requests cooperative cancellation of a running run. A running node is allowed to finish before the worker stops the run. Cancelling a terminal run returns `409`.

### Console

The React/Vite console is in `frontend/`. Start it separately from the backend:

```powershell
Set-Location frontend
npm install
npm run dev
```

The console uses `http://localhost:8080` by default; set `VITE_API_BASE_URL` before starting Vite to use a different API URL. Enter the configured demo bearer token in the console. The backend's `RELAY_FRONTEND_ORIGIN` setting controls allowed browser origins.

The console includes Overview, Workflows, and Runs pages; workflow JSON and flowchart details; run traces with guarded approval and cancellation actions; and a catalog-driven workflow builder. New workflows are saved as Drafts. Drafts can be edited and published from the console; published definitions remain read-only. Schedule triggers use validated five-field cron expressions and create normal queued runs on their scheduled minute. Cron times are interpreted in UTC; missed occurrences are not replayed.

### Current implementation status

Phases 0–7 are implemented. Phase 7 includes the run trace and listing APIs, workflow details, and the console described above. The full end-to-end acceptance run remains outstanding: run `scripts/smoke_test.py` unchanged and run `scripts/duplication_check.py` after a worker kill/resume drill. Real-provider testing with OpenRouter and prompt-injection testing were confirmed complete; see [dev_report/phase_5.md](dev_report/phase_5.md) and [dev_report/phase_7.md](dev_report/phase_7.md).

Sensitive `requires_approval` nodes are blocked by the worker unless an earlier successful approval step explicitly records `decision: approved`. Approval, rejection, and cancellation transitions are persisted in PostgreSQL and serialized against conflicting decisions.


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

The worker polls every 1,000 ms by default and uses a 60-second queue lease. These can be configured with RELAY_WORKER_POLL_INTERVAL_MS and RELAY_WORKER_LEASE_SECONDS. The scheduler checks for due cron triggers every 1,000 ms by default; configure this with RELAY_SCHEDULE_POLL_INTERVAL_MS. The scheduled tasks use a two-thread pool so workflow execution does not block cron dispatch. SERVER_PORT overrides the API port.

Per-node retries are configured with `RELAY_WORKER_RETRY_MAX_ATTEMPTS` (default 3 total attempts), `RELAY_WORKER_RETRY_INITIAL_DELAY_SECONDS` (default 2), and `RELAY_WORKER_RETRY_MAX_DELAY_SECONDS` (default 30). Backoff doubles after each failed attempt and is stored using the PostgreSQL queue availability timestamp.
