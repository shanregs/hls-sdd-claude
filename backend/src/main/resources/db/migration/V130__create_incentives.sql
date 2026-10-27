-- Manager incentives (spec 030). One incentive per School, a list of Schools that earned nobody anything, an append-only
-- event history and a one-row programme table that records when the feature went live. Ids of other modules are plain
-- ids (no foreign keys across modules). Money is numeric(12,2) in Rupees.

-- Approvals before this instant earn nothing (research D-3). The migration time is the go-live time.
CREATE TABLE incentive_program (
    id        SMALLINT PRIMARY KEY CHECK (id = 1),
    starts_at TIMESTAMPTZ NOT NULL
);

INSERT INTO incentive_program (id, starts_at) VALUES (1, now());

CREATE TABLE incentive (
    id                 UUID PRIMARY KEY,
    school_id          UUID          NOT NULL,
    contract_id        UUID          NOT NULL,
    earner_manager_id  UUID          NOT NULL,
    earner_user_id     UUID          NOT NULL,
    amount             NUMERIC(12,2) NOT NULL CHECK (amount > 0),
    closure_date       DATE          NOT NULL,
    status             VARCHAR(20)   NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'HELD', 'PAID_IN_RUN')),
    mou_changed        BOOLEAN       NOT NULL DEFAULT FALSE,
    decided_by         UUID,
    decided_at         TIMESTAMPTZ,
    approved_by_earner BOOLEAN       NOT NULL DEFAULT FALSE,
    reject_reason      VARCHAR(500),
    hold_reason        VARCHAR(100),
    run_id             UUID,
    run_month          DATE,
    zone_id            UUID          NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_incentive_school UNIQUE (school_id),
    CONSTRAINT ck_incentive_rejected CHECK (status <> 'REJECTED' OR reject_reason IS NOT NULL),
    CONSTRAINT ck_incentive_paid CHECK (status <> 'PAID_IN_RUN' OR (run_id IS NOT NULL AND run_month IS NOT NULL))
);

CREATE INDEX idx_incentive_status ON incentive (status);
CREATE INDEX idx_incentive_earner_status ON incentive (earner_manager_id, status);
CREATE INDEX idx_incentive_zone_status ON incentive (zone_id, status);
CREATE INDEX idx_incentive_run_month ON incentive (run_month);

CREATE TABLE no_earner (
    id          UUID PRIMARY KEY,
    school_id   UUID         NOT NULL,
    contract_id UUID,
    reason      VARCHAR(40)  NOT NULL CHECK (reason IN (
        'NO_PROSPECT', 'NO_OWNER', 'OWNER_NOT_MANAGER', 'APPROVED_BEFORE_APPROVAL_STEP', 'BEFORE_GO_LIVE')),
    detail      VARCHAR(200),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_no_earner_school UNIQUE (school_id)
);

CREATE TABLE incentive_event (
    id           UUID PRIMARY KEY,
    seq          BIGINT GENERATED ALWAYS AS IDENTITY,
    incentive_id UUID         NOT NULL,
    kind         VARCHAR(30)  NOT NULL CHECK (kind IN (
        'EARNED', 'APPROVED', 'REJECTED', 'REOPENED', 'OFFERED', 'OFFER_REFUSED', 'HELD', 'RELEASED', 'APPLIED',
        'MOU_CHANGED')),
    actor_id     UUID,
    detail       VARCHAR(500),
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_incentive_event_incentive ON incentive_event (incentive_id, seq);

CREATE FUNCTION incentive_event_is_final() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'incentive_event rows cannot be updated or deleted';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_incentive_event_final
    BEFORE UPDATE OR DELETE ON incentive_event
    FOR EACH ROW EXECUTE FUNCTION incentive_event_is_final();
