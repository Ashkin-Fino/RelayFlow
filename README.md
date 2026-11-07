# RelayFlow

RelayFlow is a workflow orchestration system. Define workflows as connected
nodes, publish them, and start runs manually or through webhooks. Runs and their
step-by-step traces are stored in PostgreSQL. Workflows can include conditions,
delays, AI responses, notifications, HTTP requests, approvals, and order
actions. Sensitive order actions require an approval earlier in the run.

The web console provides workflow authoring and operations views. A workflow
can be inspected as JSON or a flowchart; run traces show resolved step inputs,
outputs, and errors.

## What you can do

- Create, edit, validate, and publish Draft workflows in the visual builder.
- Trigger published workflows manually or through a secret-protected webhook.
- View workflow runs, their status, and detailed step traces.
- Approve or reject pending approval steps, and cancel eligible runs.
- Use `{{trigger.body...}}` and `{{nodes.<id>.output...}}` templates to pass
  data between a trigger and workflow nodes.
- Configure AI nodes to use an OpenAI-compatible provider.

## Console navigation

After opening the console, enter the demo bearer token configured for the API.
The token is kept in the current browser session.

- **Overview** — summary of workflows and recent runs.
- **Workflows** — list and filter workflows, inspect their JSON or flowchart,
  view a sample trigger payload, and manually trigger eligible published
  workflows. Drafts can be edited or published here.
- **Workflow Builder** — create a workflow by dragging nodes onto the canvas,
  connecting nodes, and editing their parameters. Workflows are saved as Drafts
  until published.
- **Runs** — list runs and filter by workflow or status. Open a run to inspect
  its trace, handle pending approvals, or cancel it where allowed.

Use **Change API token** in the navigation rail to enter a different token.

## HTTP API

API endpoints other than the actuator health endpoints require:

```http
Authorization: Bearer <RELAY_DEMO_TOKEN>
```

### Workflows

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/workflows` | List workflows |
| `GET` | `/workflows/{workflowId}` | Get workflow metadata and definition |
| `POST` | `/workflows` | Create a workflow as a Draft |
| `PUT` | `/workflows/{workflowId}` | Update an existing Draft |
| `POST` | `/workflows/{workflowId}/publish` | Validate and publish a Draft |

### Runs and triggers

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/workflows/{workflowId}/trigger` | Start a published manual-trigger workflow |
| `POST` | `/hooks/{workflowId}` | Start a published webhook-trigger workflow |
| `GET` | `/runs` | List runs; optionally filter with `workflowId` and/or `status` |
| `GET` | `/runs/{runId}` | Get a run and its ordered step trace |
| `POST` | `/runs/{runId}/cancel` | Cancel a run when its state allows cancellation |

Manual-trigger requests have an object-valued `input` field. The `input` object
becomes `{{trigger.body}}`:

```json
{
  "input": {
    "order_id": "ord_2001",
    "customer_email": "maya@example.com"
  }
}
```

Webhook requests send the JSON object directly as the request body and must
include the workflow's secret:

```http
X-Relay-Secret: <workflow webhook secret>
Content-Type: application/json
```

The API responds with `202 Accepted` and a `run_id` when a run is queued.

### Approvals

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/approvals?status=pending` | List approvals (default status is `pending`) |
| `POST` | `/approvals/{approvalId}/approve` | Approve a pending step |
| `POST` | `/approvals/{approvalId}/reject` | Reject a pending step; optional JSON body: `{"reason":"..."}` |

Other supported approval statuses are `approved`, `rejected`, and `cancelled`.
Supported run statuses are `queued`, `running`, `waiting_approval`, `succeeded`,
`failed`, and `cancelled`.

### Health

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/actuator/health` | Spring Boot health check |

## Run with Docker Compose

The `Dockerfile`, `compose.yaml`, and `docker/` files are included in this
GitHub repository. You must first get the repository files onto your computer. Choose one option:

**Option A — clone the repository** (requires Git):

```powershell
git clone https://github.com/Ashkin-Fino/RelayFlow.git
Set-Location RelayFlow
```

**Option B — download a ZIP:** on the GitHub repository page, select **Code →
Download ZIP**, extract the archive, then open PowerShell in the extracted
folder (often named `RelayFlow-main`). This folder should contain `compose.yaml`
and `docker.env.example`.

Install Docker Desktop with Docker Compose v2. From that repository folder,
create the local environment file and replace the example database password
and demo token:

```powershell
Copy-Item docker.env.example docker.env
# Edit docker.env and replace the example credentials.
docker compose --env-file docker.env up --build -d
docker compose --env-file docker.env ps
```

Open the console at [http://localhost:8081](http://localhost:8081). It sends
API requests through Nginx's same-origin `/api/` proxy. The API is also
available directly at [http://localhost:8082](http://localhost:8082). The
frontend and API ports bind to the local machine by default.

Compose starts PostgreSQL, the mock world, the mock AI provider, the Spring
Boot API with its workflow worker, and the Nginx frontend. The backend applies
database migrations on startup. PostgreSQL data persists in the named volume;
the mock world's in-memory order and ledger data resets when its container is
recreated.

To stop the containers while preserving database data:

```powershell
docker compose --env-file docker.env down
```

To also delete the database volume and all stored RelayFlow data:

```powershell
docker compose --env-file docker.env down --volumes
```

The bundled mock AI provider is useful for connectivity checks, but returns
generic text rather than schema-shaped output. For AI workflows that require
structured responses, set `RELAY_AI_BASE_URL`, `RELAY_AI_API_KEY`, and
`RELAY_AI_MODEL` in `docker.env` for an OpenAI-compatible provider. Keep secrets
out of source control. Do not expose this demo configuration directly to the
internet.

## Run locally from a GitHub ZIP

On the repository's GitHub page, select **Code → Download ZIP**, then extract
the archive. Open PowerShell in the extracted repository folder (often named
`RelayFlow-main`).

### Prerequisites

- Java 25 JDK
- PostgreSQL (the application uses Flyway to create its schema)
- Python 3
- Node.js 24 and npm

### 1. Create a PostgreSQL role and database

Start PostgreSQL, connect with `psql` as an administrator, then run:

```sql
CREATE ROLE relayflow LOGIN;
\password relayflow
CREATE DATABASE relayflow OWNER relayflow;
```

When prompted, enter a local password and keep it private.

### 2. Start the mock services

Open two PowerShell terminals in the extracted repository folder:

```powershell
python scripts/mock_world.py --port 9210
```

```powershell
python scripts/mock_provider.py --port 9001 --name alpha
```

The mock world provides test endpoints for notifications and order actions. The
mock provider exposes an OpenAI-compatible API for AI nodes.

### 3. Configure and start the backend

In another PowerShell terminal, set environment variables for that terminal
session. Replace the password and token values:

```powershell
$env:RELAY_DB_URL = 'jdbc:postgresql://localhost:5432/relayflow'
$env:RELAY_DB_USER = 'relayflow'
$env:RELAY_DB_PASSWORD = '<the password you created>'
$env:RELAY_DEMO_TOKEN = '<choose a private local token>'
$env:MOCK_WORLD_URL = 'http://localhost:9210'
$env:RELAY_AI_BASE_URL = 'http://localhost:9001/v1'
$env:RELAY_AI_MODEL = 'alpha-small'
$env:RELAY_AI_API_KEY = 'local-mock-key'

.\mvnw.cmd clean package
java -jar target\relayflow-1.0.0-SNAPSHOT.jar
```

The API is available at [http://localhost:8080](http://localhost:8080); its
health endpoint is [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).
The worker runs in the same process and polls PostgreSQL for queued work.

### 4. Start the console

Open a fourth PowerShell terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open the Vite URL printed in the terminal, usually
[http://localhost:5173](http://localhost:5173), and enter the same
`RELAY_DEMO_TOKEN`. During development, Vite forwards `/api` requests to the
backend at `http://localhost:8080`.

For AI nodes using a real OpenAI-compatible provider, set `RELAY_AI_BASE_URL`,
`RELAY_AI_API_KEY`, and `RELAY_AI_MODEL` in the backend terminal before
starting the backend. Do not put provider credentials in frontend environment
files.
