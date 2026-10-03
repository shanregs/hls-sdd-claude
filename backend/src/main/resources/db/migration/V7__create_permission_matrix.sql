-- Identity module: the role -> permission matrix (Constitution Principle II), spec 002.
CREATE TABLE permission_matrix (
    id           UUID PRIMARY KEY,
    role         VARCHAR(20) NOT NULL,
    module       VARCHAR(40) NOT NULL,
    action       VARCHAR(20) NOT NULL,
    granted      BOOLEAN     NOT NULL,
    updated_by   UUID,
    updated_at   TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_permission_matrix_role_module_action UNIQUE (role, module, action)
);
