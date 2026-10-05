# 016 Campus Recruitment, Job Offers and Induction: `/speckit-specify` input

2026-10-05. Roadmap row 016 (high priority; runs in parallel with 012/022 and with 023). Replaces the "recruitment"
half of the old combined HR-calendars spec. Source requirements: `HLS Teacher Management System — Requirements.md` §8
(one-month induction) and §9 (campus hiring); `docs/hls-speckit-specify-inputs.md` §12.

Depends on 005 (Teacher records and status, scope), 003 (audit), 010 (notifications, optional). Feeds 012 (the
Teachers that are mapped to MoU positions), 008 (training-day attendance) and 013 (payroll: training days).

The business process: **college drive scheduled → candidates interviewed → selected candidates get a Job Offer with a
package → accepted → Teacher record created in "Recruited" status → one-month induction (training) → signed off,
"Ready to deploy" → mapped to a School's MoU position (012)**.

## Feature description (paste as the argument to `/speckit-specify`)

016-campus-recruitment: HLS recruits teachers from colleges, in parallel with its school marketing (spec 023), and
this spec captures that whole flow. Module `recruitment` (plus the induction part of `training`).

1. **Recruitment calendar.** Admin, Director and Zone Managers schedule campus drives: college (a reusable College
   record: name, city, contact, past drives), date or dates, venue, the HLS staff attending as interviewers (Director,
   Zone Managers, Admin, or other HLS users), and a status (planned, held, cancelled). A calendar (month view and
   list) shows drives, with a person's own drives highlighted.
2. **Candidates and outcome.** For each drive, candidates are recorded (name, phone, email, degree, year, notes; bulk
   import from a sheet) with an interview outcome: selected, waitlisted or rejected, recorded with who and when.
   A waitlisted candidate can be moved to selected later.
3. **Job offers with a package.** A selected candidate gets a Job Offer: role, the package HLS offers (monthly salary
   during and after induction, any induction stipend, allowances), offer date, response deadline and status
   (offered → accepted / declined / expired). The package is the salary HLS pays the Teacher; it is separate from what
   a School pays under an MoU (012). Offers are never edited after they are sent; a changed package is a new offer
   that supersedes the old one, so the history stays.
4. **Accepted offer becomes a Teacher.** On acceptance the system creates the Teacher record in "Recruited" status
   (spec 005) with the candidate's details and the offered salary in the Teacher's salary history, with no re-entry,
   and enrols them in the next one-month induction batch.
5. **One-month induction.** Admin and Director create induction batches (dates, trainer, venue physical or virtual).
   Recruits are enrolled, their daily attendance is recorded (training days feed spec 008's training-day figures
   and 013), and at the end each recruit is signed off (completed / not completed, with remarks). A completed recruit
   moves to status "Ready to deploy" and is the intended next step before mapping to a School position in spec 012 (012 does not enforce it; whether to is a clarify question below). A recruit who does
   not complete can be extended into the next batch or released, with a reason.
6. **Dashboard.** Admin and Director see drives scheduled vs held, candidates interviewed vs selected vs offered vs
   joined vs completed induction, per college and per season, and recruits waiting to be deployed (a list that the
   marketing and mapping work reads from).

Out of scope: weekend and monthly refresher training (a later spec, roadmap 024), the School side (023, 012), payroll
for the stipend (013 reads it), candidate self-service portal, resume parsing, mobile screens.

## Role & Permission Impact (to be developed in the spec)

- Admin, Director: everything, org-wide.
- Manager (Zone Manager): schedule and view drives, record candidates and outcomes for drives they attend, view
  offers they are involved in; no authority to send offers or sign off induction unless `/speckit-clarify` decides
  otherwise.
- Teacher: ACCOUNT only (a recruit sees nothing new until they are a Teacher).
- System: none (no business data).
- New permission modules: `RECRUITMENT` (drives, candidates, offers), `INDUCTION` (batches, attendance, sign-off).
  No new role: the constitution fixes five roles.

## Points for `/speckit-clarify`

- Who may send an offer and set the package: Admin and Director only, or a Zone Manager within a salary band?
- Is the package one monthly salary, or induction stipend plus post-deployment salary? Is there a bond or notice
  period to record?
- Does an accepted candidate get a user login straight away (to see the offer and training), or only once "Ready to
  deploy"?
- What if the candidate record already exists as a Teacher (re-hire)? Matching rule (phone/email).
- Offer expiry: automatic at the deadline, and can an expired offer be reopened?
- Is induction attendance part of the Teacher attendance grid (008, code `T`) or a separate induction register that
  rolls up into it?
- Is a College a Zone-bound record, and does a Zone Manager see only colleges of their Zone?
- Season definition (academic year, drive batch) for the dashboard.
- Should a recruit be blocked from mapping (012) until "Ready to deploy"? 012 currently maps any non-exited Teacher, so this would be a rule added in 016.

## Additions from `docs/HLS-core-business-flow.md` (2026-10-05)

Decided: D1 (012 refuses to map a Teacher in training; induction sign-off makes the Teacher active), D2 (salary starts
at the first School assignment; no salary history entry at acceptance), D3 (no new Teacher statuses), D4 (generate the
offer letter and track its status). Still to add to the spec through `/speckit-clarify`:

- College workflow (identified, contacted, Placement Officer discussion, date confirmed), Placement Officer, assigned manager, next recruitment date.
- Structured group speaking assessment with scores (English, communication, correctness, expression, confidence, overall suitability) and qualified or not qualified, alongside selected, waitlisted, rejected.
- Joining ratio (actual joiners / selected x 100); funnel steps "School joined" and "Active Teachers".
- Training assessment score; optional food, accommodation and cost capture (policy unconfirmed).
- Teacher fields: college, qualification, recruitment batch, training batch.
