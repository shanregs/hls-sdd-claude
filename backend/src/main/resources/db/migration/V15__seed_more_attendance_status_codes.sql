-- Extra status codes for the attendance master data (spec 008): Substitution counts as a worked day,
-- Holiday is a non-working day, Absent is treated like Leave. They are ordinary codes (not system), so an
-- Admin can rename, re-weight or deactivate them. Skipped if a code with that short code already exists.
INSERT INTO attendance_status_code (id, short_code, name, category, weight, active, system, sort_order, version)
SELECT v.id, v.short_code, v.name, v.category, v.weight, TRUE, FALSE, v.sort_order, 0
FROM (VALUES
    ('00000000-0000-0000-0008-000000000005'::uuid, 'S', 'Substitution', 'WORKED',      1.00, 5),
    ('00000000-0000-0000-0008-000000000006'::uuid, 'H', 'Holiday',      'NON_WORKING', 0.00, 6),
    ('00000000-0000-0000-0008-000000000007'::uuid, 'A', 'Absent',       'LEAVE',       0.00, 7)
) AS v(id, short_code, name, category, weight, sort_order)
WHERE NOT EXISTS (
    SELECT 1 FROM attendance_status_code c WHERE lower(c.short_code) = lower(v.short_code)
);
