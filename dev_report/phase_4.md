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
| Bounded transient retries with exponential backoff | Implemented on feature branch; runtime verification pending |
| Retry limit exhaustion and terminal failure after the limit | Implemented on feature branch; runtime verification pending |
| Supplied `duplication_check.py` acceptance script | Not yet recorded as run |

The side-effect crash test reported this mock-world ledger result:

```text
Ledger entries checked: 2 (executed: 1, replays absorbed: 1, rejected: 0)
chat.message: 1 executed
PASS: every side effect executed exactly once.
```

This verifies the tested mock-world idempotency path. The receiving service's idempotency ledger is essential to that result.

## Retry implementation

A durable per-node retry flow has now been added on branch `feat/phase4-transient-retries`. Runtime verification is still pending.

### Failure classification

- Connection failures and HTTP request timeouts are classified as transient.
- HTTP 408, 425, 429, and 5xx responses are transient.
- Other non-2xx HTTP responses, request serialization errors, invalid request URIs, and node validation/execution errors are permanent.
- A permanent failure immediately records the failed step, marks the run failed, and completes the queue job.
- A transient failure is retried only while the per-node attempt count is below the configured maximum. Exhaustion records a terminal failed step/run.

### Durable retry state

- The existing `steps.attempt` column tracks attempts for one logical node execution.
- The existing `steps.status = waiting` represents a failed attempt waiting for retry.
- The existing queue `available_at` field stores the durable backoff deadline.
- Saving the waiting step and rescheduling/releasing the queue lease happen in one database transaction.
- The run remains `running` while a retry is pending.
- A running step is persisted before an external request. If a worker crashes, the replacement worker resumes the same attempt rather than incrementing the retry count.
- A waiting step increments its attempt only when the next attempt begins.
- Queue `attempts` continues to count queue claims/reclaims; it is not used as the node retry budget.

### Backoff configuration

Defaults: 3 total attempts (including the initial attempt), 2-second initial delay, exponential doubling, and a 30-second maximum delay. This yields retry waits of 2 seconds and then 4 seconds for the default policy.

Configure through:
- `RELAY_WORKER_RETRY_MAX_ATTEMPTS`
- `RELAY_WORKER_RETRY_INITIAL_DELAY_SECONDS`
- `RELAY_WORKER_RETRY_MAX_DELAY_SECONDS`

No sleeping thread or open database transaction is used during the delay. The PostgreSQL queue makes the retry eligible when `available_at` is reached.

### Idempotency

Every attempt for the same logical side-effect execution reuses `{run_id}:{node_id}:{sequence}`. The attempt number is never part of the key. The receiver's idempotency support remains necessary for safe replay after ambiguous network failures.

### Automated tests added

- HTTP 5xx is classified as transient.
- HTTP 4xx is classified as permanent.
- Worker schedules a transient failure using the expected first backoff.
- Worker does not retry a permanent failure.
- Retry exhaustion marks the step/run failed instead of scheduling another attempt.

These tests have been added but have **not yet been run in the user's local environment**. Crash/restart during retry scheduling and the unchanged supplied duplication script still need runtime verification.

## Phase 4 status

The lease and recovery portion of Phase 4 is locally verified for the reported kill/restart scenarios. Stable idempotency is verified against the mock-world ledger, including the crash window after a side effect and before database step persistence. Durable transient retry/backoff and retry exhaustion are now implemented on the feature branch, but local build, automated test, and runtime recovery verification are pending.

**Phase 4 remains partially complete until the new retry implementation passes local tests, including crash/restart during a scheduled retry.**
