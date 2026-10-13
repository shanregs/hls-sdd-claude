-- Spec 032 US3: a Manager's leave request, decided by an Admin or Director. The manager and leave type ids are plain
-- UUIDs: no foreign key crosses a module. Approved days are written onto manager_day (V49) through attendance.api.

CREATE TABLE manager_leave_request (
    id              UUID PRIMARY KEY,
    manager_id      UUID         NOT NULL,
    leave_type_id   UUID         NOT NULL,
    first_date      DATE         NOT NULL,
    last_date       DATE         NOT NULL,
    first_half      BOOLEAN      NOT NULL DEFAULT FALSE,
    last_half       BOOLEAN      NOT NULL DEFAULT FALSE,
    reason          VARCHAR(500) NOT NULL,
    status          VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'REVOKED')),
    requested_at    TIMESTAMPTZ  NOT NULL,
    decided_by      UUID,
    decided_at      TIMESTAMPTZ,
    decision_reason VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_manager_leave_dates CHECK (first_date <= last_date)
);

CREATE INDEX ix_manager_leave_manager ON manager_leave_request (manager_id, first_date);
CREATE INDEX ix_manager_leave_status ON manager_leave_request (status, first_date);
