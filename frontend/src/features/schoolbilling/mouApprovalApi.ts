import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";
import type { ContractRow, SalaryMode } from "./schoolContractsApi";

/** Spec 031 contracts/mou-approval-api.md. Dates are ISO; history times are instants. */

export type ApprovalState =
  | "NOT_APPLICABLE"
  | "DRAFT"
  | "PENDING"
  | "APPROVED"
  | "APPROVED_WITHOUT_DOCUMENT"
  | "APPROVED_BEFORE_APPROVAL_STEP"
  | "REJECTED"
  | "CANCELLED";

export type DocumentState = "NONE" | "ATTACHED";

export type MouAction =
  | "UPLOAD"
  | "REMOVE_DOCUMENT"
  | "SUBMIT"
  | "APPROVE"
  | "REJECT"
  | "AMEND_TERMS";

export interface PersonRef {
  id: string;
  name: string;
}

export interface MouDocument {
  id: string;
  versionNo: number;
  name: string;
  contentType: string;
  sizeBytes: number;
  addedBy: PersonRef;
  addedAt: string;
  current: boolean;
  removed: { by: PersonRef; at: string; reason: string } | null;
}

export interface HistoryEntry {
  at: string;
  action: string;
  round: number;
  actor: PersonRef | null;
  actorRole: string | null;
  note: string | null;
  detail: string | null;
  documentVersion: number | null;
}

export interface ApprovalSummary {
  contractId: string;
  state: ApprovalState;
  round: number;
  submittedBy: PersonRef | null;
  submittedAt: string | null;
  decidedBy: PersonRef | null;
  decidedAt: string | null;
  decisionReason: string | null;
  selfDecided: boolean;
  document: { state: DocumentState; current: MouDocument | null };
  version: number;
  actions: MouAction[];
}

export interface Approval extends ApprovalSummary {
  documents: MouDocument[];
  history: HistoryEntry[];
}

export interface PendingRow {
  contractId: string;
  schoolId: string;
  schoolName: string;
  zoneManagerName: string | null;
  round: number;
  submittedBy: PersonRef | null;
  submittedAt: string;
  documentState: DocumentState;
  teacherCount: number | null;
  salaryMode: SalaryMode | null;
  rate: string | null;
  startsOn: string;
  endsOn: string | null;
  approvalVersion: number;
}

export interface AmendTermsInput {
  version: number;
  rate?: string;
  positions?: { number: number; salary: string }[];
  signedOn?: string;
  startsOn?: string;
  endsOn?: string;
}

const BASE = "/api/v1/school-contracts";

export function getApproval(
  f: AuthFetch,
  contractId: string,
): Promise<ApiResult<Approval>> {
  return getJson(
    f,
    `${BASE}/contracts/${contractId}/approval`,
    "Could not load the approval.",
  );
}

export async function uploadMouDocument(
  f: AuthFetch,
  contractId: string,
  file: File,
): Promise<ApiResult<Approval>> {
  try {
    const form = new FormData();
    form.append("file", file);
    const response = await f(`${BASE}/contracts/${contractId}/documents`, {
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
    return { ok: true, data: (await response.json()) as Approval };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

/** Downloads a version with the access token and saves it; never opened inline. */
export async function downloadMouDocument(
  f: AuthFetch,
  contractId: string,
  documentId: string,
  name: string,
): Promise<ApiResult<void>> {
  try {
    const response = await f(
      `${BASE}/contracts/${contractId}/documents/${documentId}/file`,
    );
    if (!response.ok) {
      return {
        ok: false,
        reason: "Could not download the document.",
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
    return { ok: false, reason: "Could not download the document." };
  }
}

export function removeMouDocument(
  f: AuthFetch,
  contractId: string,
  documentId: string,
  reason: string,
): Promise<ApiResult<Approval>> {
  return sendJson(
    f,
    "POST",
    `${BASE}/contracts/${contractId}/documents/${documentId}/remove`,
    { reason },
  );
}

export function submitMou(
  f: AuthFetch,
  contractId: string,
  version: number,
): Promise<ApiResult<Approval>> {
  return sendJson(f, "POST", `${BASE}/contracts/${contractId}/submit`, {
    version,
  });
}

export function approveMou(
  f: AuthFetch,
  contractId: string,
  version: number,
): Promise<ApiResult<Approval>> {
  return sendJson(f, "POST", `${BASE}/contracts/${contractId}/approve`, {
    version,
  });
}

export function rejectMou(
  f: AuthFetch,
  contractId: string,
  version: number,
  reason: string,
): Promise<ApiResult<Approval>> {
  return sendJson(f, "POST", `${BASE}/contracts/${contractId}/reject`, {
    version,
    reason,
  });
}

export interface AmendResult {
  approval: Approval;
  contract: ContractRow;
}

export function amendMouTerms(
  f: AuthFetch,
  contractId: string,
  input: AmendTermsInput,
): Promise<ApiResult<AmendResult>> {
  return sendJson(f, "PATCH", `${BASE}/contracts/${contractId}/terms`, input);
}

export function listPendingApprovals(
  f: AuthFetch,
  page: number,
  size: number,
): Promise<ApiResult<PageOf<PendingRow>>> {
  return getJson(
    f,
    `${BASE}/approvals/pending?${queryString({ page, size })}`,
    "Could not load the pending approvals.",
  );
}
