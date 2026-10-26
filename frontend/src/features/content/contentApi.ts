import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Content management (spec 029, contracts/content-api.md). */
export const CONTENT_ROUTE = "/operations/content";
export const MY_CONTENT_ROUTE = "/operations/content";
const BASE = "/api/v1/content";

export type ContentStatus =
  "DRAFT" | "ASSIGNED" | "SUBMITTED" | "APPROVED" | "RETURNED";

export const STATUS_LABELS: Record<ContentStatus, string> = {
  DRAFT: "Draft",
  ASSIGNED: "Assigned",
  SUBMITTED: "Submitted",
  APPROVED: "Approved",
  RETURNED: "Returned",
};

export interface ContentRow {
  id: string;
  code: string;
  title: string;
  category: string;
  subject: string;
  authorUserId: string;
  authorName: string;
  status: ContentStatus;
  statusLabel: string;
  currentVersion: number;
  dueOn: string | null;
  overdue: boolean;
  updatedAt: string;
}

export interface ContentSummary {
  draft: number;
  assigned: number;
  submitted: number;
  approved: number;
  returned: number;
  overdue: number;
}

export interface ContentAuthor {
  userId: string;
  displayName: string;
  roles: string[];
}

export interface TypeLimit {
  extension: string;
  maxBytes: number;
}

export interface VersionView {
  number: number;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  authorUserId: string;
  authorName: string;
  submittedByUserId: string;
  submittedByName: string;
  note: string | null;
  submittedAt: string;
  removed: boolean;
  removalReason: string | null;
  lastApproved: boolean;
}

export interface ReviewView {
  versionNumber: number;
  reviewerUserId: string;
  reviewerName: string;
  decision: "APPROVED" | "RETURNED";
  comment: string | null;
  decidedAt: string;
}

export interface HistoryLine {
  event: string;
  actorName: string;
  at: string;
  before: string | null;
  after: string | null;
  note: string | null;
}

export interface ContentActions {
  submit: boolean;
  edit: boolean;
  approve: boolean;
  returnForRevision: boolean;
  removeFile: boolean;
}

export interface ContentDetail {
  id: string;
  code: string;
  title: string;
  category: string;
  subject: string;
  description: string | null;
  authorUserId: string;
  authorName: string;
  createdByUserId: string;
  createdByName: string;
  status: ContentStatus;
  statusLabel: string;
  currentVersion: number;
  lastApprovedVersion: number | null;
  dueOn: string | null;
  overdue: boolean;
  reviewerName: string | null;
  approvedOn: string | null;
  taskId: string | null;
  version: number;
  versions: VersionView[];
  reviews: ReviewView[];
  history: HistoryLine[];
  actions: ContentActions;
}

export interface ContentFilters {
  view?: "mine" | "all";
  status?: string;
  authorUserId?: string;
  category?: string;
  subject?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export const listContent = (
  f: AuthFetch,
  filters: ContentFilters,
): Promise<ApiResult<PageOf<ContentRow>>> =>
  getJson(f, `${BASE}?${queryString({ ...filters })}`, "Could not load the content.");

export const getSummary = (f: AuthFetch): Promise<ApiResult<ContentSummary>> =>
  getJson(f, `${BASE}/summary`, "Could not load the counts.");

export const getAuthors = (f: AuthFetch): Promise<ApiResult<ContentAuthor[]>> =>
  getJson(f, `${BASE}/authors`, "Could not load the authors.");

export const getLimits = (
  f: AuthFetch,
): Promise<ApiResult<{ types: TypeLimit[] }>> =>
  getJson(f, `${BASE}/limits`, "Could not load the accepted file types.");

export const getContent = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<ContentDetail>> =>
  getJson(f, `${BASE}/${id}`, "Could not load this content.");

export interface ContentInput {
  title: string;
  category: string;
  subject: string;
  description: string;
  authorUserId?: string;
  dueOn?: string;
}

export const createContent = (
  f: AuthFetch,
  input: ContentInput,
): Promise<ApiResult<ContentDetail>> => sendJson(f, "POST", BASE, input);

export const updateContent = (
  f: AuthFetch,
  id: string,
  input: ContentInput & { version: number },
): Promise<ApiResult<ContentDetail>> => sendJson(f, "PUT", `${BASE}/${id}`, input);

export const approveContent = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<ContentDetail>> => sendJson(f, "POST", `${BASE}/${id}/approve`, {});

export const returnContent = (
  f: AuthFetch,
  id: string,
  comment: string,
): Promise<ApiResult<ContentDetail>> =>
  sendJson(f, "POST", `${BASE}/${id}/return`, { comment });

export const removeFile = (
  f: AuthFetch,
  id: string,
  number: number,
  reason: string,
): Promise<ApiResult<ContentDetail>> =>
  sendJson(f, "DELETE", `${BASE}/${id}/versions/${number}/file`, { reason });

/** Sends one file as the next version. The file is required; the server refuses the wrong type or size. */
export async function submitVersion(
  f: AuthFetch,
  id: string,
  file: File,
  note: string,
): Promise<ApiResult<ContentDetail>> {
  try {
    const form = new FormData();
    form.append("file", file);
    if (note.trim()) form.append("note", note.trim());
    const response = await f(`${BASE}/${id}/versions`, {
      method: "POST",
      body: form,
    });
    if (!response.ok) {
      let reason = GENERIC_FAILURE;
      try {
        reason =
          ((await response.json()) as { reason?: string }).reason ?? reason;
      } catch {
        // keep the generic reason
      }
      return { ok: false, reason, status: response.status };
    }
    return { ok: true, data: (await response.json()) as ContentDetail };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

/** Downloads one version as a file through the signed-in session. */
export async function downloadVersion(
  f: AuthFetch,
  id: string,
  number: number,
  name: string,
): Promise<ApiResult<void>> {
  try {
    const response = await f(`${BASE}/${id}/versions/${number}/file`);
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

/** "500 MB" style text for a limit in bytes. */
export function megabytes(bytes: number): string {
  return `${Math.round(bytes / (1024 * 1024))} MB`;
}

export function fileSize(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KB`;
  return `${bytes} B`;
}

/** The refusal text for a file picked on the phone or desktop, before it is sent; null when the file is acceptable. */
export function checkFile(file: File, types: TypeLimit[]): string | null {
  const dot = file.name.lastIndexOf(".");
  const extension = dot < 0 ? "" : file.name.slice(dot + 1).toLowerCase();
  const wanted = types.find((t) => t.extension === extension);
  if (!wanted) {
    return `This file type is not accepted. Accepted: ${types
      .map((t) => t.extension)
      .join(", ")}.`;
  }
  if (file.size > wanted.maxBytes) {
    return `A .${extension} file can be at most ${megabytes(wanted.maxBytes)}.`;
  }
  if (file.size === 0) return "The file is empty.";
  return null;
}
