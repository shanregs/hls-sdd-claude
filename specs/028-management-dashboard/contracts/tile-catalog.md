# Contract: Tile catalog (version 1, fixed order)

Grant means the View grant of the owning permission module (the inbox uses Approve or Process). Zone means the tile can be split by
Zone. "Filter to check" means the target screen may not yet take the filter that reproduces the figure; the tile's task adds it
and the parity test (SC-003) proves it.

| Area | Key | Shows | Period | Grant | Zone | Link (screen and filter) | Source |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Managers | managers.tasks.today | tasks due today | today | Manager Tasks | yes | Tasks, view today | tasks |
| Managers | managers.tasks.upcoming | upcoming | today | Manager Tasks | yes | Tasks, view upcoming | tasks |
| Managers | managers.tasks.overdue | overdue | today | Manager Tasks | yes | Tasks, view overdue | tasks |
| Managers | managers.tasks.awaiting | awaiting verification | today | Manager Tasks | yes | Tasks, view awaiting | tasks |
| Managers | managers.tasks.escalated | escalated | today | Manager Tasks | yes | Tasks, view escalated | tasks |
| Managers | managers.visits | visits planned, completed, missed | month | Marketing | yes | Marketing calendar, status filter (check) | marketing |
| Managers | managers.noCheckIn | no check-in past cut-off (or not a working day) | today | Manager Attendance | yes | Manager Attendance, today, missing (check) | attendance.manager |
| Managers | managers.onLeaveToday | Managers on leave | today | Manager Leave | yes | Manager Leave, approved, covering today | leave.manager |
| Managers | managers.pendingRequests | leave and forgotten check-ins awaiting decision | all time | Manager Leave | yes | Manager Leave, pending | leave.manager |
| Managers | managers.comparison | table: overdue, awaiting, escalated, visits missed, Schools won, days with no check-in | month | Manager Tasks | yes | row opens that Manager's task list; org-wide only | tasks, marketing, attendance.manager |
| Schools | schools.stages | prospects at each stage | all time | Marketing | yes | Pipeline | marketing |
| Schools | schools.finalStage | Final Stage prospects awaiting review | all time | Marketing | yes | Prospects, Final Stage awaiting review (check) | marketing |
| Schools | schools.visits | planned, completed, missed | month | Marketing | yes | Marketing calendar (check) | marketing |
| Schools | schools.winLoss | won, lost, win rate (no data yet on zero) | month | Marketing | yes | Prospects, won / lost | marketing |
| Schools | schools.demandSupply | demand, supply, shortfall (not available when supply is) | all time | Marketing | no | Marketing dashboard | marketing |
| Recruitment | recruitment.funnel | drives held, interviewed, offers issued and accepted, joiners | month | Recruitment | no | Recruitment dashboard | recruitment |
| Recruitment | recruitment.joiningRatio | joining ratio (no data yet on zero) | month | Recruitment | no | Recruitment dashboard | recruitment |
| Recruitment | recruitment.readyToDeploy | recruits ready to deploy | all time | Recruitment | no | Induction, ready to deploy (check) | recruitment |
| Teachers | teachers.byStatus | in training, active, on leave, exited | all time | Teachers | yes | Teachers, status filter | teacher |
| Teachers | operations.substitutions | open (uncovered), assigned, Manager cover today | today | Substitution | yes | Substitutions, today, status filter (check) | substitution |
| Attendance and leave | attendance.awayToday | Teachers absent or on approved leave (or not a working day) | today | Attendance | yes | Attendance, today, away (check) | attendance |
| Attendance and leave | leave.pendingTeacher | pending Teacher leave requests | all time | Leave Management | yes | Leave Management, pending | leave |
| Payroll | payroll.run | state of this month's run and its total (or "no run yet") | month | Payroll | no | the run, or Payroll runs | payroll.run |
| Payroll | payroll.paidOwed | paid so far, still owed | month | Payroll | no | the run, owed tab | payroll.run |
| Payroll | payroll.locks | attendance locked for Teachers, for Managers | month | Payroll | no | Payroll run, month lock | payroll.run |
| Content | content.submitted | content submitted (every version out for review, approved or returned) | today | Content | no | Content, all (amended by spec 029) | content |
| Content | content.awaiting | content waiting for review | today | Content | no | Content, status Submitted | content |
| Content | content.approved | content approved | today | Content | no | Content, status Approved | content |
| Content | content.overdue | content past its due date with nothing submitted | today | Content | no | Content, all | content |

Payroll tiles are organization-wide and carry an amount; they are absent for a caller without Payroll View.

## Inbox kinds

| Kind | Grant | Opens | Source |
| --- | --- | --- | --- |
| MANAGER_LEAVE | Manager Leave, Approve | the leave decision screen | leave.manager |
| FORGOTTEN_CHECK_IN | Manager Leave, Approve | the request on the decision screen | attendance.manager |
| FINAL_STAGE_PROSPECT | Marketing, Approve | the prospect | marketing |
| CONTENT_REVIEW | Content, Approve (not content the caller wrote or submitted) | the content (spec 029) | content |
| PAYROLL_RUN | Payroll, Approve (not a run the caller submitted) | the run | payroll.run |
| TASK_VERIFICATION | Manager Tasks, Approve | the task | tasks |

## Not registered now (arrive with their specs)

Expenses (015), receivables (022), training (024), observations and performance (026), transfers and replacements (027),
incentives (030), and the inbox kinds for expenses, incentives, transfers and MoU documents. Absent, never zero.
