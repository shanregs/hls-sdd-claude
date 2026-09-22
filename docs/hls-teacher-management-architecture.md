# HLS Teacher Management — Target Architecture & Implementation Plan

> **Correction (2026-09-22)**: Wherever this document shows Manager-School-Teacher
> access as two independent assignments (e.g. `ManagerSchoolAssignment` and
> `TeacherAssignment` as separate, unrelated entities in §54-55's sprint plan, or
> `canAccessSchool`-style examples with no Zone in the picture), that no longer
> matches the real operating model. Managers operate over geographic **Zones**
> (one or more Managers per Zone); each School belongs to a Zone and is managed
> by one of that Zone's Managers; a Teacher's accountable Manager is **derived**
> from their School's Manager, not independently assigned. Zone itself is
> School master data (owned by `school`), not Organization data — `organization`
> only owns Zone–Manager assignment, reading Zone/School-Zone data through
> `school`'s public API. See `.specify/memory/constitution.md` Amendments 1.6.0
> and 1.7.0 and `docs/HLS Teacher Management System — Requirements.md` §2/§10/§13
> for the authoritative correction — this file is a loose architecture sketch,
> not re-edited line by line to match.

## 1. Target architecture

Recommended target:

```
                    ┌───────────────────────────┐
                    │       React Web            │
                    │ Desktop / Tablet           │
                    └─────────────┬─────────────┘
                                  │ HTTPS
                    ┌─────────────▼─────────────┐
                    │    React Native Android    │
                    │ Teacher / Manager / Dir.   │
                    └─────────────┬─────────────┘
                                  │
                                  ▼
              ┌─────────────────────────────────────┐
              │       HLS Spring Boot 4.1.1          │
              │          Java 25 Modular Monolith    │
              │                                     │
              │  ┌─────────────┐ ┌───────────────┐  │
              │  │ Identity    │ │ Attendance    │  │
              │  ├─────────────┤ ├───────────────┤  │
              │  │ Teacher     │ │ Payroll       │  │
              │  ├─────────────┤ ├───────────────┤  │
              │  │ School      │ │ SchoolBilling │  │
              │  ├─────────────┤ ├───────────────┤  │
              │  │ Training    │ │ Substitution  │  │
              │  ├─────────────┤ ├───────────────┤  │
              │  │ Expense     │ │ Recruitment   │  │
              │  ├─────────────┤ ├───────────────┤  │
              │  │ Marketing   │ │ Audit         │  │
              │  └─────────────┘ └───────────────┘  │
              │                                     │
              │   REST API / Security / Events      │
              └────────────────┬────────────────────┘
                               │
                 ┌─────────────▼─────────────┐
                 │       PostgreSQL           │
                 │       Canonical Data       │
                 └────────────────────────────┘

                 AWS ap-south-1
                 Docker Compose
                 Single EC2
```

The critical architectural rule is:

> **One Spring Boot application, one deployable JAR/container, many enforced business modules.**

Do not create:

```
attendance-service
payroll-service
school-service
...
```

That would violate Principle V of the constitution.

## 2. Recommended technology baseline

| Area | Choice |
|---|---|
| Java | JDK 25 LTS |
| Backend | Spring Boot 4.1.1 |
| Modular architecture | Spring Modulith 2.1.1 |
| Security | Spring Security 7.x |
| API | REST/JSON |
| Database | PostgreSQL |
| Migration | Flyway |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| API docs | OpenAPI |
| Observability | Actuator + Micrometer |
| Tests | JUnit 5 + Testcontainers + ArchUnit + Modulith |
| Batch concurrency | Java virtual threads |
| Frontend | React + TypeScript |
| Mobile | React Native + TypeScript |
| Mobile first | Android |
| Packaging | Docker |
| Deployment | Docker Compose |
| Server | AWS EC2 |
| Region | ap-south-1 |
| Reverse proxy | Nginx |
| TLS | Let's Encrypt / ACM depending on front-door design |
| CI/CD | GitHub Actions |
| File storage | S3 |
| SMS | India SMS provider |
| Financial currency | INR |
| Date presentation | DD/MM/YYYY |

Spring Boot 4.1.1 officially supports Java versions through Java 26, so Java 25 is a supported choice.

For JWT resource-server validation, Spring Security provides the standard JWT/JWK support and validates claims such as issuer and expiration.

## 3. Repository structure

A single repository:

```
hls-teacher-management/
│
├── constitution.md
│
├── specs/
│   ├── 000-system/
│   │   ├── requirements.md
│   │   ├── acceptance-criteria.md
│   │   └── scenarios.md
│   │
│   ├── 001-identity/
│   ├── 002-school/
│   ├── 003-teacher/
│   ├── 004-attendance/
│   ├── 005-payroll/
│   ├── 006-school-billing/
│   ├── 007-expense/
│   ├── 008-training/
│   ├── 009-substitution/
│   ├── 010-recruitment/
│   ├── 011-marketing/
│   ├── 012-reporting/
│   └── 013-offline-sync/
│
├── docs/
│   ├── adr/
│   │   ├── ADR-0001-modular-monolith.md
│   │   ├── ADR-0002-postgresql.md
│   │   ├── ADR-0003-payroll-calculation.md
│   │   ├── ADR-0004-authentication.md
│   │   ├── ADR-0005-audit-model.md
│   │   ├── ADR-0006-offline-sync.md
│   │   └── ADR-0007-deployment.md
│   │
│   ├── architecture/
│   ├── api/
│   └── operations/
│
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       └── test/
│
├── web/
│   └── hls-web/
│
├── mobile/
│   └── hls-mobile/
│
├── deployment/
│   ├── docker/
│   ├── compose/
│   ├── nginx/
│   └── scripts/
│
├── .github/
│   └── workflows/
│
└── README.md
```

## 4. SDD workflow

Do not start by creating entities and controllers.

Use this workflow:

```
Constitution
     ↓
Business specification
     ↓
Acceptance criteria
     ↓
Domain model
     ↓
Architecture decision
     ↓
API contract
     ↓
Database migration
     ↓
Implementation
     ↓
Unit tests
     ↓
Integration tests
     ↓
Architecture tests
     ↓
Security/scoping tests
     ↓
Deployment
     ↓
Operational verification
```

Every feature should have a specification before implementation.

For example, `specs/005-payroll/` contains:

```
requirements.md
acceptance-criteria.md
formula.md
scenarios.md
api.md
test-cases.md
```

## 5. First SDD specification

Create `specs/005-payroll/requirements.md`. Example:

```markdown
# Payroll Calculation Specification

## Purpose

Calculate monthly teacher salary from canonical attendance,
teacher assignment and salary configuration.

## Actors

- Director
- Admin
- Manager

## Preconditions

- Teacher is active.
- Teacher has a valid school assignment.
- Salary configuration exists.
- Attendance period exists.

## Rules

1. Salary cannot be manually entered into a payroll run.
2. Salary must be derived from canonical attendance.
3. Attendance corrections must affect subsequent calculation.
4. Every payroll run must have a unique run ID.
5. Payroll calculation must be repeatable.
6. Payroll result must identify the source records used.
7. Payroll approval must be separate from calculation.
8. Teacher cannot approve own payroll.
9. Manager cannot approve outside assigned schools.
10. Calculation failures must leave the previous approved payroll untouched.

## Acceptance Criteria

Given a teacher with...
When payroll is calculated...
Then...
```

The important part is that the formula must come from actual HLS operating rules. The salary formula should not be invented from the constitution alone.

## 6. Bounded contexts

Recommended modules:

**Core modules**

```
identity
organization
teacher
school
attendance
payroll
schoolbilling
expense
training
substitution
recruitment
marketing
reporting
audit
```

`organization` and `reporting` are additions beyond the modules explicitly named, because they prevent unrelated modules from becoming dumping grounds.

## 7. Dependency direction

Define this before writing business code.

```
                    ┌──────────────┐
                    │   Identity   │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              ▼                         ▼
        Organization                 Teacher
              │                         │
              └──────────┬──────────────┘
                         ▼
                       School
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
     Attendance       Training     Substitution
          │              │              │
          └──────────────┼──────────────┘
                         ▼
                      Payroll
                         │
                         ▼
                    Reporting

School ───────────────► SchoolBilling
Expense ──────────────► Reporting
Recruitment ──────────► Teacher
Marketing ────────────► Reporting
```

But importantly:

```
Payroll
   ❌ cannot access AttendanceRepository directly

Payroll
   ✅ calls Attendance public API
```

Likewise:

```
SchoolBilling
   ❌ cannot query TeacherRepository

SchoolBilling
   ✅ calls TeacherAssignment API
```

## 8. Spring Modulith package structure

Inside the backend:

```
com.hls.teacher
│
├── HlsApplication.java
│
├── identity
│   ├── IdentityConfiguration.java
│   ├── api
│   └── internal
│
├── organization
│   ├── api
│   └── internal
│
├── teacher
│   ├── api
│   └── internal
│
├── school
│   ├── api
│   └── internal
│
├── attendance
│   ├── api
│   └── internal
│
├── payroll
│   ├── api
│   └── internal
│
├── schoolbilling
│   ├── api
│   └── internal
│
├── expense
│   ├── api
│   └── internal
│
├── training
│   ├── api
│   └── internal
│
├── substitution
│   ├── api
│   └── internal
│
├── recruitment
│   ├── api
│   └── internal
│
├── marketing
│   ├── api
│   └── internal
│
├── reporting
│   ├── api
│   └── internal
│
└── audit
    ├── api
    └── internal
```

The `internal` packages are not public contracts. The `api` package is the module's public surface.

## 9. Maven project

Use Maven initially because it is straightforward for a business application and works well with the Spring ecosystem.

The parent POM starts approximately like this:

```xml
<project>
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.1.1</version>
        <relativePath/>
    </parent>

    <groupId>com.hls</groupId>
    <artifactId>hls-teacher-management</artifactId>
    <version>0.1.0-SNAPSHOT</version>

    <name>HLS Teacher Management</name>

    <properties>
        <java.version>25</java.version>
        <spring-modulith.version>2.1.1</spring-modulith.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.modulith</groupId>
                <artifactId>spring-modulith-bom</artifactId>
                <version>${spring-modulith.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webmvc</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-core</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-core</artifactId>
        </dependency>

        <dependency>
            <groupId>org.flywaydb</groupId>
            <artifactId>flyway-database-postgresql</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>

            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>

        </plugins>
    </build>
</project>
```

The exact starter artifact names should be validated against the final 4.1.1 dependency metadata during project bootstrap; Spring Boot 4.1.1 is the current stable line.

## 10. First application class

```java
package com.hls.teacher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@Modulith
@SpringBootApplication
public class HlsApplication {

    public static void main(String[] args) {
        SpringApplication.run(HlsApplication.class, args);
    }
}
```

## 11. Enforce module boundaries

This is one of the most important parts of the project.

Create `src/test/java/com/hls/teacher/ArchitectureTest.java`. Conceptually:

```java
class ArchitectureTest {

    @Test
    void applicationModulesShouldRespectBoundaries(ApplicationModules modules) {
        modules.verify();
    }
}
```

Spring Modulith provides application-module verification and module documentation facilities; the framework's current stable line is 2.1.1.

ArchUnit should additionally be used for rules that are specific to the constitution. For example:

```
attendance
    may depend on:
        identity
        teacher
        school
        audit

payroll
    may depend on:
        identity
        teacher
        school
        attendance
        substitution
        audit

schoolbilling
    may depend on:
        identity
        school
        teacher
        audit
```

But:

```
attendance
    MUST NOT depend on payroll

payroll
    MUST NOT access attendance.internal.repository

schoolbilling
    MUST NOT access teacher.internal.entity
```

These become build-breaking rules.

## 12. Database strategy

Use one PostgreSQL database, but logically separate tables by bounded context. For example:

```
identity_user
identity_role

school
school_contract
school_payment

teacher
teacher_assignment

attendance_record
attendance_correction

payroll_run
payroll_item
payslip
teacher_payout

training_calendar
training_session
training_participation

substitution
substitution_payout

expense
expense_payment

recruitment_drive
candidate
job_offer

marketing_activity

audit_event
```

Don't create one giant `teacher_master` table containing everything.

## 13. Financial tables need history

For example, don't do:

```
teacher.salary = 25000
```

and overwrite:

```
teacher.salary = 28000
```

Instead:

```
teacher_compensation
--------------------
id
teacher_id
effective_from
effective_to
monthly_salary
created_at
created_by
```

This means it is possible to answer: *What was this teacher's salary configuration on 31/07/2026?* That is essential for auditability.

## 14. Audit model

Create a central audit module.

```
audit_event
-----------

id
event_id
occurred_at
actor_user_id
actor_role
module
aggregate_type
aggregate_id
action
before_data
after_data
request_id
ip_address
reason
```

For example:

```json
{
  "aggregateType": "ATTENDANCE",
  "aggregateId": "ATT-123",
  "action": "CORRECTED",
  "before": {
    "status": "ABSENT"
  },
  "after": {
    "status": "PRESENT"
  },
  "reason": "Teacher submitted attendance correction"
}
```

The audit table should be append-only. Ideally:

```
Application DB user
        │
        ├── INSERT audit_event
        │
        ├── SELECT audit_event
        │
        └── NO UPDATE
        └── NO DELETE
```

Schema migration/admin privileges are separate.

## 15. Attendance model

Start with:

```
attendance_record

id
teacher_id
school_id
attendance_date
status
source
submitted_at
created_by
updated_at
version
```

Status:

```
PRESENT
ABSENT
HALF_DAY
LEAVE
HOLIDAY
TRAINING
SUBSTITUTION
```

Do not allow arbitrary strings. Use a domain enum.

## 16. Attendance correction

Never silently change `ABSENT → PRESENT`. Instead:

```
AttendanceCorrection

id
attendance_id
old_status
new_status
reason
requested_by
approved_by
requested_at
approved_at
```

Then:

```
attendance_record
       +
attendance_correction
       +
audit_event
```

gives a complete history.

## 17. Payroll architecture

Payroll should look like:

```
PayrollController
       │
       ▼
PayrollApplicationService
       │
       ├── Attendance API
       ├── Teacher API
       ├── School API
       ├── Substitution API
       │
       ▼
PayrollCalculationEngine
       │
       ▼
PayrollResult
       │
       ▼
PayrollRun
       │
       ▼
Payslip
```

The calculation engine should be as pure as possible:

```java
public PayrollResult calculate(PayrollInput input) {
    // deterministic calculation
}
```

That makes it extremely easy to test.

## 18. Payroll must not contain database queries

Avoid:

```java
calculateSalary(teacherId) {
    repository.findAttendance(...);
    repository.findContract(...);
    repository.findSalary(...);
    ...
}
```

Instead:

```java
PayrollInput input =
    payrollDataProvider.loadPayrollInput(
        teacherId,
        period
    );

PayrollResult result =
    calculator.calculate(input);
```

Then `PayrollCalculator` can have hundreds of test cases without PostgreSQL.

## 19. Virtual-thread payroll processing

The constitution specifically requires virtual-thread batch processing. Use JDK 25's virtual-thread executor:

```java
try (var executor =
         Executors.newVirtualThreadPerTaskExecutor()) {

    var futures = teachers.stream()
        .map(teacher ->
            executor.submit(
                () -> calculateTeacherPayroll(
                    payrollRunId,
                    teacher.id()
                )
            )
        )
        .toList();

    for (var future : futures) {
        future.get();
    }
}
```

This uses virtual threads rather than creating a fixed worker pool.

For a production implementation, improve this with:

```
PayrollRun
    RUNNING
       ↓
    COMPLETED
       or
    FAILED
       or
    PARTIAL_FAILURE
```

and individual:

```
PayrollItem
    PENDING
    CALCULATING
    CALCULATED
    FAILED
```

This makes retries and operational recovery possible.

## 20. Important payroll concurrency rule

Do not allow two payroll runs for the same `school + month` to calculate simultaneously unless explicitly intended.

Use a database uniqueness constraint:

```
unique (
    payroll_period,
    scope
)
```

or an explicit run lock. For example, `2026-08 + ALL_SCHOOLS` can have only one active calculation.

## 21. Payroll API

Example:

```
POST /api/v1/payroll/runs
```

Request:

```json
{
  "period": "2026-08",
  "scope": "ALL_SCHOOLS"
}
```

Response:

```json
{
  "runId": "PR-202608-001",
  "status": "STARTED"
}
```

Then:

```
GET /api/v1/payroll/runs/PR-202608-001
```

Response:

```json
{
  "runId": "PR-202608-001",
  "status": "COMPLETED",
  "teachers": 83,
  "calculated": 82,
  "failed": 1,
  "startedAt": "...",
  "completedAt": "..."
}
```

This is much safer than keeping the HTTP request open while calculating 100 teachers.

## 22. Security architecture

Use two layers.

**Layer 1 — Role**

```
DIRECTOR
MANAGER
TEACHER
ADMIN
```

**Layer 2 — scope**

```
DIRECTOR
    ALL

MANAGER
    assigned schools

TEACHER
    own teacher record

ADMIN
    master-data/reconciliation scope
```

Therefore, `@PreAuthorize(...)` alone is not sufficient. A manager might legitimately have `ROLE_MANAGER` but still not be allowed to access School #42 if School #42 is not assigned to them.

## 23. Attribute-based authorization

Create:

```
SchoolAccessPolicy
TeacherAccessPolicy
PayrollAccessPolicy
AttendanceAccessPolicy
```

Example:

```java
public boolean canAccessSchool(
        CurrentUser user,
        SchoolId schoolId) {

    if (user.isDirector()) {
        return true;
    }

    if (user.isManager()) {
        return assignmentRepository
            .existsByManagerAndSchool(
                user.userId(),
                schoolId
            );
    }

    return false;
}
```

Then test every boundary.

## 24. Security test matrix

Automated integration tests should cover a matrix such as:

| Action | Director | Manager A | Manager B | Teacher |
|---|---|---|---|---|
| View School A | ✓ | ✓ | ✗ | ✗ |
| View School B | ✓ | ✗ | ✓ | ✗ |
| Edit own attendance | ✓ | ✓ | ✓ | ✓ |
| Edit another teacher | ✓ | scoped | scoped | ✗ |
| Start payroll | ✓ | policy-dependent | policy-dependent | ✗ |
| Approve payroll | ✓ | configured scope | configured scope | ✗ |
| Maintain master data | ✓ | ✗/limited | ✗/limited | ✗ |

## 25. Authentication

The requirement is: short-lived JWT access token + rotating refresh token.

Recommendation:

```
Access token:
10–15 minutes

Refresh token:
7–30 days

Refresh:
rotate refresh token

Old refresh token:
invalidated
```

For teachers:

```
Mobile
   ↓
phone number
   ↓
OTP
   ↓
India SMS gateway
   ↓
identity verification
   ↓
JWT access + refresh token
```

Spring Security's current JWT resource-server support provides the token validation infrastructure; JWT issuance itself needs an authorization-server/authentication component.

The authentication implementation should be an explicit ADR rather than mixed into the identity domain.

## 26. Offline mobile architecture

This is important enough to design before implementing attendance.

React Native:

```
                Mobile App
                    │
        ┌───────────┴───────────┐
        │                       │
   Local SQLite            API Client
        │                       │
        │                  online?
        │                  /       \
        │                yes        no
        │                 │          │
        └────────────► Sync Queue ◄──┘
                          │
                          ▼
                     Spring API
```

Offline record:

```json
{
  "clientEventId": "device-123-000928",
  "type": "ATTENDANCE_CAPTURED",
  "occurredAt": "...",
  "payload": {}
}
```

The server must make `clientEventId` idempotent. If the mobile application sends the same event three times (`POST POST POST`), the server should create ONE business event, not three attendance records.

## 27. Offline sync conflict strategy

Don't use last-write-wins for financial data. Instead:

```
Attendance
   ↓
server version
   ↓
conflict detected
   ↓
manual correction workflow
```

For example:

```
LOCAL:
PRESENT

SERVER:
ABSENT

SYNC:
CONFLICT

→ create correction/review
```

Never silently overwrite payroll-affecting information.

## 28. School billing

Model contracts separately:

```
school
school_contract
school_contract_rate
teacher_assignment
school_payment
school_payment_allocation
```

Example:

```
School Contract
     │
     ├── monthly contract rate
     ├── effective dates
     ├── payment terms
     └── active/inactive
```

Then:

```
Teacher Assignment
     │
     ├── teacher
     ├── school
     ├── start date
     ├── end date
     └── billing terms
```

Receivable:

```
active assignments
       +
contract terms
       ↓
school receivable
```

not:

```
manual invoice amount
```

## 29. Payment reconciliation

Use separate entities:

```
school_payment
school_payment_allocation
```

This handles:

```
Invoice ₹100,000

Payment 1 = ₹40,000
Payment 2 = ₹35,000
Payment 3 = ₹25,000
```

or:

```
Payment ₹100,000
    ↓
₹60,000 Invoice A
₹40,000 Invoice B
```

This is much more flexible than a single `paid=true`.

## 30. Teacher payout

Likewise:

```
payroll_item
      │
      ▼
teacher_payout
      │
      ├── payment 1
      ├── payment 2
      └── payment 3
```

Supports:

```
salary = ₹28,000

advance = ₹10,000
final = ₹18,000
```

with complete auditability.

## 31. Margin calculation

Do not store `school.margin` as the canonical value. Calculate:

```
School Margin
=
School Receivable
-
Teacher Cost
-
Substitution Cost
-
Allocated Expenses
```

Then expose:

```
expected revenue
collected revenue
teacher payout
substitution payout
expenses
outstanding
margin
```

This makes the number explainable.

## 32. Training

Training deserves its own workflow.

```
Training Calendar
       │
       ▼
Training Session
       │
       ├── venue
       ├── trainer
       ├── date/time
       └── capacity
             │
             ▼
       Participation
             │
             ├── teacher
             ├── attendance
             └── completion
```

Teacher history:

```
Teacher
  │
  ├── onboarding
  ├── training
  ├── training attendance
  ├── school assignment
  └── substitution history
```

## 33. Substitution

Model:

```
substitution

id
school_id
regular_teacher_id
substitute_teacher_id
date
reason
status
approved_by
```

And:

```
substitution_payout

substitution_id
amount
status
paid_amount
```

This allows payroll reconciliation.

## 34. Recruitment

Workflow:

```
Campus Drive
     ↓
Candidate
     ↓
Interview
     ↓
Offer
     ↓
Accepted
     ↓
Teacher onboarding
```

Don't create a separate teacher manually after recruitment if the candidate becomes a teacher. Use:

```
Candidate
    ↓
Onboarding
    ↓
Teacher
```

with historical linkage.

## 35. Marketing

Keep V1 deliberately lightweight:

```
marketing_activity

id
organization_id
activity_date
prospect
contact
activity_type
status
next_follow_up
outcome
notes
```

Outcome:

```
CONTRACT_SIGNED
FOLLOW_UP_REQUIRED
DECLINED
NO_RESPONSE
```

Do not build a CRM in V1.

## 36. Reporting

Create a reporting module that consumes public module APIs/events.

Dashboards:

```
Director
Schools
Teachers
Attendance
Payroll
Receivables
Payments
Expenses
Margin
Training
Recruitment
Marketing

Manager
My Schools
My Teachers
Attendance
Substitutions
School Collections
Teacher Issues

Teacher
My Attendance
My Payslips
My Training
My Assignments
My Profile
```

## 37. Export

Create:

```
GET /api/v1/reports/schools/{id}/attendance/export
GET /api/v1/reports/payroll/{runId}/export
```

Support CSV and PDF. Don't make PDF generation part of the payroll calculation itself. Use:

```
Payroll
    ↓
canonical payroll data
    ↓
Reporting
    ↓
CSV/PDF
```

## 38. Observability from day one

Add:

```
Actuator
Micrometer
structured JSON logs
requestId
traceId
```

Every request logs:

```
requestId
userId
role
module
endpoint
duration
status
```

Payroll events:

```
payroll.run.started
payroll.teacher.calculated
payroll.teacher.failed
payroll.run.completed
```

Metrics:

```
hls_payroll_runs_total
hls_payroll_failures_total
hls_attendance_corrections_total
hls_school_payment_outstanding
hls_sync_conflicts_total
```

## 39. Required alerts

At minimum:

```
Application DOWN
Database unavailable
DB connection pool exhaustion
Disk > 80%
Disk > 90%
Payroll failure
Payroll partial failure
SMS gateway failures
Repeated authentication failures
Offline sync failures
```

## 40. Flyway migrations

Use `backend/src/main/resources/db/migration/`. Example:

```
V1__create_identity_tables.sql
V2__create_organization_tables.sql
V3__create_school_tables.sql
V4__create_teacher_tables.sql
V5__create_attendance_tables.sql
V6__create_training_tables.sql
V7__create_payroll_tables.sql
...
```

Don't allow Hibernate to create production schema. Use:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

Production: Flyway → schema, Hibernate → validation.

## 41. Testing pyramid

Four levels are needed.

**Level 1 — Domain unit tests**

Especially:

```
PayrollCalculatorTest
AttendanceRollupTest
ReceivableCalculatorTest
MarginCalculatorTest
SubstitutionPayoutTest
```

These should be fast and numerous.

**Level 2 — Module tests**

Spring Modulith module tests:

```
AttendanceModuleTest
PayrollModuleTest
SchoolBillingModuleTest
```

**Level 3 — Integration tests**

Use Testcontainers: Spring Boot + PostgreSQL container. Test transaction behavior, Flyway, security, authorization, repository behavior, audit.

**Level 4 — End-to-end**

Eventually: React → API → PostgreSQL. Test critical workflows:

```
Teacher onboarding
Attendance
Correction
Payroll
Approval
Payout
School payment
Reconciliation
```

## 42. CI pipeline

Every pull request:

```
checkout
   ↓
JDK 25
   ↓
mvn clean verify
   ↓
unit tests
   ↓
integration tests
   ↓
ArchUnit
   ↓
Spring Modulith verification
   ↓
security tests
   ↓
Docker build
```

No merge if: module boundary violation, or payroll tests fail, or authorization test fails.

## 43. Dockerfile

Use a multi-stage build:

```dockerfile
FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B clean package -DskipTests


FROM eclipse-temurin:25-jre

WORKDIR /app

COPY --from=build \
     /workspace/target/hls-teacher-management-*.jar \
     app.jar

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
```

For production, later pin the exact base image digest.

## 44. Docker Compose

Production architecture:

```yaml
services:

  app:
    image: ghcr.io/hls/hls-teacher-management:${VERSION}
    restart: unless-stopped

    environment:
      SPRING_PROFILES_ACTIVE: prod
      SPRING_DATASOURCE_URL: ${DATABASE_URL}
      SPRING_DATASOURCE_USERNAME: ${DATABASE_USERNAME}
      SPRING_DATASOURCE_PASSWORD: ${DATABASE_PASSWORD}

    depends_on:
      postgres:
        condition: service_healthy

    ports:
      - "8080:8080"

  postgres:
    image: postgres:18
    restart: unless-stopped

    environment:
      POSTGRES_DB: hls
      POSTGRES_USER: hls_app
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}

    volumes:
      - postgres_data:/var/lib/postgresql/data

    healthcheck:
      test:
        [
          "CMD-SHELL",
          "pg_isready -U hls_app -d hls"
        ]
      interval: 10s
      timeout: 5s
      retries: 5

  nginx:
    image: nginx:stable
    restart: unless-stopped

    ports:
      - "80:80"
      - "443:443"

    depends_on:
      - app

volumes:
  postgres_data:
```

For the final production file, pin exact image versions/digests and harden PostgreSQL credentials/networking.

## 45. AWS architecture

For V1:

```
                  Internet
                     │
                     ▼
                 Route 53
                     │
                     ▼
                  Nginx
                     │
             ┌───────┴───────┐
             │               │
             ▼               ▼
        React Web         Spring Boot
                             │
                             ▼
                         PostgreSQL
```

Single EC2:

```
EC2 ap-south-1
│
├── nginx
├── hls-app
└── postgres
```

However, one deployment decision worth reconsidering from the constitution:

**Recommended production variant**

```
EC2
 ├── nginx
 └── Spring Boot

RDS PostgreSQL
```

rather than:

```
EC2
 ├── nginx
 ├── Spring Boot
 └── PostgreSQL
```

The constitution says "relational database" but does not require PostgreSQL to physically run on the same VM. Keeping the app on one EC2 while using RDS gives much better database durability and backup/recovery. If strict "single VM" is mandatory, PostgreSQL can remain on the EC2.

## 46. AWS deployment phases

**Phase 1**

```
VPC
public subnet
private subnet
security group
EC2
```

**Phase 2**

```
Docker
Docker Compose
Git
AWS CLI
```

**Phase 3**

```
/opt/hls/
    compose.yaml
    .env
    nginx/
```

Never put secrets in Git.

## 47. Secret management

The constitution says: secrets never enter source control.

For AWS, use AWS Secrets Manager for:

```
DB password
JWT signing secrets
SMS gateway credentials
S3 credentials
```

with environment injection at deployment. At minimum, these must not be committed:

```
DATABASE_PASSWORD
JWT_PRIVATE_KEY
JWT_PUBLIC_KEY
SMS_API_KEY
S3 credentials
```

## 48. Production deployment

The deployment pipeline should be:

```
Developer
    ↓
Git push
    ↓
GitHub Actions
    ↓
mvn verify
    ↓
Architecture tests
    ↓
Security tests
    ↓
Docker build
    ↓
Container registry
    ↓
SSH/SSM deployment
    ↓
docker compose pull
    ↓
database migration
    ↓
application restart
    ↓
health check
    ↓
smoke test
```

## 49. Deployment script

Example:

```bash
#!/usr/bin/env bash

set -euo pipefail

VERSION="$1"

export VERSION

docker compose pull

docker compose run --rm app \
  java -jar /app/app.jar \
  --spring.main.web-application-type=none

docker compose up -d

curl --fail \
  http://localhost:8080/actuator/health

echo "Deployment successful: ${VERSION}"
```

The Flyway strategy should be refined so migrations execute exactly once as part of normal application startup or a dedicated migration job.

## 50. Zero-downtime is not required initially

The constitution explicitly says: single instance, no load balancer. Therefore don't over-engineer deployment. Use:

```
pull image
backup
migrate
restart
health check
```

For the initial <100-user system this is reasonable.

## 51. Backup strategy

If PostgreSQL is on EC2:

```
daily PostgreSQL backup
+
WAL/archive strategy if required
+
encrypted S3 storage
+
retention policy
```

Test restoration. A backup that has never been restored is not considered a verified backup. Run a restore test monthly.

## 52. Rollback strategy

```
v1.4
 ↓
v1.5
 ↓
problem
 ↓
rollback to v1.4
```

Database migrations must be designed carefully. Never depend on automatic destructive rollback for financial tables. Use additive migrations first:

```
ADD column
ADD table
ADD index
```

Remove old structures only after the application no longer needs them.

## 53. SDD implementation order

Implement in this exact sequence.

**Sprint 0 — Foundation**

```
constitution
ADR
repository
CI
Docker
Spring Boot
Spring Modulith
PostgreSQL
Flyway
security skeleton
observability
```

Deliverable: application starts, DB connects, Actuator works, architecture test passes, Docker works.

## 54. Sprint 1 — Identity + organization

Implement:

```
User
Role
Manager
School
ManagerSchoolAssignment
```

Deliverables: login, JWT, RBAC, manager scope, audit.

Tests: Director access, Manager isolation, Teacher isolation, Admin restrictions.

## 55. Sprint 2 — Teacher + school

Implement:

```
Teacher
TeacherProfile
School
SchoolContract
TeacherAssignment
```

Business workflow: create teacher → assign school → configure compensation → activate.

## 56. Sprint 3 — Attendance

Implement: attendance capture, attendance correction, attendance approval, attendance history, attendance export.

Then: offline mobile capture, sync, idempotency, conflict handling.

Do not start payroll until attendance is stable.

## 57. Sprint 4 — Training

Implement: training calendar, training session, teacher participation, training attendance, training history.

## 58. Sprint 5 — Substitution

Implement: substitution request, approval, assignment, attendance, payout, reconciliation.

## 59. Sprint 6 — Payroll

Only now implement:

```
PayrollInput
PayrollCalculator
PayrollRun
PayrollItem
Payslip
PayrollApproval
TeacherPayout
```

Order:

```
formula tests
     ↓
calculator
     ↓
database
     ↓
virtual-thread processing
     ↓
approval
     ↓
payout
```

This follows the constitutional requirement that calculation-heavy features receive unit tests before production integration.

## 60. Sprint 7 — School billing

Implement: receivable generation, school payment, partial payment, allocation, reconciliation, aging. Then margin.

## 61. Sprint 8 — Expense

Implement: expense, expense approval, partial reimbursement/payment, receipt, reconciliation, audit.

## 62. Sprint 9 — Reporting

Build: Director dashboard, Manager dashboard, Teacher dashboard, Payroll reports, School receivable reports, Margin report, Attendance reports.

## 63. Sprint 10 — Recruitment + Marketing

Recruitment: drive, candidate, interview, offer, acceptance, onboarding, teacher.

Marketing: prospect, activity, follow-up, outcome.

## 64. Frontend implementation

React web:

```
web/hls-web/src/

app/
auth/
components/
features/
    dashboard/
    schools/
    teachers/
    attendance/
    payroll/
    billing/
    expenses/
    training/
    substitution/
    recruitment/
    marketing/
reports/
api/
offline/
```

Use feature-oriented organization rather than a global `components/ services/ controllers/ models/` split for the entire application.

## 65. Mobile implementation

React Native:

```
mobile/hls-mobile/

src/
├── auth
├── attendance
├── expense
├── training
├── substitution
├── sync
├── offline
├── notifications
└── common
```

Android first:

```
Android production
      ↓
stabilize
      ↓
iOS
```

## 66. API versioning

Start with `/api/v1/`. Examples:

```
/api/v1/auth
/api/v1/teachers
/api/v1/schools
/api/v1/attendance
/api/v1/payroll
/api/v1/school-billing
/api/v1/training
/api/v1/substitutions
/api/v1/recruitment
/api/v1/marketing
/api/v1/reports
```

## 67. Don't expose entities directly

Bad:

```java
@GetMapping("/{id}")
TeacherEntity getTeacher(...)
```

Better:

```java
TeacherResponse getTeacher(...)
```

with:

```
Entity
 ↓
Application Service
 ↓
DTO
 ↓
REST
```

This prevents database/domain structures becoming the public API.

## 68. Event-driven module communication

Use domain/application events for things like:

```
TeacherAssignedToSchool
AttendanceCorrected
PayrollCalculated
PayrollApproved
SchoolPaymentReceived
TeacherOnboarded
TrainingCompleted
SubstitutionApproved
```

Example:

```
Teacher
   ↓
TeacherAssignedToSchool
   ↓
Attendance
   ↓
Payroll
```

Spring Modulith is specifically designed to support modular application structure and application events; its 2.1 release also includes improvements around event handling/observability.

## 69. Avoid event abuse

Not everything needs an event. Use direct public module APIs for queries required immediately. Use events for notification, audit side effects, report projections, workflow reactions.

For example, `Payroll → Attendance` might be a direct query/application API. But:

```
PayrollApproved
   ↓
Notification
   ↓
Audit
   ↓
Reporting
```

is a good event-driven flow.

## 70. ADR-0001

Create `docs/adr/ADR-0001-modular-monolith.md`:

```
Status: Accepted

Context:
HLS requires strong domain boundaries but currently has
fewer than 100 users.

Decision:
Use one Spring Boot deployment with Spring Modulith modules.

Consequences:
+ Simple deployment
+ Transactional consistency
+ Lower operational cost
+ Enforced module boundaries
- Requires disciplined architecture
- Scaling individual modules independently is not initially possible
```

## 71. ADR-0002

```
Status: Accepted

Database: PostgreSQL

Reasons:
- relational integrity
- transactions
- JSONB for audit snapshots
- mature indexing
- strong support with Spring Data
- suitable for financial workflows
```

## 72. ADR-0003 — Payroll

This is the most important ADR. It must contain:

```
salary formula
attendance rules
half-day rules
leave rules
holiday rules
training rules
substitution rules
rounding
effective dates
deductions
advances
arrears
corrections
approval
payout
```

Example:

```
Payroll =
BaseSalary
× AttendanceFactor
+ ApprovedAdditions
+ Arrears
- ApprovedDeductions
- Advances
```

Do not use that formula as the HLS formula unless it matches actual business rules. The constitution deliberately says the salary formula must reflect HLS's operating model. It should be derived from the actual spreadsheet/business rules rather than guessed.

## 73. Definition of Done

A feature is not complete merely because the API works. For every feature:

```
[ ] Requirement documented
[ ] Acceptance criteria documented
[ ] ADR updated if architecture changes
[ ] Database migration
[ ] Domain model
[ ] Application service
[ ] REST API
[ ] Authorization
[ ] Scope enforcement
[ ] Audit trail
[ ] Unit tests
[ ] Integration tests
[ ] Module boundary tests
[ ] API tests
[ ] Error handling
[ ] Observability
[ ] Documentation
[ ] CSV/PDF if applicable
[ ] Offline behavior if mobile applicable
[ ] Deployment verified
```

## 74. The most important quality gate

Before production, `mvn clean verify` must enforce:

```
✓ compilation
✓ unit tests
✓ integration tests
✓ PostgreSQL tests
✓ security tests
✓ manager-scope tests
✓ payroll calculation tests
✓ audit tests
✓ Modulith boundary tests
✓ ArchUnit tests
```

If an architectural boundary fails: `BUILD FAILED`. This directly implements Constitution Principle V.

## 75. Recommended project milestones

```
v0.1
Foundation
│
├── Boot
├── Java 25
├── PostgreSQL
├── Flyway
├── Modulith
├── Security
└── CI/CD

v0.2  Identity + Organization
v0.3  Schools + Teachers
v0.4  Attendance + Offline Sync
v0.5  Training + Substitution
v0.6  Payroll
v0.7  School Billing + Expenses
v0.8  Reporting + Exports
v0.9  Recruitment + Marketing
v1.0  Production Android + Web
```

## 76. Suggested first Git branches

```
main
develop

feature/foundation
feature/identity
feature/organization
feature/school
feature/teacher
feature/attendance
feature/training
feature/substitution
feature/payroll
feature/school-billing
feature/expense
feature/reporting
feature/recruitment
feature/marketing
feature/mobile-sync
```

For payroll and financial changes, require at least one additional review.

## 77. First implementation task list

Start the actual coding with exactly these tasks:

```
HLS-001  Create repository
HLS-002  Add constitution.md
HLS-003  Create ADR structure
HLS-004  Bootstrap Spring Boot 4.1.1
HLS-005  Configure Java 25
HLS-006  Add Spring Modulith 2.1.1
HLS-007  Add PostgreSQL
HLS-008  Add Flyway
HLS-009  Add Actuator
HLS-010  Add structured logging
HLS-011  Add correlation IDs
HLS-012  Add Docker
HLS-013  Add Docker Compose
HLS-014  Add GitHub Actions
HLS-015  Add Modulith architecture tests
HLS-016  Add ArchUnit rules
HLS-017  Add Testcontainers
HLS-018  Create identity module
HLS-019  Create organization module
HLS-020  Create audit module
```

Then:

```
HLS-021 → School
HLS-022 → Teacher
HLS-023 → Teacher Assignment
HLS-024 → Attendance
...
```

## 78. What NOT to do

Avoid these from day one:

```
❌ Microservices
❌ Kafka
❌ Kubernetes
❌ Service mesh
❌ Redis unless an actual use case appears
❌ Fixed thread pools for payroll
❌ Cron-based payroll
❌ Spreadsheet-driven payroll
❌ Database access across modules
❌ Shared "common" domain entities
❌ Generic CRUD controllers
❌ Soft-delete everything without history
❌ Mutable audit records
❌ Manager authorization based only on ROLE_MANAGER
❌ Manual salary entry
❌ `paid=true`
❌ Last-write-wins financial synchronization
```

The architecture should stay deliberately boring until actual scale demands otherwise.

## 79. Final target

The end state should look like:

```
                 HLS TEACHER MANAGEMENT
                         │
       ┌─────────────────┼──────────────────┐
       │                 │                  │
     WEB              MOBILE              API
       │                 │                  │
       └─────────────────┼──────────────────┘
                         │
                  SPRING BOOT 4.1.1
                       JAVA 25
                         │
                  SPRING MODULITH
                         │
 ┌─────────┬────────┬───┴────┬────────┬─────────┐
 │         │        │        │        │         │
Identity School Teacher Attendance Payroll Billing
 │         │        │        │        │         │
 └─────────┴────────┴────────┴────────┴─────────┘
                         │
              ┌──────────┴──────────┐
              │                     │
          PostgreSQL             Audit
              │
          Flyway schema
                         │
                    Docker Compose
                         │
                       EC2
                         │
                     ap-south-1
```

The core architectural invariant is:

> Every business module owns its behavior and data access, exposes only a small public API, and communicates with other modules through public contracts/events.

That is the piece that will keep this from becoming a monolith that is merely a large pile of packages.

## Recommended next implementation step

Rather than trying to implement all 14 modules at once, start by generating **Phase 1** — the actual runnable repository with:

```
Java 25
Spring Boot 4.1.1
Spring Modulith 2.1.1
Maven
PostgreSQL
Flyway
Spring Security
JWT skeleton
Actuator
Testcontainers
ArchUnit
Modulith architecture tests
Dockerfile
docker-compose.yml
GitHub Actions
application-local.yml
application-test.yml
application-prod.yml
ADR-0001 through ADR-0007
```

Then implement Identity → Organization → School → Teacher → Attendance → Payroll in that order.

The one thing needed before implementing the payroll module is the actual HLS salary/attendance spreadsheet rules — especially how present, absent, half-day, leave, holidays, training, joining/leaving mid-month, school contract rates, deductions, advances, rounding, and substitutions affect salary. Those rules should become executable specifications and tests rather than being guessed from the constitution.
