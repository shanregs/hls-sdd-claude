import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Teacher observation and performance (spec 026, contracts/performance-api.md). */
export const PERFORMANCE_ROUTE = "/operations/teacher-performance";

const BASE = "/api/v1/teacher-performance";

export type Result = "GOOD" | "NEEDS_IMPROVEMENT" | "UNSUITABLE";

export const RESULT_LABELS: Record<Result, string> = {
  GOOD: "Good",
  NEEDS_IMPROVEMENT: "Needs improvement",
  UNSUITABLE: "Unsuitable",
};

export const OBSERVATION_STATUS_LABELS: Record<string, string> = {
  DRAFT: "Draft",
  SUBMITTED: "Submitted",
  NOT_HELD: "Not held",
  SUPERSEDED: "Superseded",
};

export const ISSUE_STATUS_LABELS: Record<string, string> = {
  OPEN: "Open",
  UNDER_CORRECTION: "Under correction",
  REFERRED: "Referred",
  RESOLVED: "Resolved",
  CANCELLED: "Cancelled",
};

export const RESOLUTION_LABELS: Record<string, string> = {
  IMPROVED: "Improved",
  CONTINUE: "Continue",
  TRANSFERRED: "Transferred",
  REPLACED: "Replaced",
  EXITED: "Exited",
};

export const FEEDBACK_STATUS_LABELS: Record<string, string> = {
  RECORDED: "Recorded",
  WITHDRAWN: "Withdrawn",
  SUPERSEDED: "Superseded",
};

export interface Option {
  value: string;
  label: string;
}

export interface Options {
  areas: Option[];
  results: Option[];
  notHeldReasons: Option[];
  permissionWays: Option[];
  feedbackCategories: Option[];
  planReasons: Option[];
}

// ------------------------------------------------------------------ observations

export interface ObservationRow {
  id: string;
  rootId: string;
  versionNo: number;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  observerName: string;
  observedOn: string;
  status: string;
  overallResult: Result | null;
  averageRating: number | null;
  notHeldReason: string | null;
  principalDeclined: boolean;
  permissionRecorded: boolean;
  issueId: string | null;
}

export interface ObservationActions {
  edit: boolean;
  submit: boolean;
  notHeld: boolean;
  correct: boolean;
  attach: boolean;
}

export interface AttachmentView {
  fileId: string;
  name: string;
  contentType: string;
  sizeBytes: number;
  addedAt: string;
  addedBy: string;
}

export interface ObservationView {
  id: string;
  rootId: string;
  versionNo: number;
  current: boolean;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  observerUserId: string;
  observerName: string;
  observedOn: string;
  className: string | null;
  subject: string | null;
  durationMinutes: number | null;
  status: string;
  notHeldReason: string | null;
  notHeldReasonLabel: string | null;
  notHeldText: string | null;
  principalDeclined: boolean;
  overallResult: Result | null;
  averageRating: number | null;
  suggestedResult: Result | null;
  ratings: Record<string, number | null>;
  comments: string | null;
  strengths: string | null;
  improvements: string | null;
  principalComments: string | null;
  permissionRecorded: boolean;
  permissionBy: string | null;
  permissionWay: string | null;
  permissionOn: string | null;
  permissionLabel: string | null;
  defaultPrincipalName: string | null;
  issueId: string | null;
  issueStatus: string | null;
  followupOfIssueId: string | null;
  nextStep: "CORRECTIVE_ACTION" | "REFERRAL" | null;
  correctionReason: string | null;
  submittedAt: string | null;
  attachments: AttachmentView[];
  actions: ObservationActions;
  version: number;
}

/** The body of save, submit and correct; ratings hold only the areas that were rated (the rest are Not observed). */
export interface ObservationForm {
  observedOn: string | null;
  className: string | null;
  subject: string | null;
  durationMinutes: number | null;
  ratings: Record<string, number>;
  overallResult: Result | null;
  comments: string | null;
  strengths: string | null;
  improvements: string | null;
  principalComments: string | null;
  permission: { by: string; way: string | null; on: string | null } | null;
  version: number;
}

export interface Preview {
  average: number | null;
  suggestedResult: Result | null;
}

export interface ObservationFilters {
  zoneId?: string;
  schoolId?: string;
  managerId?: string;
  teacherId?: string;
  result?: string;
  status?: string;
  from?: string;
  to?: string;
  principalDeclined?: boolean;
  page?: number;
}

export function getOptions(authFetch: AuthFetch): Promise<ApiResult<Options>> {
  return getJson(authFetch, `${BASE}/options`, "Could not load the choices.");
}

export function listObservations(
  authFetch: AuthFetch,
  filters: ObservationFilters,
): Promise<ApiResult<PageOf<ObservationRow>>> {
  return getJson(
    authFetch,
    `${BASE}/observations?${queryString({ size: 50, ...filters })}`,
    "Could not load the observations.",
  );
}

export function getObservation(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<ObservationView>> {
  return getJson(
    authFetch,
    `${BASE}/observations/${id}`,
    "Could not load this observation.",
  );
}

export function getObservationVersions(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<ObservationView[]>> {
  return getJson(
    authFetch,
    `${BASE}/observations/${id}?versions=true`,
    "Could not load the earlier versions.",
  );
}

export function startObservation(
  authFetch: AuthFetch,
  teacherId: string,
  observedOn?: string,
): Promise<ApiResult<ObservationView>> {
  return sendJson(authFetch, "POST", `${BASE}/observations`, {
    teacherId,
    observedOn: observedOn || null,
  });
}

export function saveObservation(
  authFetch: AuthFetch,
  id: string,
  form: ObservationForm,
): Promise<ApiResult<ObservationView>> {
  return sendJson(authFetch, "PUT", `${BASE}/observations/${id}`, form);
}

export function previewRatings(
  authFetch: AuthFetch,
  ratings: Record<string, number>,
): Promise<ApiResult<Preview>> {
  return sendJson(authFetch, "POST", `${BASE}/observations/preview`, {
    ratings,
  });
}

export function submitObservation(
  authFetch: AuthFetch,
  id: string,
  form: ObservationForm,
): Promise<ApiResult<ObservationView>> {
  return sendJson(authFetch, "POST", `${BASE}/observations/${id}/submit`, form);
}

export function markNotHeld(
  authFetch: AuthFetch,
  id: string,
  body: { reason: string; text: string | null; version: number },
): Promise<ApiResult<ObservationView>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/observations/${id}/not-held`,
    body,
  );
}

export function correctObservation(
  authFetch: AuthFetch,
  id: string,
  reason: string,
  form: ObservationForm,
): Promise<ApiResult<ObservationView>> {
  return sendJson(authFetch, "POST", `${BASE}/observations/${id}/correct`, {
    reason,
    form,
  });
}

async function failureReason(response: Response): Promise<string> {
  try {
    return (
      ((await response.json()) as { reason?: string }).reason ?? GENERIC_FAILURE
    );
  } catch {
    return GENERIC_FAILURE;
  }
}

export async function uploadAttachment(
  authFetch: AuthFetch,
  id: string,
  file: File,
): Promise<ApiResult<AttachmentView>> {
  try {
    const form = new FormData();
    form.append("file", file);
    const response = await authFetch(`${BASE}/observations/${id}/attachments`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      return {
        ok: false,
        reason: await failureReason(response),
        status: response.status,
      };
    }
    return { ok: true, data: (await response.json()) as AttachmentView };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export function removeAttachment(
  authFetch: AuthFetch,
  id: string,
  fileId: string,
  reason: string,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "DELETE",
    `${BASE}/observations/${id}/attachments/${fileId}?reason=${encodeURIComponent(reason)}`,
    undefined,
    false,
  );
}

/** Fetches an attachment as a download (the server sends it as an attachment, never inline). */
export async function downloadAttachment(
  authFetch: AuthFetch,
  id: string,
  fileId: string,
): Promise<ApiResult<Blob>> {
  try {
    const response = await authFetch(
      `${BASE}/observations/${id}/attachments/${fileId}`,
    );
    if (!response.ok) {
      return {
        ok: false,
        reason: await failureReason(response),
        status: response.status,
      };
    }
    return { ok: true, data: await response.blob() };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

// ------------------------------------------------------------------ Principal feedback

export interface FeedbackRow {
  id: string;
  rootId: string;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  principalName: string;
  givenOn: string;
  category: string;
  categoryLabel: string;
  actionRequired: boolean;
  status: string;
  issueId: string | null;
}

export interface FeedbackActions {
  correct: boolean;
  withdraw: boolean;
  openIssue: boolean;
  planObservation: boolean;
}

export interface FeedbackView {
  id: string;
  rootId: string;
  versionNo: number;
  current: boolean;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  principalName: string;
  givenOn: string;
  category: string;
  categoryLabel: string;
  comments: string;
  actionRequired: boolean;
  observationRootId: string | null;
  recordedBy: string;
  recordedByName: string | null;
  status: string;
  withdrawnReason: string | null;
  withdrawnAt: string | null;
  issueId: string | null;
  issueStatus: string | null;
  offerIssue: boolean;
  correctionReason: string | null;
  actions: FeedbackActions;
  version: number;
}

export interface NewFeedback {
  teacherId: string;
  principalName: string;
  givenOn: string;
  category: string;
  comments: string;
  actionRequired: boolean;
}

export function listFeedback(
  authFetch: AuthFetch,
  filters: {
    teacherId?: string;
    category?: string;
    actionRequired?: boolean;
    status?: string;
    from?: string;
    to?: string;
    page?: number;
  },
): Promise<ApiResult<PageOf<FeedbackRow>>> {
  return getJson(
    authFetch,
    `${BASE}/feedback?${queryString({ size: 50, ...filters })}`,
    "Could not load the Principal feedback.",
  );
}

export function getFeedback(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<FeedbackView>> {
  return getJson(
    authFetch,
    `${BASE}/feedback/${id}`,
    "Could not load this feedback.",
  );
}

export function recordFeedback(
  authFetch: AuthFetch,
  body: NewFeedback,
): Promise<ApiResult<FeedbackView>> {
  return sendJson(authFetch, "POST", `${BASE}/feedback`, body);
}

export function withdrawFeedback(
  authFetch: AuthFetch,
  id: string,
  reason: string,
  version: number,
): Promise<ApiResult<FeedbackView>> {
  return sendJson(authFetch, "POST", `${BASE}/feedback/${id}/withdraw`, {
    reason,
    version,
  });
}

export function openIssueFromFeedback(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<IssueView>> {
  return sendJson(authFetch, "POST", `${BASE}/feedback/${id}/issue`, {});
}

export function planFromFeedback(
  authFetch: AuthFetch,
  id: string,
  plannedOn?: string,
): Promise<ApiResult<PlanView>> {
  return sendJson(authFetch, "POST", `${BASE}/feedback/${id}/plan`, {
    plannedOn: plannedOn || null,
  });
}

// ------------------------------------------------------------------ plans

export interface PlanView {
  id: string;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  plannedOn: string;
  reason: string;
  reasonLabel: string;
  reasonText: string | null;
  status: string;
  plannedBy: string;
  plannedByName: string | null;
  sourceFeedbackRootId: string | null;
  issueId: string | null;
  managerUserId: string | null;
  managerName: string | null;
  overdue: boolean;
  daysOverdue: number;
  lastResult: Result | null;
  created: boolean;
  actions: { edit: boolean; cancel: boolean };
  version: number;
}

export type PlanListView = "planned" | "overdue";

export function listPlans(
  authFetch: AuthFetch,
  view: PlanListView,
): Promise<ApiResult<PlanView[]>> {
  return getJson(
    authFetch,
    `${BASE}/plans?view=${view}`,
    "Could not load the planned observations.",
  );
}

export function createPlan(
  authFetch: AuthFetch,
  body: {
    teacherId: string;
    plannedOn: string;
    reason: string;
    reasonText: string | null;
  },
): Promise<ApiResult<PlanView>> {
  return sendJson(authFetch, "POST", `${BASE}/plans`, body);
}

export function updatePlan(
  authFetch: AuthFetch,
  id: string,
  body: {
    plannedOn: string;
    reason: string;
    reasonText: string | null;
    version: number;
  },
): Promise<ApiResult<PlanView>> {
  return sendJson(authFetch, "PUT", `${BASE}/plans/${id}`, body);
}

export function cancelPlan(
  authFetch: AuthFetch,
  id: string,
  reason: string,
): Promise<ApiResult> {
  return sendJson(
    authFetch,
    "DELETE",
    `${BASE}/plans/${id}?reason=${encodeURIComponent(reason)}`,
    undefined,
    false,
  );
}

// ------------------------------------------------------------------ issues

export interface IssueRow {
  id: string;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  category: string;
  status: string;
  resolution: string | null;
  correctionRounds: number;
  openedOn: string;
  closedOn: string | null;
  managerName: string | null;
}

export interface IssueLink {
  type: "OBSERVATION" | "FEEDBACK";
  rootId: string;
  id: string | null;
  date: string;
  result: string | null;
  summary: string;
}

export interface CorrectiveActionView {
  round: number;
  discussed: string;
  requiredChange: string;
  followUpOn: string;
  recordedBy: string;
  recordedByName: string | null;
  recordedOn: string;
  followUpObservationRootId: string | null;
}

export interface ReferralView {
  recommendation: "TRANSFER" | "REPLACE";
  reason: string;
  referredBy: string;
  referredByName: string | null;
  referredOn: string;
  decisionOutcome: string | null;
  decisionReportedOn: string | null;
}

export interface IssueActions {
  correctiveAction: boolean;
  refer: boolean;
  cancel: boolean;
  closeContinue: boolean;
  escalate: boolean;
}

export interface IssueView {
  id: string;
  teacherId: string;
  teacherName: string | null;
  schoolId: string;
  schoolName: string | null;
  managerUserId: string | null;
  managerName: string | null;
  sourceType: string;
  sourceRootId: string;
  category: string;
  status: string;
  resolution: string | null;
  correctionRounds: number;
  openedBy: string | null;
  openedByName: string | null;
  openedOn: string;
  closedOn: string | null;
  closingReason: string | null;
  needsCorrectiveAction: boolean;
  followUpOn: string | null;
  links: IssueLink[];
  correctiveActions: CorrectiveActionView[];
  referral: ReferralView | null;
  actions: IssueActions;
  version: number;
}

export type IssueListView = "open" | "referred" | "all";

export function listIssues(
  authFetch: AuthFetch,
  view: IssueListView,
  page = 0,
): Promise<ApiResult<PageOf<IssueRow>>> {
  return getJson(
    authFetch,
    `${BASE}/issues?${queryString({ size: 50, view: view === "all" ? undefined : view, page })}`,
    "Could not load the performance issues.",
  );
}

export function getIssue(
  authFetch: AuthFetch,
  id: string,
): Promise<ApiResult<IssueView>> {
  return getJson(
    authFetch,
    `${BASE}/issues/${id}`,
    "Could not load this issue.",
  );
}

export function recordCorrectiveAction(
  authFetch: AuthFetch,
  id: string,
  body: {
    discussed: string;
    requiredChange: string;
    followUpOn: string;
    version: number;
  },
): Promise<ApiResult<IssueView>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/issues/${id}/corrective-actions`,
    body,
  );
}

export function escalateIssue(
  authFetch: AuthFetch,
  id: string,
  note: string,
): Promise<ApiResult<IssueView>> {
  return sendJson(authFetch, "POST", `${BASE}/issues/${id}/escalate`, { note });
}

export function referIssue(
  authFetch: AuthFetch,
  id: string,
  body: { recommendation: string; reason: string; version: number },
): Promise<ApiResult<IssueView>> {
  return sendJson(authFetch, "POST", `${BASE}/issues/${id}/referral`, body);
}

export function closeIssue(
  authFetch: AuthFetch,
  id: string,
  body: { outcome: "CANCELLED" | "CONTINUE"; reason: string; version: number },
): Promise<ApiResult<IssueView>> {
  return sendJson(authFetch, "POST", `${BASE}/issues/${id}/close`, body);
}

// ------------------------------------------------------------------ Teacher page

export interface TimelineEntry {
  type: "OBSERVATION" | "FEEDBACK" | "ISSUE" | "REFERRAL";
  id: string;
  date: string;
  title: string;
  result: string | null;
  status: string;
}

export interface TeacherPage {
  teacherId: string;
  teacherName: string;
  status: string;
  schoolId: string | null;
  schoolName: string | null;
  managerName: string | null;
  latestResult: Result | null;
  openPlan: PlanView | null;
  entries: TimelineEntry[];
  observations: ObservationRow[];
  feedback: FeedbackRow[];
  issues: IssueRow[];
}

export function getTeacherPage(
  authFetch: AuthFetch,
  teacherId: string,
): Promise<ApiResult<TeacherPage>> {
  return getJson(
    authFetch,
    `${BASE}/teachers/${teacherId}`,
    "Could not load this Teacher's performance.",
  );
}
