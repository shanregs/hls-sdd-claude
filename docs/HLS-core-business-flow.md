# Company Operations Management System

## Business Requirements, Modules & End-to-End Workflow

---

## 1. Purpose of the System

The company needs a centralized management system to monitor and control its complete operational cycle:

**Recruitment → Candidate Selection → Offer → Training → School Acquisition → Teacher Allocation → School Operations → Teacher Monitoring → Substitution → Transfer/Replacement → Reporting**

The primary objective is to allow management to sit in one place and understand:

* What each manager is doing
* What each manager is supposed to do
* Which schools are being approached
* Which schools have been visited
* Which schools are in follow-up
* Which schools are confirmed
* Whether an MoU has been signed
* Which teachers are assigned to which schools
* Whether teachers are performing properly
* Which teachers require correction
* Which teachers have been transferred or replaced
* Which teachers are absent
* Who is handling substitutions
* What content has been prepared and submitted
* What training has been conducted
* Which tasks are pending
* Why a task is pending
* Whether a completed task has actually been completed
* Historical activity of managers, schools and teachers

The system should reduce dependency on phone calls, verbal communication, delayed reports and manual follow-ups.

---

# 2. Current Business Problem

The discussion indicates that management currently has limited centralized visibility.

Managers may verbally communicate:

* "I am going to this school."
* "I am preparing content."
* "I visited this school."
* "I will follow up."
* "I completed the work."

However, management does not always have immediate visibility into:

* Whether the activity actually happened
* When it happened
* What was discussed
* What the outcome was
* What needs to happen next
* Whether the manager submitted the required output

There is also an issue where reports may be submitted in batches rather than immediately.

Therefore, the system should create a structured activity trail.

---

# 3. Core Business Objective

The system should provide:

> **Centralized visibility + task accountability + operational tracking + historical records + management dashboard**

Every important business activity should move through a structured status.

For example:

```text
Assigned
   ↓
Acknowledged
   ↓
In Progress
   ↓
Completed
   ↓
Verified
```

If a task is not completed:

```text
Pending
   ↓
Reason Required
   ↓
Follow-up
   ↓
Escalation if required
```

---

# 4. Main Modules

The proposed system should contain the following major modules:

1. User & Manager Management
2. Area / Territory Management
3. Manager Task Management
4. School Management
5. School Marketing / Acquisition
6. School Visit Management
7. MoU Management
8. Content Management
9. College Management
10. Recruitment Drive Management
11. Candidate Management
12. Candidate Assessment
13. Offer Letter Management
14. Training Management
15. Teacher Management
16. Teacher-School Allocation
17. Teacher Attendance / Absence
18. Substitute Management
19. Classroom Observation
20. Principal Feedback
21. Teacher Performance Management
22. Teacher Transfer / Replacement
23. Incentive Management
24. Notifications / Escalations
25. Management Dashboard
26. Reports & Analytics
27. Activity History / Audit Trail

---

# 5. Organization Structure

The system should support the following structure:

```text
Company
   │
   ├── Management / Director
   │
   └── Managers
          │
          ├── Assigned Area
          │
          ├── Assigned Schools
          │
          ├── Assigned Teachers
          │
          ├── Recruitment Activities
          │
          ├── School Marketing
          │
          ├── Content Preparation
          │
          ├── Training
          │
          ├── Teacher Allocation
          │
          ├── Teacher Observation
          │
          └── Substitute Arrangement
```

Managers are responsible for designated geographical areas.

For example:

* Tiruppur Manager → Tiruppur area
* Salem Manager → Salem area
* Other Managers → Their designated areas

---

# 6. Manager Management

## Manager Information

The system should maintain:

* Manager ID
* Manager name
* Phone
* Email
* Designation
* Assigned area
* Joining date
* Status
* Assigned schools
* Assigned teachers
* Assigned tasks

## Manager Dashboard

Each manager should see:

* Today's tasks
* Upcoming tasks
* Overdue tasks
* School visits
* Follow-ups
* Recruitment activities
* Content tasks
* Training activities
* Teacher observations
* Substitute requirements
* Pending actions

Management should see the same information at an organizational level.

---

# 7. Manager Responsibilities

The discussion identifies several major responsibilities for managers.

### 7.1 Recruitment

Managers participate in:

* College approach
* Recruitment drives
* Candidate assessment
* Candidate selection
* Recruitment follow-up

### 7.2 School Marketing

Managers:

* Identify schools
* Visit schools
* Meet school management / Principal
* Present company materials
* Follow up
* Move interested schools toward final stage
* Coordinate final closure

### 7.3 Content Preparation

Managers:

* Prepare content
* Submit the actual content
* Maintain content records
* Make corrections if required

Simply marking:

> "Content preparation in progress"

is not enough.

The actual prepared content must be submitted.

### 7.4 Training

Managers conduct training for selected candidates / teachers.

### 7.5 Teacher Allocation

Managers decide:

> Which teacher should be assigned to which school.

### 7.6 Teacher Monitoring

Managers observe teachers in schools and collect feedback.

### 7.7 Substitute Arrangement

Managers arrange substitutes when teachers are absent.

If no substitute is available, the manager may personally go to the school/class.

---

# 8. School Acquisition / Marketing Workflow

## Overall Workflow

```text
Manager Assigned Area
        ↓
Identify Target Schools
        ↓
Create School Prospect
        ↓
Plan Visit
        ↓
Visit School
        ↓
Meet Principal / Management
        ↓
Present Company Materials
        ↓
Record Discussion
        ↓
Follow-up
        ↓
Interested?
   ┌────┴────┐
   No        Yes
   │          │
Follow-up   Proposal /
            Negotiation
               ↓
          Final Stage
               ↓
        Management Review
               ↓
          MoU / Closure
               ↓
         Active School
```

---

# 9. School Master Data

Each school should have:

* School ID
* School name
* Address
* Area
* District
* Contact person
* Principal name
* Principal contact
* Management contact
* Phone
* Email
* Assigned manager
* School status
* Number of required teachers
* Number of assigned teachers
* Start date
* MoU status
* Remarks

---

# 10. School Status

Recommended school statuses:

```text
Prospect
   ↓
Contacted
   ↓
Visit Planned
   ↓
Visit Completed
   ↓
Follow-up
   ↓
Interested
   ↓
Negotiation
   ↓
Final Stage
   ↓
MoU Signed
   ↓
Active
```

Alternative outcomes:

```text
Rejected
Lost
On Hold
```

---

# 11. School Visit Management

Every school visit should be recorded.

## Visit Information

* School
* Manager
* Date
* Time
* Purpose
* People met
* Discussion notes
* Outcome
* Next action
* Next follow-up date
* Attachments
* Status

## Visit Status

```text
Planned
   ↓
Confirmed
   ↓
Visited
   ↓
Report Submitted
   ↓
Follow-up Required
```

The system should not allow a manager to simply say that a visit was planned.

The actual visit outcome should be recorded.

---

# 12. Manager Marketing Planning

The current process has limited visibility into exactly when managers plan to visit schools.

The system should therefore support a clear calendar.

Example:

| Date      | Manager   | School   | Activity         | Status  |
| --------- | --------- | -------- | ---------------- | ------- |
| Monday    | Manager A | School A | Marketing Visit  | Planned |
| Tuesday   | Manager A | School B | Follow-up        | Planned |
| Wednesday | Manager A | School C | Final Discussion | Planned |
| Thursday  | Manager B | School D | New Visit        | Planned |

Managers should submit their activity plan.

Management should be able to view:

* Today's plan
* Tomorrow's plan
* This week's plan
* Completed activities
* Missed activities
* Rescheduled activities

---

# 13. School Closure / MoU

When a school reaches the final stage:

```text
School Prospect
      ↓
Multiple Visits / Follow-ups
      ↓
Interested
      ↓
Negotiation
      ↓
Final Stage
      ↓
MoU
      ↓
Confirmed School
```

The system should store:

* MoU status
* MoU date
* School
* Manager
* Agreement document
* Start date
* End date
* Number of teachers required
* Notes

---

# 14. Manager Incentive

The discussion mentions:

> ₹5,000 incentive per confirmed/closed school.

Therefore the system should track:

* Manager
* School
* Closure date
* Closure status
* Incentive amount
* Eligibility
* Approval status
* Payment status

Workflow:

```text
School Confirmed
      ↓
MoU / Closure Verified
      ↓
Incentive Eligible
      ↓
₹5,000
      ↓
Approved
      ↓
Paid
```

---

# 15. Content Management

Content preparation is a manager responsibility.

## Content Workflow

```text
Content Task Assigned
       ↓
Manager Starts
       ↓
Content Prepared
       ↓
Content Uploaded / Submitted
       ↓
Review
       ↓
Approved?
   ┌────┴────┐
   No        Yes
   │          │
Revision    Approved
   │
Resubmit
```

## Content Record

* Content ID
* Title
* Category
* Subject
* Manager
* Created date
* Version
* File
* Description
* Status
* Reviewer
* Comments
* Approval date

The important requirement is:

**The system must capture the actual content output, not only the activity status.**

---

# 16. College Management

Recruitment starts with colleges.

The system should maintain:

* College name
* Location
* Contact person
* Placement Officer
* Phone
* Email
* Assigned manager
* Recruitment history
* Last visit
* Next recruitment date
* Remarks

---

# 17. Recruitment Drive Management

## Recruitment Workflow

```text
College Identified
       ↓
College Contacted
       ↓
Placement Officer Discussion
       ↓
Recruitment Date Confirmed
       ↓
Campus Visit
       ↓
Candidate List
       ↓
Candidate Assessment
       ↓
Selection
       ↓
Offer Letter
       ↓
Training
```

---

# 18. Candidate Assessment

The company uses a group-based speaking assessment.

Candidates are given:

* A topic
* Headline
* Subject

Candidates speak in front of the group.

Managers evaluate:

* English speaking ability
* Communication
* Correctness
* Ability to express themselves
* Confidence
* Overall suitability

The college provides the candidate list.

The manager then marks candidates as:

```text
Qualified
Not Qualified
```

Selected candidates receive an offer letter.

---

# 19. Candidate Management

Candidate data:

* Candidate ID
* Name
* College
* Course
* Phone
* Email
* Recruitment drive
* Assessment score
* English score
* Communication score
* Selection status
* Offer status
* Training status
* Joining status
* Assigned school
* Joining date
* Exit date
* Exit reason

---

# 20. Recruitment Funnel

The dashboard should show:

```text
Candidates Identified
        ↓
Candidates Assessed
        ↓
Candidates Selected
        ↓
Offers Issued
        ↓
Training Joined
        ↓
Training Completed
        ↓
School Joined
        ↓
Active Teachers
```

This is especially important because the discussion indicates a very low joining ratio in previous recruitment.

Therefore, the system should calculate:

### Joining Ratio

```text
Actual Joiners ÷ Selected Candidates × 100
```

Example:

```text
1,100 recruited
30 joined

Joining Ratio = 30 / 1,100 × 100
```

---

# 21. Offer Letter Management

Selected candidates receive an offer letter.

Designation:

**Trainee / English Trainer**

Offer letter should include:

* Candidate name
* Designation
* Salary
* Terms
* Training information
* Expected joining
* Other applicable details

System status:

```text
Selected
   ↓
Offer Generated
   ↓
Offer Issued
   ↓
Accepted / Pending
   ↓
Training
```

---

# 22. Training Management

After selection, candidates need to attend training.

The discussion indicates approximately one month of training.

## Training Workflow

```text
Offer Issued
      ↓
Training Batch
      ↓
Training Start
      ↓
Daily / Periodic Training
      ↓
Training Completion
      ↓
Ready for School
```

Training should track:

* Training batch
* Trainer / Manager
* Candidate
* Start date
* End date
* Attendance
* Training status
* Assessment
* Completion status
* Remarks

---

# 23. Training and Salary Rule

Based on the discussion:

* Training is free.
* Salary does not start merely because an offer letter was issued.
* Salary starts when the candidate reports to the school.
* School work begins around June.
* Teachers generally work from June to March/April.
* The discussion refers to this as approximately one year / around ten months.

This business rule should be confirmed before being hard-coded because the discussion uses both "one year" and "June to March/April" terminology.

---

# 24. Food / Accommodation During Training

The discussion mentions arrangements for food and accommodation during the training period.

The system should therefore optionally capture:

* Training location
* Food arrangement
* Accommodation arrangement
* Cost
* Payment responsibility
* Candidate status

The exact financial policy should be confirmed before implementation.

---

# 25. Teacher Management

Once a candidate joins and becomes active, they should become a teacher record.

Teacher master data:

* Teacher ID
* Name
* Phone
* Email
* College
* Qualification
* Recruitment batch
* Training batch
* Designation
* Joining date
* Current school
* Manager
* Status

Teacher statuses:

```text
Selected
Offer Issued
Training
Training Completed
Available
Assigned
Active
Absent
Under Review
Transferred
Replaced
Exited
```

---

# 26. Teacher-School Allocation

Managers are responsible for deciding which teacher goes to which school.

## Workflow

```text
Teacher Ready
      ↓
School Requirement
      ↓
Manager Reviews Availability
      ↓
Teacher Assigned
      ↓
School Informed
      ↓
Teacher Reports
      ↓
Assignment Active
```

Allocation record:

* Teacher
* School
* Manager
* Start date
* End date
* Assignment status
* Reason
* Remarks

---

# 27. Teacher Assignment History

The system should never simply overwrite the current school.

It should maintain historical assignments.

Example:

| Teacher   | School   | Start | End | Reason             |
| --------- | -------- | ----- | --- | ------------------ |
| Teacher A | School X | June  | Oct | Initial assignment |
| Teacher A | School Y | Oct   | Mar | Transfer           |

This allows management to see the full history.

---

# 28. Teacher Performance Monitoring

Managers must monitor teacher performance.

## Observation Workflow

```text
Teacher Active
      ↓
Observation Due
      ↓
Manager Visits School
      ↓
Principal Permission
      ↓
Classroom Observation
      ↓
Manager Records Findings
      ↓
Principal Feedback
      ↓
Corrective Action
      ↓
Follow-up Observation
```

Managers may:

* Sit at the back of the classroom
* Observe the teacher during teaching
* Assess teaching quality
* Take feedback from the Principal
* Provide feedback
* Correct issues

---

# 29. Classroom Observation Form

The system should provide a structured observation form.

### Basic Information

* Teacher
* School
* Manager
* Date
* Class
* Subject
* Observation duration

### Evaluation

* English communication
* Teaching ability
* Content delivery
* Classroom management
* Student engagement
* Preparation
* Confidence
* Punctuality
* Overall performance

### Feedback

* Manager comments
* Principal comments
* Strengths
* Areas for improvement
* Corrective action
* Follow-up date

---

# 30. Principal Feedback

The system should separately capture Principal feedback.

Workflow:

```text
Manager Visits
      ↓
Principal Permission
      ↓
Class Observation
      ↓
Principal Feedback
      ↓
Manager Records Feedback
      ↓
Action Required?
```

Principal feedback can indicate:

* Good performance
* Needs improvement
* Attendance issue
* Teaching issue
* Communication issue
* Behaviour issue
* Other concern

---

# 31. Teacher Performance Correction

If the teacher is not performing properly:

```text
Issue Identified
      ↓
Feedback
      ↓
Correction / Coaching
      ↓
Follow-up
      ↓
Improved?
   ┌────┴────┐
  Yes        No
   │          │
Continue   Transfer /
           Replace
```

---

# 32. Teacher Transfer / Replacement

The discussion provides an example where a school requested that a teacher be removed because of performance.

The manager then reshuffles teachers.

Therefore, the system must support:

* Teacher transfer
* Teacher replacement
* School replacement request
* Reason
* Effective date
* Replacement teacher
* Previous teacher
* Manager approval
* New assignment

---

# 33. Replacement Workflow

```text
School Reports Problem
        ↓
Manager Reviews
        ↓
Observation / Validation
        ↓
Correction Attempt
        ↓
Still Not Suitable?
        ↓
Transfer / Replace
        ↓
Find Available Teacher
        ↓
Assign Replacement
        ↓
Update School
        ↓
Update Teacher History
```

---

# 34. Substitute Management

Substitution is a manager responsibility.

## Workflow

```text
Teacher Absent
      ↓
Absence Recorded
      ↓
Manager Notified
      ↓
Check Available Teachers
      ↓
Assign Substitute
      ↓
School Informed
      ↓
Substitution Completed
```

If no substitute is available:

```text
No Substitute
      ↓
Manager Goes Personally
      ↓
Class Managed
```

---

# 35. Attendance / Absence

The system should record:

* Teacher
* School
* Date
* Absence type
* Reason
* Reported by
* Substitute required
* Substitute assigned
* Manager action
* Status

---

# 36. Manager Daily Task Management

Each manager should have a task list.

Possible tasks:

* Visit school
* Follow up with school
* Contact Principal
* Prepare content
* Submit content
* Conduct training
* Observe teacher
* Collect Principal feedback
* Arrange substitute
* Recruit candidates
* Conduct assessment
* Assign teacher
* Transfer teacher
* Replace teacher

Each task should have:

* Task
* Assigned person
* Date
* Priority
* Due date
* Status
* Completion date
* Evidence
* Remarks

---

# 37. Task Accountability

Every task should follow:

```text
Task Assigned
      ↓
Manager Acknowledges
      ↓
Manager Starts
      ↓
Manager Completes
      ↓
Output / Evidence Submitted
      ↓
Manager / Management Verifies
```

If not completed:

```text
Overdue
   ↓
Reason Required
   ↓
Follow-up
   ↓
Escalation
```

---

# 38. Notifications

The system should generate notifications for:

* New task
* Task assignment
* Upcoming school visit
* Overdue task
* Follow-up due
* Training starting
* Training ending
* Teacher absent
* Substitute required
* Observation due
* Poor performance
* School requiring replacement
* MoU pending
* MoU completed
* Incentive eligible
* Manager missed activity

---

# 39. Management Dashboard

The management dashboard should provide a single-page overview.

## Today's Overview

```text
Today's School Visits
Today's Manager Tasks
Today's Training
Today's Teacher Absences
Today's Substitution Requirements
Today's Observations
Today's Follow-ups
```

---

# 40. School Dashboard

Show:

```text
Total Schools
Active Schools
Prospects
Visits Pending
Follow-ups Pending
Negotiations
Final Stage
MoUs Pending
MoUs Signed
Schools Requiring Teacher
Schools Requiring Replacement
```

---

# 41. Recruitment Dashboard

Show:

```text
Colleges
Recruitment Drives
Candidates
Assessed
Selected
Offers Issued
Training
Training Completed
School Joined
Active Teachers
Exited Teachers
Joining Ratio
```

---

# 42. Teacher Dashboard

Show:

```text
Total Teachers
Active Teachers
Training
Available
Assigned
Absent
Under Review
Transferred
Replaced
Exited
```

---

# 43. Manager Performance Dashboard

Management should be able to compare managers.

Metrics:

* Schools contacted
* School visits
* Follow-ups
* Schools closed
* MoUs
* Incentives
* Recruitment drives
* Candidates selected
* Training conducted
* Content submitted
* Teacher observations
* Performance issues resolved
* Substitutions handled
* Pending tasks
* Overdue tasks

---

# 44. Area-Wise Dashboard

Management should be able to filter by area.

Example:

```text
Salem
   ├── Manager
   ├── Schools
   ├── Prospects
   ├── Active Schools
   ├── Teachers
   ├── Vacancies
   ├── Observations
   └── Pending Tasks
```

Same structure for:

* Tiruppur
* Salem
* Other assigned areas

---

# 45. Reports

The system should provide reports such as:

### School Reports

* School pipeline
* School visits
* MoUs
* Active schools
* School-wise teacher allocation

### Recruitment Reports

* College-wise recruitment
* Candidate selection
* Offer conversion
* Training conversion
* Joining ratio

### Teacher Reports

* Teacher list
* Teacher-school allocation
* Teacher attendance
* Teacher observation
* Performance
* Transfers
* Exits

### Manager Reports

* Daily activity
* Weekly activity
* Monthly activity
* Pending tasks
* Completed tasks
* School acquisition
* Recruitment
* Teacher management

---

# 46. Audit Trail

Every important action should be logged.

Example:

```text
10:30 AM
Manager A created School ABC

11:15 AM
Manager A scheduled visit

03:00 PM
Visit marked completed

03:15 PM
Visit report submitted

Next Day
Follow-up scheduled

Next Week
School moved to Negotiation

Following Week
MoU marked Signed
```

Management should be able to see the complete history.

---

# 47. Role-Based Access

Recommended roles:

## Director / Management

Full visibility.

Can:

* View all areas
* View all managers
* View all schools
* View all teachers
* View recruitment
* View reports
* Approve important actions
* Monitor performance

## Manager

Can:

* View assigned area
* Manage assigned schools
* Manage assigned teachers
* Create visits
* Complete tasks
* Recruit
* Train
* Allocate teachers
* Conduct observations
* Arrange substitutes
* Submit content

## Optional Admin

Can:

* Manage users
* Configure master data
* Manage system settings
* Maintain reports

---

# 48. Important Business Rules

### Rule 1

Managers have assigned geographical areas.

### Rule 2

Managers decide their marketing visit schedule, but the schedule should be recorded in the system.

### Rule 3

A school visit must have an outcome.

### Rule 4

A school should move through defined statuses.

### Rule 5

A confirmed school can generate incentive eligibility.

### Rule 6

Candidate selection happens through assessment.

### Rule 7

Selected candidates receive an offer letter.

### Rule 8

Offer issued does not automatically mean active employee.

### Rule 9

Training must be completed before school assignment.

### Rule 10

Managers assign teachers to schools.

### Rule 11

Managers monitor teacher performance.

### Rule 12

Classroom observations require school/Principal permission as applicable.

### Rule 13

Principal feedback should be recorded.

### Rule 14

Performance problems require corrective action.

### Rule 15

Teachers may be transferred or replaced.

### Rule 16

Teacher assignments must maintain historical records.

### Rule 17

Teacher absence can create a substitution requirement.

### Rule 18

If no substitute is available, the manager may personally handle the class.

### Rule 19

Completed tasks should include the actual output/evidence where applicable.

### Rule 20

Pending tasks should require a reason.

---

# 49. End-to-End Business Workflow

## Phase 1 — Area & Manager Setup

```text
Management
   ↓
Create Managers
   ↓
Assign Areas
   ↓
Assign Responsibilities
```

---

## Phase 2 — School Acquisition

```text
Manager
   ↓
Identify School
   ↓
Create Prospect
   ↓
Schedule Visit
   ↓
Visit
   ↓
Record Discussion
   ↓
Follow-up
   ↓
Negotiation
   ↓
MoU
   ↓
Active School
```

---

## Phase 3 — Recruitment

```text
College
   ↓
Recruitment Date
   ↓
Candidate List
   ↓
Speaking Assessment
   ↓
Evaluation
   ↓
Selection
   ↓
Offer Letter
```

---

## Phase 4 — Training

```text
Selected Candidate
       ↓
Training Batch
       ↓
1 Month Training
       ↓
Assessment
       ↓
Training Completed
```

---

## Phase 5 — Teacher Deployment

```text
Trained Teacher
       ↓
School Requirement
       ↓
Manager Allocation
       ↓
School Joining
       ↓
Active Teacher
```

---

## Phase 6 — Teacher Operations

```text
Teacher Working
       ↓
Attendance
       ↓
Classroom Observation
       ↓
Principal Feedback
       ↓
Performance Evaluation
```

---

## Phase 7 — Performance Management

```text
Good
 ↓
Continue

Needs Improvement
 ↓
Feedback
 ↓
Correction
 ↓
Follow-up

Poor / School Rejects
 ↓
Transfer / Replacement
 ↓
New Teacher Allocation
```

---

## Phase 8 — Daily Substitution

```text
Teacher Absent
       ↓
Manager Alert
       ↓
Find Substitute
       ↓
Assign Substitute
       ↓
Class Covered
```

If no substitute:

```text
Manager Handles Class
```

---

# 50. Overall System Flow

```text
                         MANAGEMENT
                              │
                              ▼
                     AREA / MANAGER SETUP
                              │
              ┌───────────────┴────────────────┐
              │                                │
              ▼                                ▼
       SCHOOL ACQUISITION                 RECRUITMENT
              │                                │
              ▼                                ▼
        School Prospect                  College Prospect
              │                                │
              ▼                                ▼
         School Visits                   Recruitment Drive
              │                                │
              ▼                                ▼
       Follow-up / MoU                   Candidate Assessment
              │                                │
              ▼                                ▼
        ACTIVE SCHOOL                    Candidate Selection
              │                                │
              │                                ▼
              │                          Offer Letter
              │                                │
              │                                ▼
              │                            Training
              │                                │
              └───────────────┬────────────────┘
                              ▼
                       TEACHER POOL
                              │
                              ▼
                    MANAGER ALLOCATION
                              │
                              ▼
                        SCHOOL JOINING
                              │
                              ▼
                     TEACHER OPERATIONS
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
            Attendance    Observation   Substitution
                │             │             │
                │             ▼             │
                │       Principal Feedback  │
                │             │             │
                └─────────────┼─────────────┘
                              ▼
                    PERFORMANCE MANAGEMENT
                              │
                   ┌──────────┴──────────┐
                   ▼                     ▼
                Continue           Correction
                                         │
                              ┌──────────┴──────────┐
                              ▼                     ▼
                           Improve          Transfer / Replace
                              │                     │
                              └──────────┬──────────┘
                                         ▼
                                  ACTIVE OPERATIONS
                                         │
                                         ▼
                              MANAGEMENT DASHBOARD
```

---

# 51. Critical MVP Features

If the first version needs to be developed as an MVP, the highest-priority features should be:

## Priority 1 — Must Have

1. Login / Users
2. Manager Management
3. Area Assignment
4. School Management
5. School Visit Tracking
6. School Pipeline
7. Manager Task Management
8. Recruitment
9. Candidate Management
10. Candidate Assessment
11. Offer Tracking
12. Training
13. Teacher Management
14. Teacher-School Allocation
15. Teacher Observation
16. Principal Feedback
17. Substitute Management
18. Teacher Transfer / Replacement
19. Management Dashboard
20. Notifications / Pending Tasks

## Priority 2

21. Content Management
22. MoU Document Management
23. Incentive Management
24. Advanced Reports
25. Manager Performance Analytics
26. Area-wise Analytics

## Priority 3

27. Advanced automation
28. Mobile application
29. GPS/location verification
30. Automated reminders
31. Advanced analytics
32. Integration with communication tools

---

# 52. Key KPIs

The system should calculate the following KPIs.

## School Acquisition

```text
Schools Contacted
Schools Visited
Interested Schools
Negotiations
MoUs
Conversion Rate
```

## Recruitment

```text
Candidates
Selected
Offers
Training Joined
Training Completed
School Joined
Joining Ratio
```

## Teacher Operations

```text
Active Teachers
Teacher Attendance
Observations
Performance Issues
Issues Resolved
Transfers
Replacements
Exits
```

## Manager Performance

```text
Tasks Completed
Tasks Pending
Tasks Overdue
School Visits
Schools Closed
Recruitment Activity
Content Submitted
Training Conducted
Observations Completed
Substitutions Managed
```

---

# 53. The Central Management Dashboard Should Answer These Questions

When management opens the system, they should immediately be able to answer:

### Managers

* Where is each manager?
* What are they supposed to do today?
* What did they do yesterday?
* What is pending?
* What is overdue?

### Schools

* Which schools are being approached?
* Which schools were visited?
* Which schools need follow-up?
* Which schools are close to signing?
* Which MoUs are pending?
* Which schools are active?

### Teachers

* Which teacher is assigned to which school?
* Which teachers are absent?
* Which teachers are under observation?
* Which teachers have performance problems?
* Which teachers need replacement?
* Where has each teacher previously worked?

### Recruitment

* Which colleges are being approached?
* How many candidates were assessed?
* How many were selected?
* How many received offers?
* How many joined training?
* How many actually joined schools?

### Operations

* Which school needs a substitute today?
* Who is handling the substitution?
* Which teacher needs observation?
* Which Principal has provided negative feedback?
* What corrective actions are pending?

### Accountability

* Who was assigned the task?
* When was it due?
* Was it completed?
* What evidence was submitted?
* If not completed, why?
* Who needs to follow up?

---

# 54. Final Product Vision

The system should eventually work as a **central operations control center**.

Instead of:

```text
Manager → Phone Call → Director
Manager → WhatsApp → Director
Manager → Delayed Report → Director
Manager → Verbal Update → Director
```

the company should operate as:

```text
Manager
   ↓
System
   ↓
Real-Time Activity
   ↓
Dashboard
   ↓
Management
   ↓
Decision / Action
```

The final goal is:

> **Management should be able to open the system from anywhere and understand the complete current state of the business without having to individually chase managers for updates.**

---

# 55. Recommended Core Entities / Database Structure

The initial database should include at least:

```text
Users
Managers
Areas
Schools
School Contacts
School Visits
School Follow-ups
School Proposals
MoUs
Incentives

Colleges
Recruitment Drives
Candidates
Candidate Assessments
Offer Letters
Training Batches
Training Attendance

Teachers
Teacher Assignments
Teacher Attendance
Substitution Requests
Substitution Assignments
Classroom Observations
Principal Feedback
Performance Issues
Corrective Actions
Teacher Transfers
Teacher Exits

Tasks
Task Comments
Notifications
Documents
Content
Reports
Activity Logs
```

---

# 56. Important Items Requiring Business Confirmation

The discussion gives strong direction, but the following should be confirmed before development because the conversation does not fully define them:

1. Exact salary structure by designation.
2. Exact duration and legal nature of the teacher commitment.
3. Whether the June–March period is legally considered one year.
4. Exact training dates every year.
5. Food/accommodation payment responsibility and amount.
6. Exact teacher attendance policy.
7. Exact observation frequency.
8. Whether Principal approval is mandatory for every observation.
9. Who approves teacher transfers.
10. Who approves teacher replacement.
11. Who approves MoUs.
12. Who approves manager incentives.
13. Exact ₹5,000 incentive rules and exceptions.
14. Whether managers need GPS/location verification for visits.
15. Whether photographs/evidence are required for school visits.
16. Whether teachers will have access to the system.
17. Whether schools/Principals will have portal access.
18. Whether salary/payroll needs to be included in the first version.
19. Whether offer letters should be generated automatically.
20. Whether WhatsApp/SMS/email notifications are required.

These should be treated as **open requirements**, not assumptions.

---

# 57. Final End-to-End Process

The complete business process can therefore be summarized as:

```text
                    MANAGEMENT
                        │
                        ▼
                 ASSIGN MANAGERS
                        │
                        ▼
                  ASSIGN AREAS
                        │
          ┌─────────────┴─────────────┐
          │                           │
          ▼                           ▼
   SCHOOL ACQUISITION            RECRUITMENT
          │                           │
          ▼                           ▼
   Identify Schools             Identify Colleges
          │                           │
          ▼                           ▼
     School Visits              Recruitment Drive
          │                           │
          ▼                           ▼
       Follow-up                 Candidate List
          │                           │
          ▼                           ▼
       Proposal                 Assessment
          │                           │
          ▼                           ▼
        MoU                     Selection
          │                           │
          ▼                           ▼
    Active School               Offer Letter
          │                           │
          │                           ▼
          │                        Training
          │                           │
          └─────────────┬─────────────┘
                        ▼
                 TEACHER POOL
                        │
                        ▼
                TEACHER ALLOCATION
                        │
                        ▼
                  SCHOOL JOINING
                        │
                        ▼
                 TEACHER WORKING
                        │
           ┌────────────┼────────────┐
           ▼            ▼            ▼
       Attendance   Observation  Substitution
                        │
                        ▼
                 Principal Feedback
                        │
                        ▼
                 Performance Review
                        │
              ┌─────────┴─────────┐
              ▼                   ▼
          Performing          Not Performing
              │                   │
              ▼                   ▼
          Continue          Correct / Coach
                                  │
                         ┌────────┴────────┐
                         ▼                 ▼
                      Improve       Transfer/Replace
                         │                 │
                         └────────┬────────┘
                                  ▼
                           SCHOOL OPERATIONS
                                  │
                                  ▼
                         MANAGEMENT DASHBOARD
                                  │
                                  ▼
                         REPORTS / ANALYTICS
```

## Final Requirement Statement

**The required system is a centralized School, Recruitment, Teacher and Manager Operations Management System that tracks the complete lifecycle from school acquisition and college recruitment through candidate selection, training, teacher allocation, classroom operations, performance monitoring, substitution, transfer/replacement and management reporting. Every activity should be assigned, tracked, completed, documented and visible to management through a centralized dashboard.**
