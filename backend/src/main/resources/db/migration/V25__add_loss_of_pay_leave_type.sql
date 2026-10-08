-- Spec 009 amendment A3: the Loss-of-Pay leave type. Payroll (spec 013a) treats an approved day of this
-- type as an unpaid day; it finds the type by the stable code LOP exposed in leave.api.
-- Idempotent: safe if the row already exists (by id or by code).

INSERT INTO leave_type (id, code, name, sort_order, active) VALUES
    ('00000000-0000-0000-0009-000000000005', 'LOP', 'Loss of Pay', 5, TRUE)
ON CONFLICT DO NOTHING;
