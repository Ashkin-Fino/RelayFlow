# Phase 4 Development Report — Crash Recovery, Retries, and Idempotency

**Milestone:** M4 — Recovery, retries, and idempotency  
**Status:** Partially implemented. Lease recovery, restart recovery, and mock-world idempotency passed the local tests reported below. Durable transient retry/backoff is not implemented.

## Objective

Recover workflow execution after a worker stops unexpectedly. A replacement worker must reclaim expired work, continue from persisted progress, and avoid repeating an applied side effect. Retry behavior must be bounded and reuse the idempotency key for the same logical node execution.

## Implementation details

### Queue leases and atomic claims

Implemented in `common/repository/QueueJobRepository`.

- A worker claims a due queued job or a leased job whose lease expiry has passed.
- Claiming uses a PostgreSQL CTE with `FOR UPDATE SKIP LOCKED`, then assigns lease owner and expiry.
- Every claim receives a unique lease token based on worker ID and a generated UUID. Reclaiming creates a new token, even if the same worker ID is reused.
- Queue updates are fenced by the lease token and require the lease to remain active. A stale worker cannot complete or reschedule a job after expiry or after another worker has reclaimed it.
- Lease duration defaults to 60 seconds; polling defaults to one second.

The queue `attempts` column increments on every claim. It represents queue claims/reclaims, not retries for a particular node execution.

### Durable progress and restart recovery

- The run stores its current node and number of executed steps.
- Each completed node's step output is persisted before the run advances.
- A resumed worker loads the immutable run snapshot and reconstructs prior successful outputs from persisted step rows.
- Completed steps are not replayed merely because a worker process restarted.
- Delay nodes use persisted queue `available_at`; recovery does not depend on an in-memory timer or sleeping thread.

### Stable idempotency keys

- Side-effect nodes use `{run_id}:{node_id}:{sequence}`.
- Sequence distinguishes separate executions of the same node in a loop.
- Recovery or a future retry of the same logical execution must reuse the same key; retry attempt is not part of the key.
- The mock-world service records idempotency keys and absorbs replayed requests. This provides effectively-once behavior for the tested mock-world side effects.
- This is not a universal exactly-once guarantee for arbitrary external services. Replay safety depends on the receiving service implementing idempotency for the supplied key.

### Failure persistence

The current worker records a permanent node execution failure by saving the failed step, marking the run failed, and completing the queue job. This behavior was verified against PostgreSQL.

## Recovery and idempotency flow

1. Worker A claims a due queue job and receives a lease token.
2. Worker A executes or begins executing the current node.
3. If Worker A stops before completing the queue transition, the job remains leased until expiry.
4. Another worker claims the expired job with a new lease token.
5. The new worker reloads the run and successful step history.
6. It resumes from the persisted current node.
7. If the previous worker's side-effect request reached the mock world but its result was not committed to PostgreSQL, the new worker replays the request with the same idempotency key.
8. The mock world recognizes the replay instead of applying the same side effect twice.

## Local verification

| Verification | Result |
|---|---|
| Two workers do not claim the same active job concurrently | Passed |
| Kill worker after claim; replacement worker reclaims after lease expiry | Passed |
| Stop workers during a durable delay; restart and continue | Passed |
| Resume starts from the last persisted successful step | Passed |
| Completed notification before the delay is not sent again after restart | Passed |
| Crash after external side effect but before step persistence; replay uses the same key | Passed |
| Mock-world ledger reports one execution and one absorbed replay, with no rejected entries | Passed |
| Permanent external failure is persisted as failed step/run and completed queue job | Passed |
| Bounded transient retries with exponential backoff | Not implemented |
| Retry limit exhaustion and terminal failure after the limit | Not implemented |
| Supplied `duplication_check.py` acceptance script | Not yet recorded as run |

The side-effect crash test reported this mock-world ledger result:

```text
Ledger entries checked: 2 (executed: 1, replays absorbed: 1, rejected: 0)
chat.message: 1 executed
PASS: every side effect executed exactly once.
```

This verifies the tested mock-world idempotency path. The receiving service's idempotency ledger is essential to that result.

## Known gap — transient retry and backoff

**Durable per-node retry is not implemented in the current code.**

The current execution path catches a node exception, persists the step as failed, marks the run failed, and completes the queue job. The deterministic HTTP executor throws when the remote endpoint returns a non-2xx response. It does not classify transient and permanent failures or schedule a retry.

The existing queue `attempts` counter must not be used as the node retry count because it increments for ordinary one-node-per-claim progression and lease recovery as well as any future retry. The `steps.attempt` column currently defaults to 1 and is not advanced by a retry flow.

### Required follow-up implementation

1. Define a retry policy for transient failures, including connection failures/timeouts and selected HTTP statuses such as 408, 429, and 5xx. Treat permanent validation/business failures and other non-retryable 4xx responses as terminal.
2. Track attempts for the logical node execution separately from queue claim count.
3. Persist the failed attempt/error and next retry time.
4. Reschedule the queue job using PostgreSQL `available_at` with bounded exponential backoff. Do not use `Thread.sleep()` or hold a database transaction during the wait.
5. Keep the run active while a retry remains available; mark the step/run failed only after retry exhaustion or a permanent error.
6. Reuse the exact same idempotency key for every attempt of the same run/node/sequence.
7. Ensure a lease reclaim does not accidentally consume a node retry attempt.
8. Test transient recovery, retry exhaustion, permanent failure, and crash during retry scheduling.

## Phase 4 status

The lease and recovery portion of Phase 4 is locally verified for the reported kill/restart scenarios. Stable idempotency is verified against the mock-world ledger, including the crash window after a side effect and before database step persistence.

**Phase 4 remains partially complete until bounded durable transient retry/backoff and retry-exhaustion behavior are implemented and tested.** Retry support must not be described as complete yet.
