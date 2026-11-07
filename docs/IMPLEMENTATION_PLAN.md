# Relay AI Workflow Orchestrator — Implementation Plan

Status: Local-first execution plan
Primary stack: Java 25 + Spring Boot 4.1.2 + PostgreSQL 18.6 + Maven 3.10
Frontend: small console app, either server-rendered Thymeleaf or a lightweight React + Vite interface if the team wants a modern UI. The backend remains the source of truth.
Objective: Deliver a working Relay platform against the supplied problem statement, seed workflows, node catalog, and Python verification scripts. The plan explicitly treats `docs/API_CONTRACT.md`, `docs/DATA_MODEL.md`, `data/node_catalog.json`, `data/seed_workflows.json`, `scripts/smoke_test.py`, `scripts/duplication_check.py` and `docs/DESIGN.md` as the source of truth.

## 1. Implementation goals

The project must satisfy the Must Have contract before any Good To Have work begins.

Core delivery targets:
- Workflow lifecycle: create, read, validate, publish, and snapshot per-run definitions.
- Triggering: manual API triggers and secret-protected webhook triggers.
- Durable asynchronous execution: PostgreSQL-backed queue + worker loop.
- Deterministic node support: `http_request`, `condition`, `delay`, `notify`.
- AI node support with structured schema validation and exactly-one repair attempt.
- Approval gates and cancellation semantics.
- End-to-end run tracing and console views.
- Local verification: smoke test and duplication check must pass without modifying the supplied scripts.

## 2. Architecture decisions to preserve

- Backend is one Spring Boot service with two runtime modes: API and worker.
- PostgreSQL is the durable store and queue backing store, not just a transient cache.
- Runs capture an immutable definition snapshot at trigger time.
- Trigger requests enqueue and return; worker execution occurs off the HTTP request path.
- Approval gates and `max_steps` enforcement happen in the engine, never in the AI prompt.
- Side-effect calls always carry a stable idempotency key and are retried safely.
- The mock world and all contracts remain unchanged; implementation should adapt to them rather than rewriting them.

## 3. Delivery priorities

### P0 — Must-pass before demo
1. Local bootstrap and environment configuration.
2. Database schema, migrations, and seed loading.
3. Workflow CRUD + validation + publish lifecycle.
4. Trigger API + queue + worker execution.
5. Durable recovery + idempotency.
6. AI node enforcement and prompt-injection safety.
7. Approval gate enforcement and cancellation.
8. Trace API, console, and final verification.

### P1 — Good To Have only after P0 is stable
- NL compiler.
- cron schedule triggers.
- wall-clock timeout and token budget.
- versioned workflow history.
- AWS deployment, if time remains after all local acceptance criteria pass.

## 4. Detailed milestone plan

| Phase | Window | Scope | Exit criteria |
|---|---|---|---|
| P0 / M0 | Day 1 | Baseline audit and contract mapping | Requirements map to source files, no contract drift, task list created |
| P1 / M1 | Days 2–3 | PostgreSQL schema, seed loading, health checks, demo token config | API starts cleanly; seed workflows exist and are published; world URL and token are externalized |
| P2 / M2 | Days 4–5 | Workflow CRUD + validation | Draft creation and publish validation pass; invalid definitions reject with 4xx; seed IDs preserved |
| P3 / M3 | Days 6–8 | Trigger APIs + queue + worker loop | Trigger returns promptly; jobs persist and execute asynchronously; worker updates status and step state |
| P4 / M4 | Days 9–10 | Crash recovery + idempotency | Resume from durable state; no duplicate successful side effect in the ledger; retries reuse stable key |
| P5 / M5 | Days 11–12 | AI + template resolution + guardrails | `ai` step validates schema, retries once on error, rejects unsafe output; prompt-injection payloads do not bypass approval |
| P6 / M6 | Days 13–14 | Approval + cancellation | `waiting_approval` flows and cancellation semantics match contract; no bypass of `requires_approval` nodes |
| P7 / M7 | Days 15–16 | Trace API + console | `GET /runs/{runId}` and `GET /approvals?status=pending` produce accurate user-visible data; console supports basic approval workflow |
| P8 / M8 | Days 17–18 | Final verification and hardening | Smoke test green, duplication check green, common failure cases covered |

## 5. Execution sequence

### Phase 0 — baseline and freeze
- Audit `docs/API_CONTRACT.md`, `docs/DATA_MODEL.md`, `data/seed_workflows.json`, and `data/node_catalog.json`.
- Confirm required routes and statuses from the fixed contract.
- Capture a checklist of all acceptance criteria and map them to code or tests.
- Do not drift from the canonical JSON contracts in the source data and the smoke test.

### Phase 1 — foundation and persistence
- Initialize PostgreSQL schema for workflows, runs, steps, approvals, queue jobs, and seed metadata.
- Load seed workflows idempotently at startup.
- Implement health/readiness endpoints and configuration bindings for:
  - database URL
  - mock-world URL
  - demo token
  - provider credentials or test adapter flag
- Ensure startup is repeatable and does not create duplicate seed rows.

### Phase 2 — workflow definition lifecycle
- Implement `POST /workflows`, `GET /workflows`, and `POST /workflows/{workflowId}/publish`.
- Validate the canonical shape and publish flow.
- Enforce rules from the catalog:
  - node type exists
  - required params present
  - enum values valid
  - `entry` exists
  - all non-null outgoing edges resolve
  - no bad pointers before publish
- Publish sets `status = published` and freezes the definition snapshot for future runs.

### Phase 3 — triggers and asynchronous execution
- Add manual trigger endpoint: `POST /workflows/{id}/trigger`.
- Add webhook trigger: `POST /hooks/{id}` with `X-Relay-Secret` validation.
- Persist a run, its used definition snapshot, trigger input, and queue job in one transaction.
- Worker loop claims one due job, updates `queued` to `running`, and executes.
- Persist step outputs before advancing to the next node.

### Phase 4 — crash recovery and idempotency
- Add queue lease and heartbeat logic.
- Recover jobs after a worker death and continue from persisted step progress.
- Reuse the same `Idempotency-Key` across retries and resume events.
- Validate that side effects do not duplicate in the ledger after a kill-and-resume drill.
- Treat the mock-world ledger as the source of truth for exactly-once behavior.

### Phase 5 — AI and template resolution
- Implement the template engine for `{{trigger.body.*}}` and `{{nodes.*.output.*}}`.
- Build the `ai` node adapter with structured output parsing and validation.
- Perform exactly one repair call on schema mismatch.
- Record token usage and validated output in the trace.
- Verify prompt-injection payloads cannot bypass `requires_approval` restrictions.

### Phase 6 — approval gates and cancellations
- Add `approval` node execution: pause run, create approval record, and move to `waiting_approval`.
- Add `GET /approvals?status=pending`, `POST /approvals/{id}/approve`, and reject flow.
- Enforce approval evidence before any sensitive node executes.
- Add `POST /runs/{runId}/cancel` with queued/running/waiting semantics and 409 on terminal runs.

### Phase 7 — trace API and console
- Expose a run detail endpoint that includes status, step trace, timing, and available token data.
- Show pending approvals and cancellation controls.
- Keep console functionality narrow and operational: workflow list, run status, run trace, approval action.
- Do not build a second workflow engine in the UI.

### Phase 8 — acceptance verification
- Run `scripts/smoke_test.py` unchanged.
- Run `scripts/duplication_check.py` after a kill-and-resume drill.
- Confirm all required run statuses and error mappings match the contract.
- Fix reliability issues before considering the project complete.

## 6. Task-level checklist

### Core backend
- [ ] Define schema and migrations for `workflow`, `run`, `step`, `approval`, `queue_job`
- [ ] Seed loader for the four workflows from `data/seed_workflows.json`
- [ ] Workflow validator against `data/node_catalog.json`
- [ ] Trigger endpoints and transaction-bound queue insert
- [ ] Queue claim/lease helper
- [ ] Worker loop and resume logic
- [ ] Cancellation and termination logic
- [ ] Retry/backoff and timeout wrappers
- [ ] Template resolver for nested values
- [ ] Node handlers for all catalog nodes
- [ ] AI adapter and schema enforcement
- [ ] Mock-world client with idempotency headers

### API and console
- [ ] Run listing and run detail endpoints
- [ ] Approval endpoints and UI actions
- [ ] Workflow list and searchable/detail view
- [ ] Manual trigger and webhook trigger surfaces
- [ ] Pending approval listing and decision actions
- [ ] Run cancel button and terminal-state handling

### Verification
- [ ] Smoke test pass
- [ ] Duplication check pass after kill-and-resume
- [ ] Prompt-injection and approval-bypass checks
- [ ] Max-step runaway case passes
- [ ] Documentation and setup README complete

## 7. Risk controls

- Keep the source contract unchanged: if the implementation disagrees with the source, fix the implementation before changing the contract.
- Do not spend time on the NL compiler or AWS deployment until the local contract and durability checks are green.
- Test the worker crash/restart path before polishing the console.
- Maintain explicit blockers and a short issue list so the team can prioritize high-impact work.
- Keep a stable checkpoint before large refactors so the project can be recovered quickly.

## 8. Definition of done

The project is done only when all of the following are true:
- The local bootstrap and seed initialization work from a clean checkout.
- The four seed workflows are loaded and published.
- The smoke test passes with no script changes.
- `scripts/duplication_check.py` exits 0 after the kill-and-resume demo.
- `wf_runaway` fails with a `max_steps` reason.
- A prompt-injection payload cannot trigger sensitive actions without approval.
- The run trace is durable and readable.
- The basic console can operate the real backend with the demo token.
- The README is complete enough that a fresh local operator can reproduce the demo without hand-holding.

## 9. Final recommendation

Use the existing repository materials as the authoritative source. The code should be shaped around those contracts instead of inventing a custom workflow model. Local reliability is the main grading dimension, and the safest path is to finish the core durable engine first, then add cosmetics or Good To Have features only after the required scripts are green.
