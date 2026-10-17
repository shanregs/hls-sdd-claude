import { toBase64 } from "../formats/base64";
import { api, fetchBinary, upload, type UploadFile } from "./httpClient";

/**
 * Typed calls to the marketing visit endpoints of spec 023, unchanged (spec 034 research §1). The shapes copy the server's
 * responses (specs/034-mobile-visits-tasks/data-model.md); the app computes none of the statuses they carry.
 */
const BASE = "/api/v1/marketing";

/** The stored status of a visit. */
export type VisitStatus = "PLANNED" | "COMPLETED" | "CANCELLED";

/** What the Manager sees: the stored status, or MISSED for a planned visit whose date has passed (the server decides). */
export type EffectiveVisitStatus = VisitStatus | "MISSED";

export type VisitKind = "VISIT" | "CALL" | "PROPOSAL_MEETING" | "FOLLOW_UP";

export interface TargetRef {
  id: string;
  name: string;
}

export interface Person {
  userId: string;
  name: string;
}

export interface AttachmentRef {
  fileId: string;
  name: string;
  sizeBytes: number;
  contentType: string;
}

export interface DateChange {
  from: string;
  to: string;
}

/** One visit, call or meeting (a marketing activity). */
export interface Visit {
  id: string;
  type: VisitKind;
  status: VisitStatus;
  effectiveStatus: EffectiveVisitStatus;
  rescheduled: boolean;
  date: string;
  notes: string | null;
  outcome: string | null;
  followUpOn: string | null;
  followUpOverdue: boolean;
  cancelReason: string | null;
  prospect: TargetRef | null;
  school: TargetRef | null;
  attendees: Person[];
  attachments: AttachmentRef[];
  dateHistory: DateChange[];
  version: number;
}

export interface Prospect {
  id: string;
  name: string;
  board: string | null;
  zoneName: string | null;
  stage: string;
  won: boolean;
  schoolId: string | null;
  contactPerson: string | null;
}

export interface ProspectPage {
  content: Prospect[];
  page: number;
  size: number;
  totalElements: number;
}

export interface PlanVisitBody {
  prospectId: string;
  type: VisitKind;
  date: string;
  attendeeUserIds?: string[];
  notes?: string;
}

export interface CompleteVisitBody {
  outcome: string;
  notes?: string;
  followUpOn?: string;
  version: number;
}

/** The visits of the Manager between two dates, both included. `drives` in the answer are not used. */
export async function listVisits(from: string, to: string): Promise<Visit[]> {
  const query = `from=${from}&to=${to}&mine=true`;
  const answer = await api.get<{ content: Visit[] }>(`${BASE}/activities?${query}`);
  return answer.content ?? [];
}

export const getVisit = (id: string) => api.get<Visit>(`${BASE}/activities/${id}`);

export const planVisit = (body: PlanVisitBody) => api.post<Visit>(`${BASE}/activities`, body);

export const completeVisit = (id: string, body: CompleteVisitBody) =>
  api.post<Visit>(`${BASE}/activities/${id}/complete`, body);

export const rescheduleVisit = (id: string, body: { date: string; version: number }) =>
  api.post<Visit>(`${BASE}/activities/${id}/reschedule`, body);

export const cancelVisit = (id: string, body: { reason: string; version: number }) =>
  api.post<Visit>(`${BASE}/activities/${id}/cancel`, body);

export const attachVisitPhoto = (id: string, file: UploadFile) =>
  upload<AttachmentRef>(`${BASE}/activities/${id}/attachments`, "file", file);

/** A file the server holds, with what it says about it. */
export interface FileView {
  /** Ready for an image view; held in memory only. */
  dataUri: string;
  contentType: string;
  sizeBytes: number;
  fileName: string | null;
}

/** Downloads a file as bytes and returns it as an in-memory address plus the server's details. */
export async function toFileView(path: string): Promise<FileView> {
  const answer = await fetchBinary(path);
  return {
    dataUri: `data:${answer.contentType};base64,${toBase64(answer.bytes)}`,
    contentType: answer.contentType,
    sizeBytes: answer.sizeBytes,
    fileName: answer.fileName,
  };
}

export const openVisitFile = (fileId: string) => toFileView(`${BASE}/files/${fileId}`);

/** A typed search over the prospects the Manager may see; a won prospect is a School. */
export function listProspects(params: { query?: string; page?: number; size?: number } = {}): Promise<ProspectPage> {
  const parts = [`page=${params.page ?? 0}`, `size=${params.size ?? 25}`];
  if (params.query && params.query.trim() !== "") parts.push(`query=${encodeURIComponent(params.query.trim())}`);
  return api.get<ProspectPage>(`${BASE}/prospects?${parts.join("&")}`);
}

/** The staff who can attend a visit (the list the web uses for the same choice). */
export const listColleagues = () => api.get<Person[]>("/api/v1/recruitment/interviewers");
