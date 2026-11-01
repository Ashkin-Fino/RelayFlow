# Phase 5 Development Report — AI, Templates, and Guardrails

**Milestone:** M5 — AI and template resolution  
**Status:** Implemented for AI execution, schema enforcement, token accounting, nested templates, and sensitive-node guard. End-to-end provider/injection verification is pending.

## Context review

Reviewed `README.md`, `docs/DESIGN.md`, `docs/IMPLEMENTATION_PLAN.md`, `docs/DATABASE_SCHEMA.md`, `docs/RELAY_PROBLEM_STATEMENT.md`, and the available Phase 0–4 reports, along with the API contract, data model, implementation/evaluation guides, catalog, and seed workflows. `docs/CONTEXT.md` is not present in the repository.

The Phase 5 plan calls for `{{trigger.body.*}}` and `{{nodes.*.output.*}}` resolution, an AI provider adapter, JSON Schema validation, exactly one repair request, token trace data, and protection against AI prompt injection bypassing approval requirements.

## Implementation

| Requirement | Implementation | Status |
|---|---|---|
| Nested template paths | Existing `TemplateResolver` now traverses object properties and numeric array indexes; complete-value templates preserve JSON types, while interpolation renders values into strings. Missing paths fail explicitly. | Implemented |
| Provider adapter | Added an OpenAI-compatible chat-completions client behind `AiProviderClient`; endpoint, model, key, and request timeout use typed/configured settings. Provider errors are classified for the existing durable retry policy. | Implemented |
| Prompt-injection boundary | Provider system instructions treat customer/external text as data and explicitly deny action/approval authority. AI output is restricted to the workflow's declared schema and is not interpreted as an approval decision. | Implemented |
| Structured output | AI response content is parsed as JSON and validated against the node's `output_schema`. Invalid schemas and remote schema references are rejected before use. | Implemented |
| One repair call | A failed parse/schema validation triggers exactly one second provider call with validation errors appended; a second invalid response fails the step without further calls. | Implemented |
| Token tracing | Prompt/completion token usage from both calls is returned with the step result, stored in the existing `steps` columns, and accumulated into `runs.ai_tokens_used`. Failed/retried AI attempts retain reported token usage. | Implemented |
| Sensitive-node enforcement | Before executing any catalog node marked `requires_approval`, the worker requires an earlier successful `approval` step whose persisted output explicitly says `decision: approved`. The decision is checked from worker-persisted step state, not AI output. | Implemented |

Approval creation/decision APIs and the approval node lifecycle remain Phase 6 work. Until those exist, sensitive nodes without the required persisted approval step fail closed. No migration was needed because the database schema already contains the token columns.

## Verification

Focused command:

```powershell
.\mvnw.cmd "-Dtest=AiNodeExecutorTest,OpenAiCompatibleAiProviderClientTest,TemplateResolverTest,DeterministicNodeExecutorTest,WorkerExecutionServiceTest" test
```

Result: **PASS**, 17 tests. Coverage includes valid AI output and token usage, one schema-repair call, terminal schema failure after two calls, rejection of remote schema references, denial of AI output that attempts to claim approval, OpenAI-compatible request/auth/usage handling, nested object/array templates, unresolved paths, provider-independent deterministic execution, existing worker retry behavior, and both denial and allowance cases for the sensitive-node approval guard.

The first test run found an invalid test template (`nodes.classify.output` omitted a required path component); the test was corrected to reference `nodes.classify.output.category`. The first run of the new guard tests then exposed a missing step-type accessor, which was added; the final focused suite passes.

Full verification: `.\mvnw.cmd package` **PASS**, 43 tests passed, zero failures/errors; `python scripts/validate_pack.py` **PASS** (7 node types, 3 trigger types, 4 seed workflows, 8 sample payloads, and 15 NL evaluation cases); `git diff --check` **PASS**.

## Remaining verification and limitations

- Live integration against an OpenAI-compatible model and the sample prompt-injection payloads has not been run.
- The bundled `mock_provider.py` emits generic prose rather than schema-shaped classifications, so it is not sufficient for the seeded triage workflow's structured output without a provider/test double that returns the requested JSON.
- The approval lifecycle/API and run trace API are not implemented yet; token persistence is in place but there is not yet a user-facing trace endpoint.
- Supplied smoke and duplication acceptance scripts have not been recorded as passing.
