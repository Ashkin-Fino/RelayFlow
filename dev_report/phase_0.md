# Phase 0 — Baseline Audit and Scope Freeze

**Audit date:** 2026-10-24  
**Phase 0 status: COMPLETED.** The source contract, current application skeleton, local toolchain, and baseline checks have been audited and mapped. Static pack validation and application packaging pass; the context test is blocked by missing datasource configuration, and no Relay API is running yet.

## 1. Scope and source of truth

The audit cross-checked the problem statement, API contract, data model, implementation guide, evaluation guide, design and implementation plan, all files in `data/`, and all verification/mock scripts in `scripts/`.

For implementation decisions, use this authority order:

1. Fixed route, payload, and status requirements in `docs/API_CONTRACT.md` and `scripts/smoke_test.py`.
2. Node parameters and flags in `data/node_catalog.json`; seeded definitions in `data/seed_workflows.json`.
3. Must Have scope and verification scenarios in `docs/RELAY_PROBLEM_STATEMENT.md`, `docs/IMPLEMENTATION_GUIDE.md`, and `docs/EVALUATION_GUIDE.md`.
4. Persistence guidance in `docs/DATA_MODEL.md`.
5. `docs/DESIGN.md` and `docs/IMPLEMENTATION_PLAN.md` express the chosen implementation approach and must remain consistent with the sources above.

The specification is stack-agnostic. The current design selects Java/Spring Boot/PostgreSQL, but that is a project choice rather than a pack requirement. The problem statement requires a read-and-operate console; it explicitly says a drag-and-drop workflow builder is not required.

## 2. Workspace and prerequisite inventory

- Present: `data/` (catalog, seeds, sample payloads, NL eval), `docs/`, and five utility/verification scripts in `scripts/`.
- A Spring Boot 4.1.2 / Java 25 skeleton now exists: `pom.xml`, Maven wrapper, one application entry point, `application.properties`, and one context-load test.
- No workflow/API implementation, entities/repositories, database migrations, seed loader, frontend, or feature-level tests were found.
- `README.md` contains only the project title; it does not yet document setup or execution.
- `HELP.md` is generated Spring Boot reference material, not project-specific setup documentation.
- Python 3.13.7 is available.
- Java 25.0.2 and Spring Boot 4.1.2 are selected in the project.
- Apache Maven 3.10.0 is installed at `C:\DevTools\apache-maven-3.10.0`; PostgreSQL 18 is installed and running with `psql.exe` 18.6. Both machine PATH entries are present. This existing terminal initially had a stale PATH; after refreshing its process PATH, both `mvn` and `psql` resolve by name. `pg_isready` confirms `localhost:5432` is accepting connections.
- The worktree had existing modifications in `.gitignore` and `docs/IMPLEMENTATION_PLAN.md`; these were left untouched by Phase 0.

## 3. Baseline checks

| Check | Command | Result |
|---|---|---|
| Pack consistency | `python scripts/validate_pack.py` | PASS, exit 0: 7 node types, 3 trigger types, 4 seed workflows, 8 sample payloads, 15 NL eval cases (3 traps) |
| Relay API smoke test | `python scripts/smoke_test.py --url http://localhost:8080` | BLOCKED as expected: no Relay API is implemented/running; port 8080 is not listening |
| Mock-world server | `python scripts/mock_world.py --port 9210`; `GET /health` | PASS: server started; response `{"status":"ok","service":"mock-world"}` |
| Mock-world ledger | `GET http://localhost:9210/admin/ledger` | PASS: endpoint responded with 0 entries |
| Mock-world ledger check | `python scripts/duplication_check.py --url http://localhost:9210` | PASS, exit 0: 0 entries checked (0 executed, 0 replays, 0 rejected) |
| Java runtime | `java -version` | Java 25.0.2 available |
| Maven | `mvn -version` after refreshing process PATH | PASS: Apache Maven 3.10.0, using Java 25.0.2 |
| App package | `mvn -q -DskipTests package` | PASS, exit 0: application skeleton compiles and packages when test execution is skipped |
| App tests | `mvn test` | FAIL, exit 1: the only `@SpringBootTest` cannot load the context because datasource configuration/driver selection is incomplete (`Failed to determine a suitable driver class`) |
| PostgreSQL client | `psql --version` after refreshing process PATH | PASS: PostgreSQL 18.6 |

The earlier smoke attempt printed `4 passed, 1 warnings, 12 failed`, but the four apparent passes are not evidence of working validation: the script treats any non-2xx response as rejection, and transport failures return status `0`. The API smoke test is expected to be blocked until endpoints and a worker exist. Do not change the supplied verification script to mask this; rerun it once the API and worker are available.

The duplication checker passes against the newly started mock world because its ledger is empty. This verifies connectivity and the checker's empty-ledger path only; it does **not** verify idempotency, side-effect uniqueness, or crash recovery. Those require a Relay implementation to run `wf_slow_fulfillment`, kill/restart its worker, and populate the ledger first.

## 4. Requirement-to-evidence map

| Requirement area | Canonical evidence | Audit result / implementation status |
|---|---|---|
| Definition and API contract | `docs/API_CONTRACT.md`, `data/seed_workflows.json`, `scripts/smoke_test.py` | Required routes/statuses/payloads are mapped in this report and plan. No controllers or endpoints exist under `src/`; implementation remains future work. |
| Catalog and graph validation | `data/node_catalog.json`, `scripts/validate_pack.py` | Static data passes. No validator code or tests exist under `src/`; runtime type/parameter/enum/entry/edge checks are unimplemented. Loops must remain legal. |
| Seeds and triggers | `data/seed_workflows.json`, `data/sample_payloads.jsonl` | Four seed IDs and eight payloads exist. No seed loader, workflow API, webhook auth, run snapshot, or queue implementation exists under `src/`. |
| Deterministic nodes and templates | Catalog, seed definitions, implementation guide | `http_request`, `condition`, `delay`, `notify`, and template resolution are requirements only; no engine/handler classes or behavior tests exist. |
| AI correctness | Catalog, triage seed, injection payloads, mock provider | Schema validation, one repair attempt, token tracing, and injection resistance are unmapped to implementation because there is no AI adapter or engine code. The mock provider is generic and does not prove Relay behavior. |
| Durability and idempotency | `docs/DATA_MODEL.md`, mock world, duplication check, slow seed | No persistence entities, migrations, queue, worker, or idempotency handling exist. The empty-ledger verifier pass does not test recovery. |
| Approvals, cancellation, and sensitive actions | API contract, catalog, expense/triage seeds | No approval/API/engine implementation exists. Required approval gate, cancellation transitions, and 409 behavior are specified but untested. |
| Step cap, retries, and failures | Catalog, `wf_runaway`, mock-world failure injection, evaluation guide | No runtime cap/retry/timeout logic exists. `wf_runaway` must fail at `max_steps: 12`; implementation and behavior tests remain future work. |
| App bootstrap and tests | `src/main/java/com/airtribe/relayflow/RelayflowApplication.java`, `src/test/java/com/airtribe/relayflow/RelayflowApplicationTests.java`, `src/main/resources/application.properties` | Bootstrap compiles. Only test is a context-load test and currently fails because datasource configuration is incomplete. |
| Console and deliverables | Problem statement, implementation guide, README | Console is read/operate only; no UI exists. README/setup/demo instructions are incomplete. |
| Optional compiler/evaluation | `data/nl_eval.jsonl`, evaluation guide | 15 cases comprise 12 workflow cases and 3 refusal traps. Assertions/notes are answer keys and must not be sent to the compiler. Compiler is explicitly deferred until Must Have passes. |

## 5. Cross-check findings and plan adjustments

- The pack validator is a **static data-pack check**, not an engine test. Its PASS confirms parseable and internally consistent supplied data only.
- The mock-world server can run independently of Relay; its empty-ledger duplication check is useful as a connectivity baseline but is not a durability acceptance result.
- The API smoke test does not cover AI nodes or crash recovery; passing it will not replace the kill-and-resume drill or injection/failure scenarios.
- The demo flow should use the supplied payloads, especially `pay_001`, `pay_inject_001`, `pay_inject_002`, `pay_101`, `pay_102`, and `pay_201`, rather than inventing substitute fixtures.
- Keep the workflow authoring API in scope, but do not make a drag-and-drop editor a P0 deliverable. The required console is for listing workflows/runs, inspecting traces, and operating approvals.
- Keep `max_steps` mandatory. `timeout_seconds`, `max_ai_tokens`, cron schedules, version history, and the NL compiler are Good To Have unless needed for an explicitly selected implementation choice.
- `docs/DESIGN.md` has a misplaced duplicate technical appendix after its final numbered section. It is not a contract defect, but should be cleaned up before treating the design doc as the maintained implementation reference.

## 6. Phase 0 exit status

**Completed:** source inventory and cross-check; canonical requirements mapped to source artifacts and current code/test locations; contract and verification boundaries recorded; build/test/pack baselines captured; local toolchain and services checked; prioritized next gates documented. No unimplemented product feature is represented as complete.

**Not verified (product work, not Phase 0 audit blockers):** authenticated PostgreSQL query/schema access, application seed loading, API/worker behavior, approvals, AI schema enforcement, and crash recovery. The bootstrap compiles, but its context test currently fails for missing datasource configuration. Mock-world health and its empty-ledger verifier path are verified; the real recovery drill remains pending.

## 7. Next implementation gates (Phase 1 onward)

1. Verify PostgreSQL authentication with local credentials and create/document the Relay development database and connection settings; the server is listening, but authenticated SQL access is unverified.
2. Configure the Spring datasource so the existing context test starts successfully; add the initial migration and repeatable catalog/seed loader.
3. Implement workflow list/create/publish validation, then manual/webhook triggers, durable queue, and worker. Preserve the supplied data and verification scripts.
4. Add focused behavior tests, then run the API smoke test when endpoints exist. Perform the kill-and-resume duplication drill only after durable workflow execution exists.

**Phase 0 conclusion: COMPLETED.** The stack is Java 25, Spring Boot 4.1.2, Maven 3.10.0, and PostgreSQL 18.6. The audit exit criteria are met: source/contracts were checked, requirements mapped, baseline checks run, and follow-up work ordered. Phase 1 should address database configuration and the first real workflow behavior; the current test failure and absent API are recorded as implementation gaps, not reasons to leave the Phase 0 audit incomplete.