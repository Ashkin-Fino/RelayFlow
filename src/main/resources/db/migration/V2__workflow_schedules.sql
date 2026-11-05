CREATE TABLE workflow_schedules (
    workflow_id varchar(128) PRIMARY KEY REFERENCES workflows(id) ON DELETE RESTRICT,
    cron text NOT NULL,
    next_fire_at timestamptz NOT NULL,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX workflow_schedules_due_idx
    ON workflow_schedules (next_fire_at, workflow_id)
    WHERE enabled = true;
