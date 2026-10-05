# 005a Designations and Employment Details (amendment A1 to 005): `/speckit-specify` input

2026-10-05. Roadmap amendment **A1**. Depends on 005 (merged). Needed by **013a** (salary structures by
designation) and **032** (Manager attendance). It adds three small facts to the Manager and Teacher records and a list
to choose from; it changes no rule of 005.

## Feature description (paste as the argument to `/speckit-specify`)

005a-designations: Designations and employment details for Managers and Teachers. A **Designation** is a named job
title that salary structures are attached to (spec 013a). An Admin or Director keeps the list under MASTER DATA →
Designations: name, whether it applies to Teachers or Managers, and active or retired (never deleted once used).
A **Manager record** gains a designation, an employee id (unique, optional until set) and a joining date. A **Teacher
record** gains a designation and an optional employee id. The Teachers and Managers screens show and edit these fields
(Admin and Director; a Zone Manager sees them but cannot change them). Every change is audited with the prior and
new value. The public interfaces `organization.api.ManagerQueries` and `teacher.api.TeacherDirectory` expose the
designation, employee id and joining date so 013a and 032 never read the tables.

1. Designation list: create, rename, retire and reactivate; a retired designation stays on records that use it and
   cannot be chosen for new ones.
2. Manager: set designation, employee id, joining date. The joining date is the first day the Manager is employed; it
   is not the date the Manager is assigned to a Zone.
3. Teacher: set designation and an optional employee id. A Teacher's reporting date is **not** stored here: it is the
   first School assignment (spec 012, decision D2).
4. Existing Managers and Teachers keep working with no designation until one is set; screens flag "designation
   missing" so payroll can be prepared.
5. New permission module `DESIGNATIONS` (View, Create, Edit); the Manager and Teacher edit permissions already cover
   the new fields.

Out of scope: salary amounts (013a), bank details, documents, leave balances, the Teacher status machine.

## Points for `/speckit-clarify`

- The first list of designations for Teachers and for Managers (names).
- Is the employee id typed by hand, or generated (for example HLS-0001)? Must it be unique across Teachers and
  Managers?
- Can a designation change over time (a promotion), and must the history be kept with the effective date? (013a needs
  the designation on a date.)
- Should the joining date of an existing Manager be required before their first payroll?
