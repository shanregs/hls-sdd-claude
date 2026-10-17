import type { AccessModel } from "../../src/api/accessModelApi";
import type { Person, Prospect, Visit } from "../../src/api/marketingApi";
import type {
  TaskActions,
  TaskDetail,
  TaskRow,
  TaskSummary,
  TaskTypeOption,
} from "../../src/api/tasksApi";

const item = (label: string, route: string, actions = ["VIEW"]) => ({ label, route, actions });

export const VISITS_ROUTE = "/marketing/calendar";
export const TASKS_ROUTE = "/operations/tasks";

/** The signed-in user of the app tests (see `authResult` in fakeServer.ts). */
export const ME = { userId: "u-1", name: "Tara" };

interface Grants {
  create?: boolean;
  edit?: boolean;
}

const grants = (g: Grants = {}) => ["VIEW", ...(g.create === false ? [] : ["CREATE"]), ...(g.edit === false ? [] : ["EDIT"])];

/** A Manager's menu as the server's catalog gives it: OPERATIONS → My Tasks and MARKETING → Marketing Calendar. */
export function visitTaskModel(options: { tasks?: Grants; visits?: Grants; taskLabel?: string } = {}): AccessModel {
  return {
    roles: ["MANAGER"],
    navigation: [
      { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
      { section: "OPERATIONS", items: [item(options.taskLabel ?? "My Tasks", TASKS_ROUTE, grants(options.tasks))] },
      { section: "MARKETING", items: [item("Marketing Calendar", VISITS_ROUTE, grants(options.visits))] },
      {
        section: "ACCOUNT",
        items: [
          item("Profile", "/account/profile", ["VIEW", "EDIT"]),
          item("Notifications", "/account/notifications", ["VIEW", "DELETE"]),
          item("Settings", "/account/settings"),
        ],
      },
    ],
    dataScope: { TASKS: "OWN", MARKETING: "ASSIGNED" },
  };
}

/** A Director sees the same two routes under the web's names. */
export const directorVisitTaskModel = (): AccessModel => ({ ...visitTaskModel({ taskLabel: "Manager Tasks" }), roles: ["DIRECTOR"] });

export function visit(overrides: Partial<Visit> = {}): Visit {
  return {
    id: "v-1",
    type: "VISIT",
    status: "PLANNED",
    effectiveStatus: "PLANNED",
    rescheduled: false,
    date: "2026-10-06",
    notes: null,
    outcome: null,
    followUpOn: null,
    followUpOverdue: false,
    cancelReason: null,
    prospect: { id: "p-1", name: "Green Valley Public School" },
    school: null,
    attendees: [{ userId: ME.userId, name: "Manoj Manager" }],
    attachments: [],
    dateHistory: [],
    version: 0,
    ...overrides,
  };
}

export function prospect(overrides: Partial<Prospect> = {}): Prospect {
  return {
    id: "p-1",
    name: "Green Valley Public School",
    board: "CBSE",
    zoneName: "Demo Zone",
    stage: "CONTACTED",
    won: false,
    schoolId: null,
    contactPerson: "Mrs Rao",
    ...overrides,
  };
}

export const colleague = (overrides: Partial<Person> = {}): Person => ({ userId: "u-2", name: "Divya Director", ...overrides });

export function taskRow(overrides: Partial<TaskRow> = {}): TaskRow {
  return {
    id: "t-1",
    type: "SCHOOL_VISIT",
    typeLabel: "School visit",
    title: "Visit the school and meet the Principal",
    status: "ASSIGNED",
    dueOn: "2026-10-12",
    assigneeUserId: ME.userId,
    assigneeName: "Manoj Manager",
    assigneeActive: true,
    linkedType: null,
    linkedId: null,
    linkedLabel: null,
    place: null,
    overdue: false,
    daysOverdue: 0,
    escalated: false,
    ...overrides,
  };
}

export const NO_ACTIONS: TaskActions = {
  acknowledge: false,
  start: false,
  complete: false,
  overdueReason: false,
  addEvidence: false,
  verify: false,
  sendBack: false,
  edit: false,
  cancel: false,
};

/** A task's detail; `actions` is whatever the test sets (the fake server recomputes it after each step). */
export function taskDetail(overrides: Partial<Omit<TaskDetail, "task">> & { task?: Partial<TaskRow> } = {}): TaskDetail {
  const { task, ...rest } = overrides;
  return {
    task: taskRow(task),
    description: null,
    createdBy: null,
    createdByName: "Divya Director",
    escalateAfterDays: 3,
    source: "DEMO",
    overdueReason: null,
    completionNote: null,
    completedAt: null,
    verifiedAt: null,
    verifiedByName: null,
    escalatedAt: null,
    cancelReason: null,
    parentTaskId: null,
    followUpTaskIds: [],
    history: [
      {
        event: "CREATED",
        actorUserId: null,
        actorName: "Divya Director",
        at: "2026-10-01T05:30:00Z",
        before: null,
        after: "ASSIGNED",
        note: null,
      },
    ],
    evidence: [],
    actions: { ...NO_ACTIONS, acknowledge: true },
    version: 0,
    ...rest,
  };
}

export const taskSummary = (overrides: Partial<TaskSummary> = {}): TaskSummary => ({
  today: 1,
  upcoming: 0,
  overdue: 2,
  awaiting: 0,
  escalated: 0,
  done: 0,
  ...overrides,
});

export const taskTypes = (): TaskTypeOption[] => [
  { value: "SCHOOL_VISIT", label: "School visit" },
  { value: "FOLLOW_UP", label: "Follow-up" },
  { value: "CONTENT_SUBMISSION", label: "Content preparation and submission" },
  { value: "RECRUITMENT", label: "Recruitment" },
  { value: "TRAINING", label: "Training" },
  { value: "TEACHER_ASSIGNMENT", label: "Teacher assignment" },
  { value: "OBSERVATION", label: "Observation" },
  { value: "PRINCIPAL_FEEDBACK", label: "Principal feedback" },
  { value: "SUBSTITUTE_ARRANGEMENT", label: "Substitute arrangement" },
];
