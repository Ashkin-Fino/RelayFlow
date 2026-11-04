# Relay UI Design

Status: Approved design baseline for the Phase 7 console  
Frontend: React + Vite  
Backend: Java 25 + Spring Boot + PostgreSQL  
Repository layout: frontend application in `frontend/`; backend remains in the existing Spring Boot source tree.

## 1. Purpose and design principles

The Relay console is the user-facing control plane for creating workflow definitions, inspecting workflows, monitoring runs, and acting on approval or cancellation requests.

The frontend is a thin client. The Spring Boot API is the only backend interface used by the browser and remains the source of truth for workflow definitions, publication state, runs, traces, approvals, and execution state.

The frontend does not connect to PostgreSQL, the worker process, the mock world, or model providers directly.

Design principles:

- Keep the UI operational and clear rather than adding unnecessary visual complexity.
- Use a mixed visual style: dark navy navigation panel on the right, light main content area, blue primary actions, and restrained semantic status colors.
- Use the canonical workflow JSON as the data model. The graph editor is a visual representation and editor for that same definition, not a separate workflow format.
- Published definitions are read-only in the initial console. A user creates and edits a Draft, then saves it through the API.
- Preserve the backend API contract and status vocabulary. UI labels may be more human-readable, but API values must not be renamed.
- Keep the frontend lightweight and avoid a global state-management dependency unless later complexity demonstrates a need.

## 2. Technology decisions

| Concern | Decision |
|---|---|
| UI framework | React |
| Build tool and dev server | Vite |
| Language | JavaScript (JSX) |
| Routing | React Router |
| Workflow graph | `@xyflow/react` |
| Icons | `lucide-react` |
| API communication | Native Fetch API |
| Styling | CSS (component/page styles; no UI framework required initially) |
| Backend | Existing Java + Spring Boot API |
| Persistence | PostgreSQL, accessed only by Spring Boot |

Do not add Redux, a second backend-for-frontend, or a frontend database for this phase.

## 3. Application navigation and information architecture

There are exactly four primary navigation items, displayed in a vertical navigation panel on the **right side** of the application shell:

1. **Overview** — homepage and operational summary.
2. **Workflows** — workflow catalogue and workflow detail pages.
3. **Runs** — run history, filters, and run trace pages.
4. **Workflow Builder** — create and save workflow definitions as Drafts.

The right-side navigation remains available across the main application pages. Selecting a workflow or run opens its detail page within the relevant section; it does not create another primary navigation item.

### 3.1 Overview (homepage)

The Overview is the default route after the application loads.

It should provide a concise operational snapshot:

- Total workflows, with Draft and Published counts.
- Run counts grouped by lifecycle status.
- Runs currently waiting for approval.
- Recent runs table/list with workflow, run ID, status, and start time. Retrieve these directly from the global `GET /runs` endpoint; the Overview page does not need to fetch every workflow first or aggregate workflow-scoped run lists.
- Quick links to the Workflows, Runs, and Workflow Builder sections.

The page obtains its data from the workflow and run APIs. It must not query the database or worker directly.

### 3.2 Workflows

The Workflows page displays all workflows in a table.

Suggested columns:

- Workflow name
- Workflow ID
- Trigger type
- Status
- Last updated
- Actions / open details

Filters:

- All
- Draft
- Published

Selecting a row opens that workflow's detail page.

#### Workflow detail page

Display workflow metadata and provide two view tabs:

- **JSON** — formatted view of the stored canonical workflow definition.
- **Flowchart** — graph view of the same definition using React Flow.

These are two representations of one definition. Switching tabs must not silently change the workflow.

The detail page includes a **View Runs** action. It navigates to the Runs section with the selected workflow ID already applied as a filter.

For Draft workflows, provide the relevant edit/save and publish actions supported by the backend. Published workflows are read-only in this initial design. Do not implement editing a published definition in place.

### 3.3 Runs

The Runs page displays runs in a table.

Suggested columns:

- Run ID
- Workflow name / Workflow ID
- Trigger type
- Status
- Current node (when available)
- Started at
- Finished at
- Actions / open trace

Filters:

- Workflow ID
- Pending approval (maps to `waiting_approval`)
- `queued`
- `running`
- `waiting_approval`
- `cancelled`

The UI may also offer All statuses. The backend's complete status vocabulary additionally includes `succeeded` and `failed`; these must remain visible in the unfiltered list and may be added as explicit filters.

Clicking a run opens its Run Trace page.

#### Run Trace page

Show the run summary and an ordered execution trace.

For each step, display the available trace data:

- Node ID and node type
- Step status
- Sequence and attempt
- Resolved input
- Output
- Start time and duration
- Error details, if any
- AI token usage, when applicable
- Idempotency key, when present and safe to display

Run-level actions:

- **Cancel** — shown only while cancellation is meaningful. Call the run cancellation API and refresh the run state. Handle a 409 response for terminal runs without presenting the action as successful.
- **Approve / Reject** — shown on the approval step when its approval is still pending. Use the approval ID associated with that step and call the corresponding approval API. On success, refresh the trace and run state.

Approval actions live inside the Run Trace page; there is no separate Approvals navigation tab.

### 3.4 Workflow Builder

The Workflow Builder is a dedicated page for creating new workflow definitions and saving them as Drafts.

The builder uses a visual node canvas backed by React Flow. It must support the canonical workflow definition:

- Workflow metadata: ID (generated or assigned according to backend rules), name, and trigger configuration.
- Entry node.
- Workflow limits.
- Nodes with ID, type, params, and outgoing connections.
- Standard `next` connections and condition `on_true` / `on_false` branches.

The node palette and parameter forms must be driven by the canonical `data/node_catalog.json` contract. Do not maintain a second independent list of node types or required parameters in the frontend.

The builder should allow the user to:

1. Add a node from the supported node catalogue.
2. Configure its parameters.
3. Connect nodes and define condition branches.
4. Choose the entry node.
5. Validate basic graph completeness in the UI for helpful feedback.
6. Save the definition using `POST /workflows`, creating a Draft.
7. Navigate to the newly created workflow detail page after a successful save.

The backend remains authoritative for validation. A successful frontend-only validation must never be treated as proof that the backend will publish the definition.

Publishing is a separate lifecycle action; saving in the builder creates a Draft and does not automatically publish it.

## 4. Frontend/backend boundary

### 4.1 Responsibility split

| Responsibility | Frontend (React) | Spring Boot API | Worker |
|---|---|---|---|
| Render pages, tables, filters, graph and JSON | Yes | No | No |
| Collect and submit workflow definitions | Yes | Validate, persist | No |
| Publish workflow | Initiates request | Validate and publish | No |
| Trigger a run | Initiates request | Persist run, snapshot and queue job | Executes asynchronously |
| Execute workflow nodes | No | No | Yes |
| Persist run and step state | No | Owns persistence and exposes API | Persists execution progress through backend services |
| Display run trace | Renders response | Reads and returns trace | Produces trace data |
| Approve/reject | Initiates request | Persists decision and updates run/queue state | Resumes or terminates execution |
| Cancel run | Initiates request | Persists cancellation request/state | Observes cancellation and stops at a safe boundary |

**There is no direct frontend-to-worker interaction.** The browser communicates only with the Spring Boot API. The API and worker are separate runtime modes/processes of the same backend application and coordinate through PostgreSQL and the durable queue. The worker does not expose a browser-facing API for the console.

The frontend must never call PostgreSQL, the worker process, the mock-world service, or the AI provider directly.

### 4.2 API base URL and authentication

Configure the API base URL using a Vite environment variable:

```env
VITE_API_BASE_URL=http://localhost:8080
```

All platform API calls use:

```http
Authorization: Bearer <demo-token>
Content-Type: application/json
```

The demo token must not be hard-coded into committed source files. For local development, provide it through a local untracked environment/configuration mechanism or a simple runtime sign-in/configuration screen. Never expose provider API keys or webhook secrets in frontend source or logs.

Webhook callers are external systems, not the browser console. The browser does not need to call the webhook endpoint as part of the initial UI.

### 4.3 API routes used by the UI

The following routes are the frontend integration contract. Fixed routes and response requirements are defined in `docs/API_CONTRACT.md`. Additional routes are documented as project extensions.

| UI use case | Method and route | Frontend behavior |
|---|---|---|
| List workflows | `GET /workflows` | Populate workflow table and status filters |
| Create Draft | `POST /workflows` | Submit canonical workflow definition; expect created ID and Draft status |
| Workflow detail | `GET /workflows/{workflowId}` | Project extension; load metadata and stored definition |
| Publish Draft | `POST /workflows/{workflowId}/publish` | Publish action; show backend validation errors |
| Trigger workflow | `POST /workflows/{workflowId}/trigger` | Optional run action; body is `{"input": {...}}` |
| List all runs / recent runs | `GET /runs` | Project extension; returns all run summaries newest first using the same JSON array and `RunSummary` fields as the workflow-scoped endpoint; used by Overview and the unfiltered Runs page |
| List runs for workflow | `GET /runs?workflowId={workflowId}` | Project extension; returns the same JSON array shape filtered to the requested workflow |
| Run trace | `GET /runs/{runId}` | Load run summary and ordered steps |
| Pending approvals | `GET /approvals?status=pending` | Used to associate pending approval records with run traces and approval state |
| Approve | `POST /approvals/{approvalId}/approve` | Approve the pending approval |
| Reject | `POST /approvals/{approvalId}/reject` | Reject the pending approval |
| Cancel run | `POST /runs/{runId}/cancel` | Request run cancellation |

The workflow detail and run-list routes are project extensions beyond the minimum fixed smoke-test contract. Their implementation and response fields must remain aligned with the backend DTOs and `docs/API_CONTRACT.md`.

### 4.4 Run list retrieval and filtering

The run-list API supports both global and workflow-scoped retrieval:

- `GET /runs` — returns all run summaries, newest first.
- `GET /runs?workflowId={workflowId}` — returns run summaries for the specified workflow, newest first.

Both routes return the **same JSON array structure** and the same `RunSummary` fields. The only difference is the database filter applied. The `workflowId` query parameter is optional; omitting it selects all runs. Supplying an unknown workflow ID retains the existing 404 behavior. The global endpoint does not introduce a pagination envelope or rename/add/remove response fields.

The frontend should use the global endpoint directly for the Overview recent-runs section and the unfiltered Runs page. This is simpler and avoids fetching the workflow list, making one run request per workflow, and merging/sorting the results in the browser. When a workflow filter is selected, call the same endpoint with `?workflowId=...`.

The “Pending approval” filter is a presentation filter for runs whose status is `waiting_approval`. Approval details and actionable approval IDs are obtained from the approval/run trace APIs; do not infer an approval ID from a run ID.

### 4.5 Response and error handling

- Treat API responses as authoritative; refresh affected data after mutations.
- Preserve backend status strings exactly: `queued`, `running`, `waiting_approval`, `succeeded`, `failed`, `cancelled`.
- Display structured errors from `{"error":{"message":"...","code":"..."}}` in the relevant page or action context.
- Handle 401 by showing an authentication/configuration error.
- Handle 404 for deleted or unknown workflow/run/approval references.
- Handle 409 for already-decided approvals or cancellation of terminal runs.
- Show loading, empty, success, and failure states for all data-driven pages.
- Do not optimistically show a workflow as published, a run as cancelled, or an approval as decided before the API confirms success.

## 5. Final frontend folder structure

Create the frontend as a separate Vite application in the repository root:

```text
RelayFlow/
├── frontend/
│   ├── index.html
│   ├── package.json
│   ├── vite.config.js
│   ├── .env.example
│   └── src/
│       ├── api/
│       │   ├── client.js
│       │   ├── workflowApi.js
│       │   ├── runApi.js
│       │   └── approvalApi.js
│       ├── components/
│       │   ├── layout/
│       │   │   ├── AppShell.jsx
│       │   │   └── RightNavigation.jsx
│       │   ├── common/
│       │   │   ├── StatusBadge.jsx
│       │   │   ├── DataTable.jsx
│       │   │   ├── LoadingState.jsx
│       │   │   └── ErrorState.jsx
│       │   ├── workflows/
│       │   │   ├── WorkflowTable.jsx
│       │   │   ├── WorkflowFilters.jsx
│       │   │   ├── WorkflowJsonView.jsx
│       │   │   └── WorkflowGraphView.jsx
│       │   ├── builder/
│       │   │   ├── WorkflowCanvas.jsx
│       │   │   ├── NodePalette.jsx
│       │   │   ├── NodeConfigPanel.jsx
│       │   │   └── WorkflowSettings.jsx
│       │   └── runs/
│       │       ├── RunsTable.jsx
│       │       ├── RunFilters.jsx
│       │       ├── RunSummary.jsx
│       │       ├── RunTrace.jsx
│       │       └── ApprovalActions.jsx
│       ├── pages/
│       │   ├── OverviewPage.jsx
│       │   ├── WorkflowsPage.jsx
│       │   ├── WorkflowDetailPage.jsx
│       │   ├── WorkflowBuilderPage.jsx
│       │   ├── RunsPage.jsx
│       │   └── RunTracePage.jsx
│       ├── hooks/
│       ├── utils/
│       │   ├── workflowDefinition.js
│       │   ├── workflowGraphMapper.js
│       │   └── status.js
│       ├── App.jsx
│       ├── main.jsx
│       └── styles/
│           ├── global.css
│           ├── layout.css
│           └── components.css
└── (existing Spring Boot backend, data/, scripts/, docs/)
```

Keep API request logic in `src/api/`; page components should not duplicate fetch/auth/error-handling logic. Keep graph-to-definition conversion in `workflowGraphMapper.js` and test it independently.

## 6. Route map

Suggested browser routes:

| Route | Page |
|---|---|
| `/` | Overview |
| `/workflows` | Workflow list |
| `/workflows/new` | Workflow Builder |
| `/workflows/:workflowId` | Workflow detail (JSON / Flowchart tabs) |
| `/runs` | Runs table and filters |
| `/runs/:runId` | Run Trace |

The workflow-specific run filter should be represented in URL query state, for example:

```text
/runs?workflowId=wf_expense_approval
```

This makes the View Runs navigation linkable and preserves the filter on refresh.

## 7. Implementation boundaries and acceptance checks

- React + Vite is the chosen frontend stack; do not substitute another framework.
- The four primary navigation items are Overview, Workflows, Runs, and Workflow Builder.
- Overview is the homepage.
- Workflow list supports Draft/Published filters and opens a workflow detail page.
- Workflow detail has JSON and Flowchart tabs and a View Runs action.
- Runs has workflow and status filters, opens run traces, and supports cancellation.
- Approval actions are available on a pending approval step in the run trace; there is no separate Approvals navigation item.
- Workflow Builder creates a canonical definition and saves it as Draft through the API.
- The graph and JSON views represent the same canonical definition.
- All browser/backend communication goes through the Spring Boot API; there is no frontend/worker connection.
- The frontend does not bypass backend validation or approval enforcement.
- No secrets are committed to the repository.
- Existing backend API and worker behavior, database schema, smoke scripts, and mock-world contracts are not changed merely to accommodate UI presentation.
