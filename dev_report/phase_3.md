# Phase 3 Development Report — Trigger, Durable Queue, and Basic Execution

**Milestone:** M3 — Trigger, durable queue, and basic execution  
**Status:** Implemented; reported local runtime tests passed. Full acceptance verification remains pending.

## 1. Objective

Implement manual and webhook triggers, persist each accepted run and its execution snapshot, and execute workflow nodes asynchronously through a PostgreSQL-backed queue. Execution progress must survive API and worker process restarts.

## 2. Implementation details

### Trigger API

Implemented in the API run controller and trigger service.

- Manual trigger: POST /workflows/{workflowId}/trigger. Requires a published manual workflow and an object-valued input property. Returns HTTP 202 with run_id.
- Webhook trigger: POST /hooks/{workflowId}. Requires a published webhook workflow, validates X-Relay-Secret using a constant-time comparison, rejects invalid secrets and non-object payloads, and returns HTTP 202 with run_id.
- Both trigger paths persist the run, trigger input, immutable definition snapshot, and queue job in one database transaction.

### PostgreSQL queue and worker

- PostgreSQL is the durable queue; no separate broker is required.
- Queue claims use a transaction and FOR UPDATE SKIP LOCKED to prevent concurrent claims of the same available job.
- Claims record worker identity, lease expiry, and a unique lease token.
- Worker polling interval, worker identity, and lease duration are configurable.
- The same Spring Boot application/JAR supports combined API+worker, API-only, and worker-only modes.

### Execution and progress persistence

Implemented in WorkerExecutionService, RunProgressService, and the deterministic node executor.

For each queue claim, the worker loads the run and immutable snapshot, moves queued runs to running, checks limits.max_steps, locates the current node, reconstructs prior successful outputs, resolves parameters, executes one node, and persists step output plus run progress transactionally. It then completes the job or releases/reschedules it for the next node.

One node per claim keeps leases short. Delay is represented by a future queue availability time rather than a sleeping worker thread.

### Implemented deterministic nodes

- condition: evaluates supported comparison operators and selects on_true or on_false.
- delay: returns a delay duration and reschedules the job using PostgreSQL available_at.
- notify: sends email or chat notifications to the unchanged mock-world service.
- http_request: sends the configured HTTP request and captures the response.

Side-effect requests use the stable key {run_id}:{node_id}:{sequence}. Unsupported node types fail explicitly.

### Failure and guardrail behavior

- Template-resolution and node-execution errors are persisted as failed step/run outcomes.
- A failed node marks the run failed and completes the associated queue job.
- The worker enforces max_steps. Exceeding it fails the run with max_steps_exceeded and a message containing the configured limit.
- Run statuses are constrained by the database schema to the defined lifecycle vocabulary.

## 3. Runtime execution flow

1. Caller submits a manual trigger or secret-protected webhook.
2. API validates the workflow and trigger configuration.
3. One transaction inserts the run, immutable definition snapshot, trigger input, and queue job.
4. API returns HTTP 202 with the run ID.
5. Worker claims a due queue job using a lease.
6. Worker executes one node from the persisted run position.
7. Step output and the next run position are saved before the job is released or completed.
8. The next claim continues from persisted run state.

## 4. Local verification

The following results were reported from local testing:

| Verification | Result |
|---|---|
| Trigger creates a run and queue row | Passed |
| API-only mode leaves queued work for a worker | Passed |
| Worker-only mode processes previously queued work | Passed |
| Two workers can process different jobs | Passed |
| Same job is not claimed concurrently by both workers | Passed |
| Delay node schedules the next node without blocking for the delay duration | Passed |
| wf_runaway stops at max_steps=12 with max_steps_exceeded | Passed |
| Permanent mock-world failure is persisted as failed step and run, with queue job completed | Passed |
| Manual and webhook route implementations | Implemented; webhook runtime test not recorded here |
| Supplied smoke_test.py | Not yet recorded as run |
| Supplied duplication_check.py | Not yet recorded as run |

Permanent-failure database verification confirmed all three expected records: failed step, failed run, and completed queue job.

## 5. Phase 3 status and remaining scope

**Phase 3 implementation is in place and its reported local execution tests passed.** Later-milestone items are not claimed as complete:

- Durable transient-failure retry and exponential backoff.
- AI provider execution and structured output validation/repair.
- Approval lifecycle and engine-enforced approval evidence.
- order_action execution.
- Run cancellation.
- Complete run/step trace API and console.
- Full unchanged smoke-test and duplication-check acceptance runs.

Phase 3 is locally verified for the tested trigger/queue/worker paths, but the project is not overall acceptance-complete.

## 6. Local run modes

Build:

~~~powershell
.\mvnw.cmd clean package
~~~

Combined API and worker mode (worker enabled by default):

~~~powershell
java -jar target/relayflow-0.0.1-SNAPSHOT.jar
~~~

API-only mode:

~~~powershell
java -jar target/relayflow-0.0.1-SNAPSHOT.jar --relay.worker.enabled=false
~~~

Worker-only mode:

~~~powershell
java -jar target/relayflow-0.0.1-SNAPSHOT.jar --spring.main.web-application-type=none --relay.worker.enabled=true --relay.worker.id=worker-only-1
~~~

The API and worker must use the same PostgreSQL database. See the root README.md for database setup, environment configuration, and mock-world startup instructions.
