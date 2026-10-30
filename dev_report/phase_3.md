# Phase 3 Development Report — Trigger Queue and Worker Execution

**Phase:** M3 — Trigger, durable queue, and basic execution  
**Branch:** `feat/phase3-worker-execution`  
**Status:** Draft PR; local runtime verification is still required.

## Objective
Connect the manual/webhook trigger API and PostgreSQL queue to a background worker that executes asynchronously, persists each node transition, and resumes from durable state after a restart.

## Implemented
- Added scheduled `QueueWorker` polling the PostgreSQL queue through `FOR UPDATE SKIP LOCKED`.
- Added `WorkerExecutionService` to load the immutable run snapshot, resume at `current_node_id`, resolve templates, execute the current deterministic node, and persist progress.
- One node is executed per queue claim. Successful progress releases the queue lease immediately for the next node; delay nodes reschedule using PostgreSQL `available_at`, without sleeping a worker thread.
- Enforced `limits.max_steps`; unresolved templates and node execution errors are recorded as failed steps and terminal run errors.
- Preserved stable side-effect idempotency keys as `{run_id}:{node_id}:{sequence}`; queue attempt count and worker identity are excluded.
- Moved run/step entities, repositories, progress service, and executor classes out of `run/` into `execution/`; moved orchestration into top-level `worker/`.
- Updated `docs/DESIGN.md` with the folder structure and runtime modes.
- Added configurable worker identity, poll interval, and lease duration through Spring properties and environment placeholders.
- Added a worker execution unit test and updated trigger tests for the package refactor.

## Runtime flow
1. Trigger API persists the run snapshot and queue job transactionally.
2. Worker claims a job and receives a unique lease token.
3. Worker loads the persisted run state and previous successful outputs.
4. It resolves and executes one node.
5. A transaction persists the step, advances the run, and completes or reschedules the queue job.
6. A crash leaves the lease to expire; a later worker reclaims the job and resumes from persisted progress.

## Verification
A focused unit test was added. Maven tests, PostgreSQL/mock-world integration, `scripts/smoke_test.py`, and `scripts/duplication_check.py` were **not run in this environment**. The draft PR must be locally verified before merging.

## Scope limitations / follow-up
This worker invokes the deterministic executor currently implemented for `condition`, `delay`, `notify`, and `http_request`. AI, approval, and order-action nodes; approval lifecycle; retries/backoff; cancellation; and the complete crash/duplication acceptance drill remain follow-up milestones. This report does not mark those features complete.

## Local verification checklist
1. Start PostgreSQL and the unchanged mock world on port 9210.
2. Start the Spring Boot application (worker enabled by default).
3. Trigger `wf_test_support_notification`.
4. Confirm queue status transitions `queued → leased → completed`, run status becomes `succeeded`, and a step row appears.
5. Trigger `wf_slow_fulfillment`; confirm delay is represented by `available_at` without blocking a worker thread.
6. Stop the worker during a lease, wait for expiry, restart it, and confirm resume from the persisted current node.
7. Run the original smoke and duplication scripts unchanged.
