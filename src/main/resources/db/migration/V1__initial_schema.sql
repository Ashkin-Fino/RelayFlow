CREATE TABLE workflows (
    id varchar(128) PRIMARY KEY CHECK (length(btrim(id)) > 0),
    name text NOT NULL CHECK (length(btrim(name)) > 0),
    description text,
    status varchar(16) NOT NULL CHECK (status IN ('draft', 'published')),
    definition jsonb NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE runs (
    id varchar(128) PRIMARY KEY CHECK (length(btrim(id)) > 0),
    workflow_id varchar(128) NOT NULL REFERENCES workflows(id) ON DELETE RESTRICT,
    definition_snapshot jsonb NOT NULL,
    status varchar(24) NOT NULL CHECK (status IN (
        'queued', 'running', 'waiting_approval', 'succeeded', 'failed', 'cancelled'
    )),
    trigger_type varchar(16) NOT NULL CHECK (trigger_type IN ('manual', 'webhook', 'schedule')),
    trigger_input jsonb NOT NULL DEFAULT '{}'::jsonb,
    current_node_id varchar(128),
    steps_executed integer NOT NULL DEFAULT 0 CHECK (steps_executed >= 0),
    ai_tokens_used bigint NOT NULL DEFAULT 0 CHECK (ai_tokens_used >= 0),
    error jsonb,
    cancel_requested boolean NOT NULL DEFAULT false,
    started_at timestamptz,
    finished_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX runs_workflow_created_idx ON runs (workflow_id, created_at DESC);
CREATE INDEX runs_status_created_idx ON runs (status, created_at);

CREATE TABLE steps (
    id bigserial PRIMARY KEY,
    run_id varchar(128) NOT NULL REFERENCES runs(id) ON DELETE RESTRICT,
    node_id varchar(128) NOT NULL,
    node_type varchar(64) NOT NULL,
    sequence integer NOT NULL CHECK (sequence > 0),
    status varchar(24) NOT NULL CHECK (status IN ('running', 'waiting', 'succeeded', 'failed', 'cancelled')),
    attempt integer NOT NULL DEFAULT 1 CHECK (attempt > 0),
    resolved_input jsonb NOT NULL DEFAULT '{}'::jsonb,
    output jsonb,
    error jsonb,
    idempotency_key text UNIQUE,
    tokens_prompt integer CHECK (tokens_prompt IS NULL OR tokens_prompt >= 0),
    tokens_completion integer CHECK (tokens_completion IS NULL OR tokens_completion >= 0),
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    duration_ms bigint CHECK (duration_ms IS NULL OR duration_ms >= 0),
    CONSTRAINT steps_run_sequence_unique UNIQUE (run_id, sequence),
    CONSTRAINT steps_run_step_node_unique UNIQUE (run_id, id, node_id)
);

CREATE TABLE approvals (
    id varchar(128) PRIMARY KEY CHECK (length(btrim(id)) > 0),
    run_id varchar(128) NOT NULL,
    step_id bigint NOT NULL UNIQUE,
    node_id varchar(128) NOT NULL,
    message text NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('pending', 'approved', 'rejected', 'cancelled')),
    decided_by varchar(128),
    decided_at timestamptz,
    reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT approvals_run_step_fk FOREIGN KEY (run_id, step_id, node_id) REFERENCES steps(run_id, id, node_id) ON DELETE RESTRICT,
    CONSTRAINT approvals_decision_fields_check CHECK (
        (status = 'pending' AND decided_by IS NULL AND decided_at IS NULL)
        OR (status IN ('approved', 'rejected') AND decided_by IS NOT NULL AND decided_at IS NOT NULL)
        OR status = 'cancelled'
    )
);

CREATE INDEX approvals_status_created_idx ON approvals (status, created_at);
CREATE INDEX approvals_run_node_idx ON approvals (run_id, node_id);

CREATE TABLE queue_jobs (
    id bigserial PRIMARY KEY,
    run_id varchar(128) NOT NULL UNIQUE REFERENCES runs(id) ON DELETE RESTRICT,
    status varchar(16) NOT NULL CHECK (status IN ('queued', 'leased', 'completed', 'cancelled')),
    available_at timestamptz NOT NULL DEFAULT now(),
    lease_owner varchar(128),
    lease_expires_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    last_error text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT queue_jobs_lease_fields_check CHECK (
        (status = 'leased' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL)
        OR (status <> 'leased' AND lease_owner IS NULL AND lease_expires_at IS NULL)
    )
);

CREATE INDEX queue_jobs_ready_idx ON queue_jobs (available_at, id) WHERE status = 'queued';
CREATE INDEX queue_jobs_lease_expiry_idx ON queue_jobs (lease_expires_at, id) WHERE status = 'leased';