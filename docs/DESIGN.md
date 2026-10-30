# Relay AI Workflow Orchestrator — Design Document

Status: Local-first design baseline
Implementation choice: Java 25 + Spring Boot 4.1.2 + PostgreSQL 18.6 + Maven 3.10, with a lightweight console in the same project or a minimal React + Vite UI if needed for demos. The backend remains the source of truth; the console is only a thin presentation layer.
Scope sources: `docs/RELAY_PROBLEM_STATEMENT.md`, `docs/API_CONTRACT.md`, `docs/DATA_MODEL.md`, `docs/IMPLEMENTATION_GUIDE.md`, `data/node_catalog.json`, `data/seed_workflows.json`, `data/sample_payloads.jsonl`, and the supplied Python verification scripts.

## 1. Purpose and scope

Relay is a durable workflow orchestration platform. Builders define workflows as JSON graphs of typed nodes, validate and publish them, and trigger runs manually or through secret-protected webhooks. A worker executes nodes asynchronously, persists traces, pauses for human approval, and resumes safely after a process crash. AI can classify or structure input, but AI output cannot bypass engine-enforced validation or approval.

This design is intentionally local-first: the mock world and the Python verification scripts remain unchanged, and AWS deployment is deferred until local reliability passes. The platform must satisfy the fixed contract used by `scripts/smoke_test.py` and the exact-once verification used by `scripts/duplication_check.py`.

### Goals
- Meet every fixed route, JSON shape, status vocabulary, and behavior in the API contract.
- Load the catalog and the four seed workflows as Published at startup.
- Make workflow creation and publishing deterministic, safe, and repeatable.
- Execute workflows asynchronously via a durable PostgreSQL-backed queue.
- Preserve snapshots, steps, approvals, and queue state across process restarts.
- Make side effects replay-safe using stable idempotency keys.
- Enforce approval gates in engine logic instead of trusting model output.
- Provide a console for workflow listing, approval handling, and run tracing.
- Keep configuration portable for a future AWS deployment without provisioning AWS now.

### Out of scope for the initial Must Have
- AWS hosting.
- Enterprise identity, multi-tenancy, and role isolation beyond a shared demo token.
- Multi-worker cluster orchestration; start with one worker and use leases/locking patterns.
- Natural-language compiler, cron schedules, AI token caps, and wall-clock timeouts as Good To Have.
- Exactly-once guarantees for arbitrary external systems that do not support idempotency semantics.

## 2. Design decisions

### 2.1 Runtime model

The platform will use a single Spring Boot backend with two runtime modes:
- API mode: handles workflow CRUD, trigger requests, approval endpoints, and run queries.
- Worker mode: claims queued jobs from PostgreSQL, executes or resumes the workflow engine, and persists step progress.

This matches the repo contract: triggers enqueue immediately and return promptly; execution never happens in the HTTP request handler.

### 2.2 Data model

PostgreSQL is the source of truth for every stateful domain object:
- Workflow definitions and publication state.
- Run records and immutable definition snapshots.
- Step traces and per-node outcomes.
- Approval records and decisions.
- Queue jobs and claim metadata.

The database is also the queue backing store. Each job is durable and recoverable; a dead worker leaves jobs available for recovery via lease expiry or heartbeat.

Workflow definitions use Spring Data JPA entities and repositories, with `workflow.service.WorkflowService` owning create/list/publish use cases. The workflow entity and repository live in `workflow.entity` and `workflow.repository`; API/domain errors live in `workflow.exceptions`. Keep persistence classes inside the owning feature package rather than placing all domain persistence in a global bucket. Queue claiming may use a narrowly scoped JDBC adapter later where PostgreSQL locking features such as `FOR UPDATE SKIP LOCKED` are needed.

### 2.3 Seed and validation principles

At startup the service loads `data/node_catalog.json` and `data/seed_workflows.json` and ensures the seed workflows are present and Published.

Validation is strict and centralized:
- node type must exist in the catalog
- all required params must be present
- enum members must match the catalog
- `entry` and all non-null outgoing pointers must resolve to existing nodes
- trigger configuration must match allowed trigger types
- loops are allowed; runtime `max_steps` bounds them
- invalid definitions fail at create or publish time with 4xx and a clear error payload

### 2.4 Execution semantics

The engine executes runs from a persisted snapshot captured at trigger time. It checks:
- `queued` or `running` state transitions
- `max_steps` guard before each node
- cancellation between nodes
- approval evidence before `requires_approval: true` actions
- per-node retry/backoff for transient failures
- idempotency key reuse for replay-safe side effects

The engine stores each completed step before moving to the next node. On restart it resumes from the persisted position and skips completed steps.

## 3. Canonical workflow contract

The system must preserve the canonical definition shape expressed in `data/seed_workflows.json`:

```json
{
  "id": "wf_example",
  "name": "Example workflow",
  "trigger": { "type": "manual" },
  "entry": "hello",
  "limits": { "max_steps": 5, "timeout_seconds": 60, "max_ai_tokens": 0 },
  "nodes": [
    {
      "id": "hello",
      "type": "notify",
      "params": {
        "channel": "chat",
        "to": "#ops",
        "message": "Hello"
      },
      "next": null
    }
  ]
}
```

The contract fixes the following required behaviors:
- `id`, `name`, `trigger`, `entry`, `limits`, and `nodes[]` are mandatory.
- Every node must contain `id`, `type`, `params`, and either `next` or `on_true`/`on_false` for conditions.
- `trigger.type` may be `manual`, `webhook`, or `schedule`.
- `limits.max_steps` is mandatory; the time and token caps are Good To Have.
- Seed workflows must preserve their IDs and statuses as Published.

## 4. Catalog-driven execution model

The catalog in `data/node_catalog.json` is authoritative for node types, required params, and approval/sensitive-action metadata.

### 4.1 Deterministic nodes

#### `http_request`
- Methods: `GET`, `POST`, `PUT`, `DELETE`
- Supports templated URL, headers, and body
- Mutating requests (non-GET) are side effects and must include an `Idempotency-Key` header
- Captures HTTP status code and body output
- Timeout and retry/backoff logic must be configured per request

#### `condition`
- Supports `equals`, `not_equals`, `greater_than`, `less_than`, `contains`
- `greater_than` and `less_than` compare numerically when both values parse as numbers; otherwise the step must fail clearly
- Routes by `on_true` and `on_false`
- Output is a boolean `result`

#### `delay`
- Persists a pause state and resumes from durable state after worker restart
- A plain in-memory sleep is not acceptable for the Must Have path

#### `notify`
- `email` calls `POST {MOCK_WORLD_URL}/email/send`
- `chat` calls `POST {MOCK_WORLD_URL}/chat/message`
- Must include a stable idempotency key for the same logical node execution
- Returns the mock-world response and saved notification ID

### 4.2 AI node

#### `ai`
- Reads a prompt template, resolves trigger input and earlier node output, and calls the provider adapter
- Validates output against the `output_schema` in the node param
- On schema mismatch, performs one repair attempt with the validation error appended to the prompt
- If it still fails, the step ends as `failed` and traces the reason
- Records token usage in the step trace for visibility and diagnostics

This is required because the seed workflows and the prompt-injection scenario depend on AI classification being reliable and schema-checked before branching.

### 4.3 Sensitive node

#### `approval`
- Creates a persisted approval row and moves the run to `waiting_approval`
- The engine pauses and restarts only when a human approves or rejects
- Decision data includes `decided_by`, `decided_at`, and the final decision
- Repeated decisions are rejected with 409

#### `order_action`
- Handles `refund` and `replacement`
- Requires an earlier approved approval in the same run; this is engine-enforced logic
- Must be prevented on a payload that asks it to act without approval, regardless of model output
- Sends a stable `Idempotency-Key` to the mock world and persists it in the step trace

## 5. Template resolution and data flow

The engine resolves placeholders from earlier outputs and from the trigger body.

Supported examples include:
- `{{trigger.body.amount_usd}}`
- `{{trigger.body.customer_email}}`
- `{{nodes.classify.output.category}}`
- `{{nodes.create_shipment.output.body.shipment_id}}`

Rules:
- Nested property traversal is required.
- Template values may be embedded inside strings or used as whole JSON values.
- Unresolvable values fail the step clearly rather than silently becoming empty strings.
- Template evaluation must be deterministic and consistent across retries and resume paths.

This requirement is critical for workflow safety and for the approval message and condition predicates in the seeded workflows.

## 6. Trigger and run lifecycle

### 6.1 Workflow lifecycle
- New workflow: `draft`
- Publish: `POST /workflows/{id}/publish` validates and changes status to `published`
- Published workflows are frozen for an in-flight or historical run; a run keeps its definition snapshot
- The trigger endpoints never mutate the live definition during execution

### 6.2 Trigger flow
- HTTP trigger validates workflow existence, published state, and webhook secret
- Manual triggers accept `{"input": {...}}` and route input as `{{trigger.body}}`
- The API inserts a new run, a definition snapshot, and a durable queue job in one transaction
- The API responds with a run ID and returns promptly
- The worker executes the run asynchronously

### 6.3 Run states
The required state vocabulary is exactly:
- `queued`
- `running`
- `waiting_approval`
- `succeeded`
- `failed`
- `cancelled`

`failed` is used for time/cap violation, validation failure, or unrecoverable step failure. `cancelled` is used for explicit cancellation or rejected approval.

## 7. Reliability and durability

### 7.1 Queue and leases
The queue job must be durable and recoverable:
- claim with a short transaction using `SELECT ... FOR UPDATE SKIP LOCKED` or an equivalent lock model
- maintain a lease or heartbeat so a dead worker cannot hold a job forever
- if the worker crashes, recovery reclaims the job and resumes from persisted state

### 7.2 Step persistence
The engine persists each node execution before it advances, including:
- node ID and type
- sequence number and attempt count
- resolved input
- output
- duration and failure reason
- AI token usage when applicable
- idempotency key for side-effect nodes

This keeps the state machine reproducible after kill-and-restart drills.

### 7.3 Idempotency and exactly-once behavior
Every side-effect call to the mock world must carry a stable `Idempotency-Key`. The canonical key pattern is:
- `{run_id}:{node_id}:{sequence}`

This key is reused across retries and crash recovery. The retry attempt number is never part of the key.

If a previous request reached the mock world and the worker crashes before persisting the result, the resume path replays the same request with the same key. The mock world absorbs the replay and returns the stored result. If the earlier call never reached the world, the later call executes normally.

The validation script `scripts/duplication_check.py` checks for duplicate successful side effects by action and payload. A clean pass requires zero duplicates and no keyless successful actions.

## 8. Approval and sensitive actions

Approval gating is enforced by the engine, not by the AI prompt or workflow content.

Before any node marked `requires_approval: true` executes, the engine checks whether there is a persisted approval for this run with an approved status prior to the sensitive node. If no approved record exists, the node is blocked and the run cannot continue.

This protects the seeded workflows and the prompt-injection tests, where malicious text inside the trigger body must not bypass approval logic.

## 9. Cancellation semantics

The run API must support explicit cancellation.

Cancellation behavior:
- `queued`: cancel without executing any node
- `running`: stop cooperatively between steps; an in-flight request may finish or fail, but no later node may start after cancellation is observed
- `waiting_approval`: close the pending approval and mark the run cancelled
- `succeeded`, `failed`, or `cancelled`: reject with 409 and do not alter the terminal state

A rejected approval is treated as a cancelled run, so both cancellation paths end in the same terminal state.

## 10. Trace, console, and observability

`GET /runs/{runId}` must return at least the `status` and the step list. Each step should include:
- `node_id`
- `type`
- `status`
- `sequence`
- `attempt`
- `input`
- `output`
- `started_at`
- `duration_ms`
- `error` when applicable
- AI token usage for AI nodes

The console only needs to be a simple operational interface; the correctness of the trace is higher priority than polished UI.

Credentials and secrets must never be logged. That includes bearer tokens, webhook secrets, and provider API keys.

## 11. Security and error behavior

Authentication and error patterns must match the contract:
- platform API: `Authorization: Bearer <demo-token>`
- webhook: `X-Relay-Secret` per workflow
- invalid token: 401
- invalid secret: 401 or 403
- unknown workflow / run / approval: 404
- invalid definition: 400 or 422 with an `error.message` and `error.code`
- trigger of unpublished workflow: 400 or 409
- already-decided approval or terminal cancellation: 409

Every error response uses the same body shape: `{"error":{"message":"...","code":"..."}}`.

## 12. Verification and acceptance criteria

The implementation is only done when the following are green:
- `scripts/smoke_test.py` passes without changes
- `scripts/duplication_check.py` exits 0 after a kill-and-resume drill
- all four seeded workflows can be listed and published
- `wf_expense_approval` can pause on approval and resume successfully
- `wf_runaway` fails with a max-step reason before looping forever
- prompt-injection payloads do not bypass approval gates
- side-effect traces remain durable through worker restarts
- run statuses remain within the required vocabulary

The project may include Good To Have items such as the NL compiler or cron schedules, but only after the Must Have contract is green and durable.

## 13. Recommended delivery strategy

The implementation should proceed in this specific order:
1. Seed and database foundation
2. Workflow CRUD and validation
3. Trigger API and PostgreSQL queue
4. Worker execution and step persistence
5. Crash recovery and idempotency
6. Approval gate enforcement and cancellation
7. AI node validation and prompt-injection guardrails
8. Trace API, console, and smoke-check verification


- Trigger transaction: insert run + snapshot + queue job.
- Approval transaction: decide approval + update run/queue state.
- Step completion transaction: persist output + advance run + reschedule/complete job.
- Claim transaction: acquire lease atomically.
- Never hold a long DB transaction across external HTTP calls or delays.

## 8. Canonical files and local execution

Keep these files/scripts intact and use them as the source of truth:
- `data/node_catalog.json`
- `data/seed_workflows.json`
- `data/sample_payloads.jsonl`
- `data/nl_eval.jsonl`
- `scripts/mock_world.py`
- `scripts/mock_provider.py`
- `scripts/smoke_test.py`
- `scripts/duplication_check.py`
- `scripts/validate_pack.py`

Catalog metadata (`required`, `enum`, `templatable`, `side_effect`, `requires_approval`) must drive validation and runtime safety; avoid a second manually maintained catalog.

### Java package layout

The Maven project lives at the repository root; there is no nested `app/` directory. Organize current code by feature and keep persistence details under the feature that owns them. Packages marked future are added with their implementation phase.

```text
src/main/java/com/airtribe/relayflow/
  auth/                 # demo-token filter; webhook auth added with triggers
  catalog/              # catalog loader shared by validation and runtime
  config/               # typed application configuration
  seed/                 # repeatable startup seed loader
  exception/            # global exception handler
    GlobalExceptionHandler.java
  workflow/             # workflow feature boundary
    WorkflowController.java
    dto/                 # immutable API response records
    entity/              # WorkflowEntity (JPA mapping)
    repository/          # WorkflowRepository (Spring Data JPA)
    service/             # WorkflowService and WorkflowDefinitionValidator
    exceptions/          # domain exceptions and API exception advice
  run/                  # future run API, run/step persistence and trace DTOs
  approval/              # future approval decisions and gate queries
  nodes/                 # future node handlers
  worker/                # future queue polling, leases and recovery
  ai/                    # future provider adapter, schema validation and usage
  mockworld/             # future external mock-world HTTP client
  console/               # future page/controllers if server-rendered UI is used
src/main/resources/
  application.properties
  db/migration/ # Flyway
  templates/
  static/
src/test/java/com/airtribe/relayflow/ # tests mirror production packages, including workflow/dto and workflow/service
data/            # original canonical starter data
scripts/         # original Python support and verification scripts
docs/DESIGN.md
```

### Local processes
1. PostgreSQL service.
2. Mock world: `python scripts/mock_world.py --port 9210`.
3. Spring Boot API.
4. Spring Boot worker.
5. Acceptance scripts.

API and worker use the same database/config. No AWS resource is required for local development. Use fake model provider for deterministic tests and a real provider only for AI demos.

## 9. Verification plan

### Gate A — API and validation
- Seeds present and Published.
- Create/publish/trigger a minimal workflow.
- Unknown type, missing parameter, bad pointer and bad entry rejected with clear errors.
- Wrong webhook secret rejected; correct secret creates run.
- Status vocabulary exact.

### Gate B — execution
- Expense below $100 succeeds without approval.
- Expense above $100 pauses, appears in pending approvals, then resumes and succeeds after approval.
- `wf_runaway` fails with a cap reason.
- Traces contain resolved inputs and outputs.

### Gate C — reliability
- Kill worker during `wf_slow_fulfillment` delay and restart.
- Verify resume and idempotency headers.
- `duplication_check.py` exits 0.
- Test transient mock-world failures, timeouts, cancellation in queued/running/waiting states, and invalid AI output followed by one repair.

### Gate D — console
- Workflows, run history, trace, pending approvals, approve/reject and cancel are functional.

### Gate E — Good To Have
- Compiler refusal for unsupported capabilities.
- Generated workflow passes standard validator.
- Evaluation report from `nl_eval.jsonl`.
- Cron schedule tests if implemented.

Run the original smoke test unchanged:
```bash
python scripts/smoke_test.py --url http://localhost:8080 --token <demo-token> --world http://localhost:9210
```
Then:
```bash
python scripts/duplication_check.py --url http://localhost:9210
```

## 10. Acceptance checklist

### Must Have
- [ ] Catalog loaded and used by validator.
- [ ] Four seeds loaded as Published.
- [ ] Workflow create/list/publish.
- [ ] Invalid definitions rejected clearly.
- [ ] Run definition snapshot.
- [ ] Manual and secret-protected webhook triggers.
- [ ] All seven node types.
- [ ] Template resolver and clear unresolved-path errors.
- [ ] Durable queue; no inline workflow execution in API request.
- [ ] Worker kill-and-resume passes.
- [ ] Stable idempotency keys on all side effects.
- [ ] `duplication_check.py` passes after recovery drill.
- [ ] Approval pause/approve/reject and engine-level gate.
- [ ] Per-node retry/backoff and dependency timeouts.
- [ ] `max_steps`; `wf_runaway` fails with cap reason.
- [ ] Detailed trace including AI token usage.
- [ ] Cancellation for queued/running/waiting approval.
- [ ] Console for workflows, runs, traces, approvals.
- [ ] `smoke_test.py` passes unchanged.

### Good To Have
- [ ] Natural-language compiler and refusal path.
- [ ] Evaluation over `nl_eval.jsonl`.
- [ ] Cron schedules.
- [ ] Timeout and AI token caps.
- [ ] Numbered immutable workflow versions.

## 11. Key design decisions
1. Python repository assets remain the specification/test harness; Java replaces the platform implementation, not mock world or scripts.
2. PostgreSQL is the durable source of truth; a DB-backed queue is sufficient.
3. API enqueues; worker executes.
4. Snapshots isolate runs from later workflow changes.
5. Engine, not AI prompts, authorizes sensitive actions.
6. Sequence-based idempotency keys distinguish loop iterations and survive retries.
7. Local implementation and all verification precede AWS deployment.
