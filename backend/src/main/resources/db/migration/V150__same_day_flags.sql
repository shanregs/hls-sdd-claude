-- Spec 040: same-day flexibility. A flag on each record saying it was recorded for today without prior approval or
-- planning, the link between the two parts of a split leave request, and the phone's idempotency key for offline logs.

ALTER TABLE leave_request
    ADD COLUMN same_day          BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN linked_request_id UUID    REFERENCES leave_request (id);

ALTER TABLE substitution_requirement
    ADD COLUMN same_day BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE task
    ADD COLUMN same_day   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN client_key UUID;

ALTER TABLE marketing_activity
    ADD COLUMN same_day   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN client_key UUID;

CREATE UNIQUE INDEX uq_task_client_key ON task (client_key) WHERE client_key IS NOT NULL;
CREATE UNIQUE INDEX uq_marketing_activity_client_key ON marketing_activity (client_key) WHERE client_key IS NOT NULL;

CREATE INDEX ix_leave_request_same_day ON leave_request (first_date) WHERE same_day;
CREATE INDEX ix_substitution_same_day ON substitution_requirement (duty_date) WHERE same_day;
CREATE INDEX ix_task_same_day ON task (due_on) WHERE same_day;
CREATE INDEX ix_marketing_activity_same_day ON marketing_activity (activity_date) WHERE same_day;
