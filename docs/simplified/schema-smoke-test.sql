-- Smoke test for schema.sql. Run after it on an empty database:
--   psql -v ON_ERROR_STOP=1 -f schema-smoke-test.sql
-- Part 1 inserts a small happy-path data set (a payroll month and an invoice).
-- Part 2 proves that the constraints refuse bad data: each statement must fail;
-- the script raises an error if one of them is accepted.
-- All inside a transaction that is rolled back.
BEGIN;

INSERT INTO app_user (phone, username, password_hash, full_name, roles) VALUES
  ('9000000001','admin','x','Admin One','{ADMIN}'),
  ('9000000002','director','x','Director One','{DIRECTOR}'),
  ('9000000003','mgr1','x','Manager One','{MANAGER}'),
  ('9000000004','t1','x','Teacher One','{TEACHER}');

INSERT INTO designation (name, kind, suggested_salary) VALUES ('Teacher','TEACHER',20000), ('Zone Manager','MANAGER',40000);
INSERT INTO designation (name, kind, substitute_day_rate) VALUES ('Substitute','SUBSTITUTE',800);

INSERT INTO zone (name) VALUES ('Zone A');
INSERT INTO employee (kind, employee_code, full_name, user_id, designation_id, zone_id, joining_date)
  SELECT 'MANAGER','M001','Manager One',u.id,d.id,z.id,'2026-01-01' FROM app_user u, designation d, zone z
  WHERE u.username='mgr1' AND d.name='Zone Manager' AND z.name='Zone A';
INSERT INTO employee (kind, employee_code, full_name, user_id, designation_id, joining_date)
  SELECT 'TEACHER','T001','Teacher One',u.id,d.id,'2026-01-01' FROM app_user u, designation d WHERE u.username='t1' AND d.name='Teacher';
INSERT INTO employee (kind, employee_code, full_name, designation_id, zone_id, day_rate)
  SELECT 'SUBSTITUTE','S001','Sub One',d.id,z.id,800 FROM designation d, zone z WHERE d.name='Substitute' AND z.name='Zone A';
UPDATE zone SET manager_id = (SELECT id FROM employee WHERE employee_code='M001');

INSERT INTO employee_salary (employee_id, monthly_salary, effective_from, set_by)
  SELECT e.id, 20000, '2026-01-01', u.id FROM employee e, app_user u WHERE e.employee_code='T001' AND u.username='admin';

INSERT INTO school (name, zone_id, manager_id, city, pincode)
  SELECT 'School One', z.id, z.manager_id, 'Pune', '411001' FROM zone z;
INSERT INTO school_month_days (school_id, year, month, working_days, updated_by)
  SELECT s.id, 2026, 10, 26, u.id FROM school s, app_user u WHERE u.username='admin';

INSERT INTO school_contract (school_id, teacher_count, start_date, end_date, fee_mode, uniform_monthly_fee, created_by)
  SELECT s.id, 1, '2026-04-01','2027-03-31','UNIFORM',30000,u.id FROM school s, app_user u WHERE u.username='admin';
INSERT INTO contract_position (contract_id, school_id, position_no)
  SELECT id, school_id, 1 FROM school_contract;
INSERT INTO placement (employee_id, position_id, school_id, start_date, created_by)
  SELECT e.id, p.id, p.school_id, '2026-04-01', u.id FROM employee e, contract_position p, app_user u
  WHERE e.employee_code='T001' AND u.username='admin';

INSERT INTO attendance_mark (employee_id, school_id, mark_date, status, marked_by)
  SELECT e.id, s.id, '2026-10-05','ABSENT',u.id FROM employee e, school s, app_user u WHERE e.employee_code='T001' AND u.username='admin';
INSERT INTO substitution (absent_employee_id, school_id, absence_date, status, substitute_id, assigned_by, assigned_at,
                          day_status, day_marked_by, day_marked_at, day_rate)
  SELECT t.id, s.id, '2026-10-05','ASSIGNED', sub.id, u.id, now(), 'PRESENT', u.id, now(), 800
  FROM employee t, employee sub, school s, app_user u
  WHERE t.employee_code='T001' AND sub.employee_code='S001' AND u.username='admin';

INSERT INTO payroll_run (year, month, started_by) SELECT 2026, 10, id FROM app_user WHERE username='admin';
INSERT INTO payroll_line (run_id, employee_id, monthly_salary, working_days, payable_days, lop_days, day_value, earned_pay, lop_amount, net_pay)
  SELECT r.id, e.id, 20000, 26, 25, 1, 769.23, 20000, 769.23, 19230.77 FROM payroll_run r, employee e WHERE e.employee_code='T001';
INSERT INTO payroll_line (run_id, employee_id, substitute_days, substitute_rate, net_pay)
  SELECT r.id, e.id, 1, 800, 800 FROM payroll_run r, employee e WHERE e.employee_code='S001';
UPDATE payroll_run SET status='APPROVED', approved_by=(SELECT id FROM app_user WHERE username='director'), approved_at=now();
-- payment columns stay editable after approval
UPDATE payroll_line SET paid_status='PAID', paid_on='2026-11-02', paid_mode='BANK' WHERE substitute_days IS NULL;
UPDATE substitution SET paid_in_run_id=(SELECT id FROM payroll_run);

INSERT INTO invoice (invoice_no, school_id, contract_id, year, month, raised_on, due_date, total_amount, raised_by)
  SELECT 'INV-2026-10-001', c.school_id, c.id, 2026, 10, '2026-11-01','2026-12-01',30000,u.id FROM school_contract c, app_user u WHERE u.username='admin';
INSERT INTO payment (school_id, amount, paid_on, mode, recorded_by)
  SELECT s.id, 10000,'2026-11-10','BANK',u.id FROM school s, app_user u WHERE u.username='admin';
INSERT INTO payment_allocation (payment_id, invoice_id, amount) SELECT p.id, i.id, 10000 FROM payment p, invoice i;
INSERT INTO invoice_adjustment (invoice_id, amount, reason, created_by) SELECT i.id, 2000,'agreed deduction',u.id FROM invoice i, app_user u WHERE u.username='admin';

DO $$ BEGIN
  IF (SELECT balance FROM invoice_balance) <> 18000 THEN RAISE EXCEPTION 'balance wrong'; END IF;
END $$;

-- ---------------- Part 2: statements that MUST be refused ----------------
CREATE FUNCTION expect_fail(sql text, label text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
  BEGIN
    EXECUTE sql;
  EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'ok   refused: % (%)', label, SQLSTATE;
    RETURN;
  END;
  RAISE EXCEPTION 'NOT REFUSED: %', label;
END $$;

SELECT expect_fail($$INSERT INTO app_user (phone,password_hash,full_name,roles) VALUES ('9','x','n','{SYSTEM}')$$, 'unknown role');
SELECT expect_fail($$INSERT INTO app_user (phone,password_hash,full_name,roles) VALUES ('9000000001','x','n','{ADMIN}')$$, 'duplicate phone');
SELECT expect_fail($$UPDATE employee_salary SET monthly_salary = 1$$, 'salary history is append-only');
INSERT INTO contract_position (contract_id, school_id, position_no) SELECT id, school_id, 2 FROM school_contract;
INSERT INTO employee (kind, employee_code, full_name, status) VALUES ('TEACHER','T002','Teacher Two','IN_TRAINING');
INSERT INTO audit_log (action, entity_type, entity_id) VALUES ('TEST','employee',1);
SELECT expect_fail($$DELETE FROM audit_log$$, 'audit is append-only');
SELECT expect_fail($$INSERT INTO placement (employee_id, position_id, school_id, start_date, created_by)
   SELECT e.id, p.id, p.school_id, '2026-05-01', 1 FROM employee e, contract_position p WHERE p.position_no=2 AND e.employee_code='M001'$$, 'Manager cannot be placed');
SELECT expect_fail($$INSERT INTO placement (employee_id, position_id, school_id, start_date, created_by)
   SELECT e.id, p.id, p.school_id, '2026-05-01', 1 FROM employee e, contract_position p WHERE e.employee_code='T001'$$, 'overlapping placement of the same Teacher/seat');
SELECT expect_fail($$INSERT INTO placement (employee_id, position_id, school_id, start_date, created_by)
   SELECT e.id, p.id, 999, '2026-05-01', 1 FROM employee e, contract_position p WHERE p.position_no=2 AND e.employee_code='T002'$$, 'placement school must match position school');
SELECT expect_fail($$INSERT INTO school_contract (school_id, teacher_count, start_date, end_date, fee_mode, uniform_monthly_fee, created_by)
   SELECT id, 1, '2027-04-01','2028-03-31','UNIFORM',1,1 FROM school$$, 'second live contract for a School');
SELECT expect_fail($$INSERT INTO attendance_mark (employee_id, school_id, mark_date, status, marked_by)
   SELECT e.id, s.id, '2026-10-05','PRESENT',1 FROM employee e, school s WHERE e.employee_code='T001'$$, 'two marks for one Teacher and date');
SELECT expect_fail($$INSERT INTO attendance_mark (employee_id, school_id, mark_date, status, marked_by)
   SELECT e.id, s.id, '2026-10-06','HOLIDAY',1 FROM employee e, school s WHERE e.employee_code='T001'$$, 'unknown attendance status');
SELECT expect_fail($$INSERT INTO manager_checkin (employee_id, work_date, day_type, check_in_at)
   SELECT id, '2026-10-06','FIELD', now() FROM employee WHERE employee_code='M001'$$, 'Field day needs a purpose');
SELECT expect_fail($$INSERT INTO leave_request (employee_id, leave_type, from_date, to_date, days, status, decided_by, decided_at)
   SELECT id, 'SICK','2026-10-07','2026-10-07',1,'REJECTED',1,now() FROM employee WHERE employee_code='T001'$$, 'rejection needs a reason');
SELECT expect_fail($$INSERT INTO substitution (absent_employee_id, school_id, absence_date, status)
   SELECT e.id, s.id, '2026-10-05','OPEN' FROM employee e, school s WHERE e.employee_code='T001'$$, 'second requirement for one Teacher and date');
SELECT expect_fail($$UPDATE payroll_line SET net_pay = 1, adjustments_total = 1 WHERE substitute_days IS NULL$$, 'approved run lines are frozen');
SELECT expect_fail($$INSERT INTO payroll_line (run_id, employee_id, earned_pay, net_pay)
   SELECT r.id, e.id, 10, 99 FROM payroll_run r, employee e WHERE e.employee_code='M001'$$, 'line frozen / net must add up');
SELECT expect_fail($$INSERT INTO payroll_run (year, month, started_by) VALUES (2026, 10, 1)$$, 'second run for a month');
INSERT INTO payroll_adjustment (employee_id, kind, amount, description, for_year, for_month, school_id, status, created_by)
  SELECT e.id,'INCENTIVE',5000,'won',2026,11,s.id,'PENDING',1 FROM employee e, school s WHERE e.employee_code='M001';
SELECT expect_fail($$INSERT INTO payroll_adjustment (employee_id, kind, amount, description, for_year, for_month, school_id, status, created_by)
   SELECT e.id,'INCENTIVE',5000,'won again',2026,12,s.id,'PENDING',1 FROM employee e, school s WHERE e.employee_code='M001'$$, 'second incentive for the same School');
SELECT expect_fail($$INSERT INTO invoice (invoice_no, school_id, contract_id, year, month, raised_on, due_date, total_amount, raised_by)
   SELECT 'X', c.school_id, c.id, 2026, 10, '2026-11-01','2026-12-01',1,1 FROM school_contract c$$, 'second invoice for School and month');
SELECT expect_fail($$UPDATE invoice SET total_amount = 1$$, 'invoice is immutable');
SELECT expect_fail($$DELETE FROM payment$$, 'payment is append-only');
SELECT expect_fail($$INSERT INTO expense_claim (claimant_id, claim_date, amount, category, status)
   SELECT id, current_date, 100, 'TRAVEL_FUEL', 'SUBMITTED' FROM employee WHERE employee_code='M001'$$, 'claim needs a Zone or head office');
SELECT expect_fail($$INSERT INTO employee (kind, employee_code, full_name) VALUES ('SUBSTITUTE','S002','No zone')$$, 'Substitute needs a Zone');

ROLLBACK;
