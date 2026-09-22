-- Teacher module Salary History slice (specs/009-teacher-salary-history). See data-model.md.
-- V7 — Identity=V1, Organization=V2, Audit=V3, School/Zone=V4, School/Places=V5, Teacher=V6.
-- Never edited or deleted (FR-004) — a correction is a new row, not a change to an existing one.

CREATE TABLE teacher_salary_history (
    id             UUID          PRIMARY KEY,
    teacher_id     UUID          NOT NULL,
    amount         NUMERIC(12,2) NOT NULL,
    effective_from DATE          NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL,
    created_by     UUID          NOT NULL
);

-- research.md §5: "current"/"as of" both resolve via ORDER BY effective_from DESC, created_at DESC.
CREATE INDEX ix_teacher_salary_history_teacher_id_effective_from
    ON teacher_salary_history (teacher_id, effective_from DESC, created_at DESC);

-- research.md §2: salary no longer lives on teacher_profile — a single source of truth,
-- derived from teacher_salary_history's latest row, not cached redundantly here.
ALTER TABLE teacher_profile DROP COLUMN hls_offered_salary;
