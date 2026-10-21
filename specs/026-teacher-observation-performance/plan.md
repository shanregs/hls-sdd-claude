# Implementation Plan: Teacher Observation and Performance

**Branch**: `026-teacher-observation-performance` | **Date**: 2026-10-21 | **Spec**: [spec.md](spec.md)

## Summary

A new `performance` module (package `com.hls.performance`, api / internal / web like the other modules) owning classroom
observations, Principal feedback, performance issues with corrective actions, referrals and observation plans. It plans an
observation by creating an Observation task for the Teacher's Manager through the `tasks` module, completes that task when
the observation is submitted, and publishes two events that `notification` turns into in-app notifications. A new
`TEACHER_PERFORMANCE` permission module is seeded for Admin, Director and Manager with one navigation item under OPERATIONS.
A React feature (`features/performance`) provides the lists, the observation form, the feedback form, the issue page and the
Teacher page. The `tasks` module's creation interface (C2) gains four small operations (change the date, complete, cancel,
reassign, all by source key) because C2 today can only create. See [research.md](research.md) for the decisions.

Read from the code, not the planning documents (research.md section 1): the task module's creation interface cannot yet update
or complete a task; there is no event when a School's Manager or a Teacher's School changes, so a reassignment is a
reconciliation rather than a listener; Teacher exit is not published, so closing issues for an exited Teacher is a daily check.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers in tests), React + Vite + Vitest.
- Migration: `V60__create_performance.sql` (current max on main is V53; a gap is left for the parallel 015, 027, 028 and 035
  branches, as spec 025 did with V45).
- Constraints: append-only history (trigger refuses UPDATE and DELETE on `performance_history` and on superseded versions),
  no cross-module foreign keys, audit through `ChangeRecorder`, business date through `BusinessDate`, optimistic version on
  every editable row, 404 for anything out of scope.
- Files: observation attachments go through `files.api.FileStore` (up to 5 per observation).
- Scale: under 100 users; 2,000 observations and 500 issues must list in under 2 seconds (SC-008), met with indexes on
  teacher, school, manager, status and date.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | `performance_history` is append-only (DB trigger) and every create, submit, correction, withdrawal, corrective action, referral, status change and closing also goes to Change History through `ChangeRecorder` with before and after values. A corrected observation or feedback is a new version row; the earlier row stays readable to Admin and Director. |
| II Five roles, RBAC | New module `TEACHER_PERFORMANCE` with View, Create, Edit (no Approve, no Delete); seeded for Admin, Director and Manager; editable in the matrix; Teacher and System are ineligible in `PermissionEligibility`. |
| III Data scope | Manager reaches only Teachers placed today at their Schools, through `TeacherScopeQueries`, plus records they authored; Admin and Director org-wide; everything else answers 404. Lists, the Teacher page, the dashboard summary and the 027 interface all filter on the same scope. |
| IV Role-based UI | One OPERATIONS item "Teacher Performance" from the navigation catalog for Admin, Director and Manager; buttons follow the grants and the server-sent `actions` on each record. No Teacher screen. |
| V Formula-driven | Not touched: no pay, receivable or margin effect (FR-033). |
| VI Training and substitution | Not touched; transfer and replacement stay in 027. |
| VII Modular monolith | `performance` reads `teacher.api` (scope, directory, placement), `organization.api` (Manager of a School), `school.api` (Principal contact), `tasks.api`, `files.api`, `identity` (callers, directors) and `designation.api` (BusinessDate). Only `notification` and (later) 027 and 028 read `performance.api`. ArchUnit and Modulith verification enforce it. |
| VIII Concurrency | Request-path only; optimistic version, second writer gets 409. The daily reconcile job is sequential and small. |
| IX Testability | Unit tests for the average and suggestion and the status machine; API tests per endpoint and per role and scope boundary; ArchUnit; frontend tests for the grants. |
| X Security | Every endpoint re-checks the grant and scope on the server; attachments are sent as downloads gated by observation visibility; free text is escaped on output and capped at 2000 characters. |

Constitution change: PATCH 2.5.7, a Teacher Performance row in the default access matrix and the OPERATIONS navigation item.
No principle changes. Gate result: pass before research and after design, with no violations.

## Project Structure

```text
specs/026-teacher-observation-performance/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/performance/
  api/       PerformanceReferrals (for 027), PerformanceSummary (for 028), ObservationPlanned, ReferralRecorded, package-info
  internal/  Observation, ObservationRating, ObservationAttachment, PrincipalFeedback, PerformanceIssue, CorrectiveAction,
             Referral, ObservationPlan, PerformanceHistory, repositories, ObservationService, FeedbackService, PlanService,
             IssueService, ReferralService, PerformanceQueries, ScopeGuard, TaskBridge, ManagerReconcileJob (daily:
             reassign tasks, close issues of exited Teachers), PerformanceDevSeeder
  web/       ObservationController, FeedbackController, IssueController, PlanController, TeacherPerformanceController,
             PerformanceExceptionAdvice
backend/src/main/java/com/hls/tasks/api/TaskCreation.java (+ four operations), internal/TaskService.java (+ implementation)
backend/src/main/java/com/hls/notification/internal/PerformanceEventListener.java (+ MessageFactory texts, two types)
backend/src/main/java/com/hls/identity/ (PermissionModule, PermissionEligibility, NavigationCatalog, AccessModelService)
backend/src/main/resources/db/migration/V60__create_performance.sql
frontend/src/features/performance/ (ObservationsPage, ObservationForm, FeedbackPage, IssuePage, TeacherPerformancePage,
  PlannedPage, performanceApi, __tests__)
postman/ (collection additions)
```

**Structure Decision**: one new backend module and one frontend feature; mobile is out of scope (spec). The task-module
amendment is the only change to an existing module's API.

## Design Decisions

See [research.md](research.md). Highlights: one `performance` module rather than "observation" plus "performance" (they share
the Teacher page, the issue loop and the scope); observation versions are rows linked by a `root_id`, current version flagged;
the open plan is idempotent through the task's source key `OBSERVATION_PLAN:{teacherId}`; follow-up tasks use
`PERFORMANCE_FOLLOW_UP:{issueId}:{round}`; the Manager of a Teacher is always read live, never stored, except on the
observation, which keeps the observer and School at the time of the visit; a daily reconcile job stands in for the missing
Manager-change and Teacher-exit events.

## Phases

1. Migration, entities, permission module, navigation, scope guard, task-module amendment (C2 operations).
2. Observation form: draft, submit, not held, correction, attachments, average and suggestion (US1).
3. Plans and task bridge: plan, change, complete on submit, reassign, notification (US2).
4. Principal feedback and withdrawal (US3).
5. Performance issues, corrective action, follow-up, escalation, cancel (US4).
6. Referrals, the 027 interface, Director notification and closing (US5).
7. Lists, Teacher page, dashboard summary (US6); then frontend, Postman, demo data, roadmap, constitution row.

## As built (2026-10-21)

Delivered as planned, with the changes recorded in [research.md](research.md) section 14: plan-id task keys, the fifth C2
operation `completeLinked`, `ReferralInfo`, summary keyed by Manager user id, and the `{reason, form}` correction body.
