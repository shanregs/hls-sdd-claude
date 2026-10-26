import {
  getJson,
  queryString,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Manager Activity Calendar (spec 035, contracts/calendar-api.md). Read-only: GET calls only. */
export const ACTIVITY_CALENDAR_ROUTE = "/operations/activity-calendar";

export type CalendarKind =
  | "CAMPUS_DRIVE"
  | "SCHOOL_VISIT"
  | "CALL"
  | "MEETING"
  | "TASK"
  | "TRAINING";

export type CalendarStatus = "PLANNED" | "DONE" | "MISSED" | "CANCELLED";

export const KIND_LABELS: Record<CalendarKind, string> = {
  CAMPUS_DRIVE: "Campus drive",
  SCHOOL_VISIT: "School visit",
  CALL: "Call",
  MEETING: "Meeting",
  TASK: "Task",
  TRAINING: "Training",
};

export const STATUS_LABELS: Record<CalendarStatus, string> = {
  PLANNED: "Planned",
  DONE: "Done",
  MISSED: "Missed",
  CANCELLED: "Cancelled",
};

export interface CalendarPerson {
  userId: string;
  name: string;
  zoneName: string | null;
  manager: boolean;
}

export interface CalendarLink {
  screen: "MARKETING" | "RECRUITMENT" | "TASKS" | "TRAINING";
  path: string;
}

export interface CalendarItem {
  key: string;
  activityId: string;
  kind: CalendarKind;
  subKind: string | null;
  date: string;
  status: CalendarStatus;
  doneLate: boolean;
  completedOn: string | null;
  rescheduled: boolean;
  originalDate: string | null;
  daysMoved: number | null;
  daysLate: number | null;
  people: CalendarPerson[];
  zoneId: string | null;
  zoneName: string | null;
  place: string | null;
  link?: CalendarLink | null;
}

export interface Counts {
  planned: number;
  done: number;
  doneLate: number;
  open: number;
  missed: number;
  cancelled: number;
  rescheduled: number;
}

export interface ManagerCounts extends Counts {
  userId: string;
  name: string;
}

export interface CalendarSummary extends Counts {
  byKind: Partial<Record<CalendarKind, Counts>>;
  byManager?: ManagerCounts[];
}

export interface SourceReport {
  group: "DRIVES" | "VISITS" | "TASKS" | "TRAINING";
  available: boolean;
  read: boolean;
  note?: string | null;
}

export interface CalendarResponse {
  month: string;
  today: string;
  partial: boolean;
  sources: SourceReport[];
  summary: CalendarSummary;
  items: CalendarItem[];
}

export interface NeedsAttentionEntry {
  item: CalendarItem;
  reasons: ("MISSED" | "RESCHEDULED")[];
  daysLate: number | null;
  daysMoved: number | null;
}

export interface NeedsAttentionGroup {
  userId: string;
  name: string;
  missed: number;
  rescheduled: number;
  entries: NeedsAttentionEntry[];
}

export interface NeedsAttentionResponse {
  from: string;
  to: string;
  partial: boolean;
  groups: NeedsAttentionGroup[];
}

export interface FiltersResponse {
  managers: { id: string; name: string }[];
  zones: { id: string; name: string }[];
  kinds: { kind: CalendarKind; available: boolean }[];
  statuses: CalendarStatus[];
}

export interface CalendarQuery {
  month?: string;
  managerId?: string;
  zoneId?: string;
  kind?: string;
  status?: string;
}

const BASE = "/api/v1/activity-calendar";

export function getCalendar(
  authFetch: AuthFetch,
  query: CalendarQuery,
): Promise<ApiResult<CalendarResponse>> {
  const qs = queryString({ ...query });
  return getJson(
    authFetch,
    `${BASE}${qs ? `?${qs}` : ""}`,
    "Could not load the calendar.",
  );
}

export function getNeedsAttention(
  authFetch: AuthFetch,
  query: {
    from?: string;
    to?: string;
    managerId?: string;
    zoneId?: string;
    kind?: string;
  },
): Promise<ApiResult<NeedsAttentionResponse>> {
  const qs = queryString({ ...query });
  return getJson(
    authFetch,
    `${BASE}/needs-attention${qs ? `?${qs}` : ""}`,
    "Could not load the items that need attention.",
  );
}

export function getFilters(
  authFetch: AuthFetch,
): Promise<ApiResult<FiltersResponse>> {
  return getJson(
    authFetch,
    `${BASE}/filters`,
    "Could not load the calendar filters.",
  );
}
