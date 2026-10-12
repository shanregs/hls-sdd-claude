# Implementation Plan: Manager Tasks

**Branch**: `025-manager-tasks` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

## Summary

A new `tasks` module (package `com.hls.tasks`, api / internal / web like the other modules) owning the task lifecycle,
history, evidence links, a daily deadline job, and two public interfaces: `TaskCreation` (C2) and `TaskActivities`
(C1). The `notification` module gains three types and a listener for the events the task module publishes. A listener
in `task` turns the 023 `FollowUpScheduled` event into a Follow-up task. A new `TASKS` permission module is seeded for
Admin, Director and Manager, with two navigation items. A React feature (`features/tasks`) provides My Tasks and
Manager Tasks.

## Technical Context

- Java 25, Spring Boot, Spring Modulith, JPA, Flyway, PostgreSQL (Testcontainers in tests), React + Vite + Vitest.
- Migration: `V45__create_task_tables.sql` (current max on main is V26; V45 chosen as a gap to avoid collisions with
  the parallel 011 and 017a branches).
- Constraints: append-only history (trigger refuses UPDATE and DELETE), no cross-module foreign keys, audit via
  `ChangeRecorder`, business date via `BusinessDate`.

## Constitution Check

| Principle | How it is met |
| --- | --- |
| I Auditable | `task_history` is append-only (DB trigger) and every field change also goes to Change History through `ChangeRecorder`. |
| II Five roles, RBAC | New module `TASKS` with View, Create, Edit, Approve; seeded grants; editable in the matrix; Teacher and System ineligible; Manager cannot hold Approve. |
| III Data scope | Manager sees only tasks assigned to them (server-filtered, others 404); Admin and Director org-wide. |
| IV Role-based UI | Menu from the navigation catalog: "My Tasks" (Manager) and "Manager Tasks" (Admin, Director) under OPERATIONS. |
| V/VII Modular monolith | `tasks` reads `identity`, `organization.api`, `files.api`, `school.api`, `designation.api` (BusinessDate), `recruitment.api` (event). Only `notification` reads `tasks.api`. ArchUnit test enforces it. |
| VIII Concurrency | Optimistic version on the task; second writer gets 409. |
| IX Testability | Unit, API, per-role authorization, ArchUnit and frontend tests. |
| X Security | Every endpoint re-checks the grant server side; file downloads gated by task visibility; attachments sent as downloads. |

Constitution change: PATCH 2.5.3, a Manager Tasks row in the default access matrix. No principle changes.

## Project Structure

```text
specs/025-manager-tasks/ spec.md plan.md research.md data-model.md quickstart.md tasks.md contracts/
backend/src/main/java/com/hls/tasks/
  api/       TaskCreation, TaskActivities, TaskAssigned, TaskOverdue, TaskEscalated, TaskType, package-info
  internal/  Task, TaskStatus, TaskHistory, TaskEvidence, repositories, TaskService, TaskQueryService,
             TaskCreationImpl, TaskActivitiesImpl, DeadlineJob, FollowUpListener, TaskDevSeeder
  web/       TaskController
backend/src/main/java/com/hls/notification/internal/TaskEventListener.java (+ MessageFactory texts)
backend/src/main/resources/db/migration/V45__create_task_tables.sql
frontend/src/features/tasks/ (TasksPage, TaskDetail, taskApi, __tests__)
```

## Design Decisions

See [research.md](research.md). Highlights: overdue is derived; the deadline job keeps per-task markers
(`overdue_notified_for` due date, `escalated_at`) so repeated runs are idempotent; the notification type check is
replaced by a pattern check so parallel specs adding types do not clobber each other; C2 idempotency is a partial
unique index on (`source`, `source_key`) for open tasks.

## Phases

1. Migration, entities, permission module, navigation, access-model scope.
2. Lifecycle service and controller (US1, US2).
3. Overdue reason, follow-up, deadline job, notifications (US3).
4. C2 interface, 023 listener, C1 interface (US4, US5).
5. Frontend, Postman, demo data, roadmap, constitution row.
