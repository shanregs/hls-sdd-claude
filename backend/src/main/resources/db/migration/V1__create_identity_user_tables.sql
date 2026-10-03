-- Identity module: users and their fixed-role assignments (Constitution Principle II).
CREATE TABLE app_user (
    id                    UUID PRIMARY KEY,
    display_name          VARCHAR(255) NOT NULL,
    phone                 VARCHAR(20)  NOT NULL,
    password_hash         VARCHAR(255),
    active                BOOLEAN      NOT NULL DEFAULT TRUE,
    linked_teacher_id     UUID,
    failed_attempt_count  INTEGER      NOT NULL DEFAULT 0,
    lock_until            TIMESTAMPTZ,
    CONSTRAINT uq_app_user_phone UNIQUE (phone)
);

CREATE TABLE role_assignment (
    id       UUID PRIMARY KEY,
    user_id  UUID        NOT NULL REFERENCES app_user (id),
    role     VARCHAR(20) NOT NULL,
    CONSTRAINT uq_role_assignment_user_role UNIQUE (user_id, role)
);
