import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Spec 023 contracts/marketing-api.md. Amounts are strings with two decimals; dates are ISO. */

export type StoredStage =
  | "PROSPECT"
  | "CONTACTED"
  | "VISIT"
  | "FOLLOW_UP"
  | "INTERESTED"
  | "NEGOTIATION"
  | "FINAL_STAGE"
  | "ON_HOLD"
  | "LOST";

/** What the board shows: the stored stage, or WON, MOU or ACTIVE once the prospect is won (derived from spec 012). */
export type ShownStage = StoredStage | "WON" | "MOU" | "ACTIVE";

export const ACTIVE_STAGES: StoredStage[] = [
  "PROSPECT",
  "CONTACTED",
  "VISIT",
  "FOLLOW_UP",
  "INTERESTED",
  "NEGOTIATION",
  "FINAL_STAGE",
];

export const STAGE_LABEL: Record<ShownStage, string> = {
  PROSPECT: "Prospect",
  CONTACTED: "Contacted",
  VISIT: "Visit",
  FOLLOW_UP: "Follow-up",
  INTERESTED: "Interested",
  NEGOTIATION: "Negotiation",
  FINAL_STAGE: "Final Stage",
  ON_HOLD: "On Hold",
  LOST: "Lost",
  WON: "Won",
  MOU: "MoU",
  ACTIVE: "Active",
};

export interface PersonRef {
  userId: string;
  name: string;
}

export interface ProspectRow {
  id: string;
  name: string;
  board: string | null;
  zoneId: string;
  zoneName: string;
  owner: PersonRef;
  stage: StoredStage;
  effectiveStage: ShownStage;
  expectedTeachers: number | null;
  won: boolean;
  followUpOverdue: boolean;
  mouOverdue: boolean;
  schoolId: string | null;
  contactPerson: string | null;
  version: number;
}

export interface HistoryRow {
  kind: "STAGE" | "REVIEW_APPROVED" | "REVIEW_REJECTED";
  from: string | null;
  to: string;
  reason: string | null;
  by: PersonRef;
  at: string;
}

export interface OwnerChange {
  from: PersonRef | null;
  to: PersonRef;
  by: PersonRef;
  at: string;
}

export interface ProposalPosition {
  number: number;
  title: string | null;
  salary: string;
}

export interface Proposal {
  id: string;
  revision: number;
  label: string;
  teacherCount: number;
  startMonth: string;
  salaryMode: "SAME_FOR_ALL" | "PER_TEACHER";
  rate: string | null;
  positions: ProposalPosition[];
  monthlyTotal: string;
  notes: string | null;
  createdBy: PersonRef;
  createdAt: string;
}

export interface ContractStatus {
  available: boolean;
  status: "NOT_AVAILABLE" | "NOT_WON" | "NOT_RECORDED" | "MOU" | "ACTIVE";
  contract: {
    startsOn: string;
    endsOn: string | null;
    teacherCount: number | null;
    salaryMode: string | null;
    rate: string | null;
    positions: ProposalPosition[];
    filled: number;
    vacant: number;
  } | null;
  proposal: Proposal | null;
  differences: string[];
}

export interface ProspectDetail {
  row: ProspectRow;
  address: string | null;
  designation: string | null;
  phone: string | null;
  email: string | null;
  lostReason: string | null;
  wonAt: string | null;
  placeId: string | null;
  stageHistory: HistoryRow[];
  ownerHistory: OwnerChange[];
  proposal: Proposal | null;
  contractStatus: ContractStatus;
}

export interface ProspectInput {
  name: string;
  board?: string;
  address?: string;
  zoneId: string;
  contactPerson?: string;
  designation?: string;
  phone?: string;
  email?: string;
  expectedTeachers?: number;
  version?: number;
}

export type ActivityType = "VISIT" | "CALL" | "PROPOSAL_MEETING" | "FOLLOW_UP";
export const ACTIVITY_LABEL: Record<ActivityType, string> = {
  VISIT: "Visit",
  CALL: "Call",
  PROPOSAL_MEETING: "Proposal meeting",
  FOLLOW_UP: "Follow-up",
};

export interface AttachmentRef {
  fileId: string;
  name: string;
  sizeBytes: number;
  contentType: string;
}

export interface Activity {
  id: string;
  type: ActivityType;
  status: "PLANNED" | "COMPLETED" | "CANCELLED";
  effectiveStatus: "PLANNED" | "COMPLETED" | "CANCELLED" | "MISSED";
  rescheduled: boolean;
  date: string;
  notes: string | null;
  outcome: string | null;
  followUpOn: string | null;
  followUpOverdue: boolean;
  cancelReason: string | null;
  prospect: { id: string; name: string } | null;
  school: { id: string; name: string } | null;
  attendees: PersonRef[];
  attachments: AttachmentRef[];
  dateHistory: { from: string; to: string }[];
  version: number;
}

export interface PlannedDrive {
  kind: string;
  id: string;
  ownerUserId: string;
  date: string;
  place: string | null;
  status: string;
}

export interface ActivityList {
  content: Activity[];
  drives: PlannedDrive[];
}

export interface PlanInput {
  prospectId?: string;
  schoolId?: string;
  type: ActivityType;
  date: string;
  attendeeUserIds?: string[];
  notes?: string;
}

export interface ProposalInput {
  teacherCount: number;
  startMonth: string;
  salaryMode: "SAME_FOR_ALL" | "PER_TEACHER";
  rate?: string;
  positions?: { title?: string; salary: string }[];
  notes?: string;
}

export interface Board {
  columns: Record<string, ProspectRow[]>;
  counts: Record<string, number>;
}

export interface WinInput {
  placeId?: string;
  billingContact?: string;
  linkSchoolId?: string;
  principal?: {
    name: string;
    phone?: string | null;
    email?: string | null;
  } | null;
  accountant?: {
    name: string;
    phone?: string | null;
    email?: string | null;
  } | null;
}

export interface WinResult {
  prospect: unknown;
  handoff: {
    schoolId: string;
    path: string | null;
    proposal: Proposal | null;
    available: boolean;
  };
}

export interface MarketingDashboard {
  period: string;
  visits: {
    planned: number;
    completed: number;
    missed: number;
    cancelled: number;
  };
  prospectsByStage: Record<string, number>;
  won: number;
  lost: number;
  winRate: string | null;
  wonPerZone: { name: string; won: number }[];
  wonPerOwner: { name: string; won: number }[];
  demand: number;
  supply: number | null;
  shortfall: number | null;
}

export interface Settings {
  mouOverdueDays: number;
  version: number;
}

const M = "/api/v1/marketing";

export const listProspects = (
  f: AuthFetch,
  params: {
    zone?: string;
    owner?: string;
    stage?: string;
    query?: string;
    page?: number;
    size?: number;
  },
): Promise<ApiResult<PageOf<ProspectRow>>> =>
  getJson(
    f,
    `${M}/prospects?${queryString(params)}`,
    "Could not load prospects.",
  );
export const getProspect = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<ProspectDetail>> =>
  getJson(f, `${M}/prospects/${id}`, "Could not load this prospect.");
export const createProspect = (
  f: AuthFetch,
  input: ProspectInput,
): Promise<ApiResult<{ row: ProspectRow }>> =>
  sendJson(f, "POST", `${M}/prospects`, input);
export const updateProspect = (
  f: AuthFetch,
  id: string,
  input: ProspectInput,
): Promise<ApiResult<{ row: ProspectRow }>> =>
  sendJson(f, "PUT", `${M}/prospects/${id}`, input);
export const changeOwner = (
  f: AuthFetch,
  id: string,
  ownerUserId: string,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${M}/prospects/${id}/owner`, { ownerUserId });
export const moveStage = (
  f: AuthFetch,
  id: string,
  stage: StoredStage | "RESUME" | "REOPEN",
  reason?: string,
  version?: number,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${M}/prospects/${id}/stage`, { stage, reason, version });
export const reviewProspect = (
  f: AuthFetch,
  id: string,
  decision: "APPROVE" | "REJECT",
  reason?: string,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${M}/prospects/${id}/review`, { decision, reason });
export const winProspect = (
  f: AuthFetch,
  id: string,
  input: WinInput,
): Promise<ApiResult<WinResult>> =>
  sendJson(f, "POST", `${M}/prospects/${id}/win`, input);
export const getPipeline = (
  f: AuthFetch,
  params: { zone?: string; owner?: string },
): Promise<ApiResult<Board>> =>
  getJson(
    f,
    `${M}/pipeline?${queryString(params)}`,
    "Could not load the pipeline.",
  );

export const listActivities = (
  f: AuthFetch,
  params: { from?: string; to?: string; mine?: boolean; prospect?: string },
): Promise<ApiResult<ActivityList>> =>
  getJson(
    f,
    `${M}/activities?${queryString(params)}`,
    "Could not load activities.",
  );
export const planActivity = (
  f: AuthFetch,
  input: PlanInput,
): Promise<ApiResult<Activity>> =>
  sendJson(f, "POST", `${M}/activities`, input);
export const completeActivity = (
  f: AuthFetch,
  id: string,
  outcome: string,
  followUpOn?: string,
  notes?: string,
): Promise<ApiResult<Activity>> =>
  sendJson(f, "POST", `${M}/activities/${id}/complete`, {
    outcome,
    followUpOn,
    notes,
  });
export const rescheduleActivity = (
  f: AuthFetch,
  id: string,
  date: string,
): Promise<ApiResult<Activity>> =>
  sendJson(f, "POST", `${M}/activities/${id}/reschedule`, { date });
export const cancelActivity = (
  f: AuthFetch,
  id: string,
  reason: string,
): Promise<ApiResult<Activity>> =>
  sendJson(f, "POST", `${M}/activities/${id}/cancel`, { reason });
export const removeAttachment = (
  f: AuthFetch,
  activityId: string,
  fileId: string,
  reason: string,
): Promise<ApiResult<void>> =>
  sendJson(
    f,
    "DELETE",
    `${M}/activities/${activityId}/attachments/${fileId}`,
    { reason },
    false,
  );

/** Uploads one file to a visit; the server checks type, size and the ten-file limit. */
export async function uploadAttachment(
  f: AuthFetch,
  activityId: string,
  file: File,
): Promise<ApiResult<AttachmentRef>> {
  try {
    const form = new FormData();
    form.append("file", file);
    const response = await f(`${M}/activities/${activityId}/attachments`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      const body = (await response.json().catch(() => ({}))) as {
        reason?: string;
      };
      return {
        ok: false,
        reason: body.reason ?? GENERIC_FAILURE,
        status: response.status,
      };
    }
    return { ok: true, data: (await response.json()) as AttachmentRef };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

/** Downloads a file with the access token and hands it to the browser as a save-as, never opening it inline. */
export async function downloadAttachment(
  f: AuthFetch,
  fileId: string,
  name: string,
): Promise<ApiResult<void>> {
  try {
    const response = await f(`${M}/files/${fileId}`);
    if (!response.ok) {
      return {
        ok: false,
        reason: "Could not download the file.",
        status: response.status,
      };
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 10_000);
    return { ok: true, data: undefined };
  } catch {
    return { ok: false, reason: "Could not download the file." };
  }
}

export const listProposals = (
  f: AuthFetch,
  prospectId: string,
): Promise<ApiResult<Proposal[]>> =>
  getJson(
    f,
    `${M}/prospects/${prospectId}/proposals`,
    "Could not load the proposals.",
  );
export const createProposal = (
  f: AuthFetch,
  prospectId: string,
  input: ProposalInput,
): Promise<ApiResult<Proposal>> =>
  sendJson(f, "POST", `${M}/prospects/${prospectId}/proposals`, input);

export const getDashboard = (
  f: AuthFetch,
  period?: string,
): Promise<ApiResult<MarketingDashboard>> =>
  getJson(
    f,
    `${M}/dashboard?${queryString({ period })}`,
    "Could not load the dashboard.",
  );
export const getSettings = (f: AuthFetch): Promise<ApiResult<Settings>> =>
  getJson(f, `${M}/settings`, "Could not load the settings.");
export const saveSettings = (
  f: AuthFetch,
  input: Settings,
): Promise<ApiResult<Settings>> => sendJson(f, "PUT", `${M}/settings`, input);
