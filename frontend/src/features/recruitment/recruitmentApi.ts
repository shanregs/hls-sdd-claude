import {
  GENERIC_FAILURE,
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
} from "../common/masterDataApi";

/** Spec 016 contracts/recruitment-api.md. Amounts are strings with two decimals; dates are ISO. */

export interface Contact {
  name: string;
  phone?: string | null;
  email?: string | null;
}

export interface College {
  id: string;
  name: string;
  city: string;
  placementOfficer: Contact | null;
  principal: Contact | null;
  drives: number;
  version: number;
}

export interface CollegeInput {
  name: string;
  city: string;
  placementOfficer?: Contact | null;
  principal?: Contact | null;
  version?: number;
}

export interface PersonRef {
  userId: string;
  name: string;
}

export type DriveStatus = "PLANNED" | "HELD" | "CANCELLED";

export interface Drive {
  id: string;
  college: { id: string; name: string; city: string };
  season: string | null;
  venue: string | null;
  status: DriveStatus;
  cancelReason: string | null;
  dates: string[];
  interviewers: PersonRef[];
  scheduledBy: string;
  candidates: number;
  outcomes: Record<string, number>;
  heldNoCandidates: boolean;
  version: number;
}

export interface DriveInput {
  collegeId: string;
  dates: string[];
  venue?: string;
  season?: string;
  interviewerUserIds: string[];
  version?: number;
}

export type Outcome = "SELECTED" | "WAITLISTED" | "REJECTED";
export type Criterion = "SPEAKING" | "ENGLISH" | "COMMUNICATION";
export const CRITERIA: Criterion[] = ["SPEAKING", "ENGLISH", "COMMUNICATION"];

export interface Assessment {
  number: number;
  scores: Partial<Record<Criterion, number>>;
  remarks: string | null;
  assessedByName: string | null;
  assessedAt: string;
}

export interface Candidate {
  id: string;
  driveId: string;
  name: string;
  phone: string;
  email: string | null;
  degree: string | null;
  year: string | null;
  notes: string | null;
  outcome: Outcome | null;
  outcomeAt: string | null;
  teacherId: string | null;
  assessment: Assessment | null;
  version: number;
}

export interface NewCandidate {
  name: string;
  phone: string;
  email?: string;
  degree?: string;
  year?: string;
  notes?: string;
}

export interface ImportResult {
  saved: number;
  skipped: number;
  errors: { row: number; reason: string }[];
}

export interface HistoryRow {
  kind: string;
  value: string;
  note: string | null;
  byName: string | null;
  at: string;
}

export type OfferStatus =
  "DRAFT" | "ISSUED" | "ACCEPTED" | "DECLINED" | "EXPIRED" | "SUPERSEDED";

export interface Offer {
  id: string;
  candidateId: string;
  candidateName: string;
  college: string;
  role: string;
  monthlySalary: string;
  allowances: string | null;
  terms: string | null;
  expectedJoining: string | null;
  offerDate: string;
  responseDeadline: string;
  status: OfferStatus;
  supersedesId: string | null;
  declineReason: string | null;
  issuedByName: string | null;
  issuedAt: string | null;
  decidedAt: string | null;
  teacherId: string | null;
  version: number;
}

export interface OfferInput {
  role: string;
  monthlySalary: string;
  allowances?: string;
  terms?: string;
  expectedJoining?: string;
  offerDate: string;
  responseDeadline: string;
}

export interface Batch {
  id: string;
  name: string;
  startsOn: string;
  endsOn: string;
  trainer: string | null;
  venueType: "PHYSICAL" | "VIRTUAL";
  venue: string | null;
  seatLimit: number;
  enrolled: number;
  status: "PLANNED" | "RUNNING" | "COMPLETED" | "CANCELLED";
  version: number;
}

export interface BatchInput {
  name: string;
  startsOn: string;
  endsOn: string;
  trainer?: string;
  venueType: "PHYSICAL" | "VIRTUAL";
  venue?: string;
  seatLimit: number;
}

export type DayStatus = "PRESENT" | "HALF" | "ABSENT";

export interface RosterRow {
  enrolmentId: string;
  teacherId: string;
  name: string;
  result: "COMPLETED" | "NOT_COMPLETED" | null;
  remarks: string | null;
  followUp: "NEXT_BATCH" | "RELEASED" | null;
  days: {
    date: string;
    status: DayStatus;
    dayValue: number;
    reason: string | null;
  }[];
}

export interface RecruitRef {
  teacherId: string;
  name: string;
  status: string;
}

export interface DashboardCounts {
  drivesScheduled: number;
  drivesHeld: number;
  interviewed: number;
  assessed: number;
  selected: number;
  offered: number;
  accepted: number;
  inducted: number;
  readyToDeploy: number;
  placed: number;
  active: number;
  joiningRatio: string | null;
}

export interface Dashboard {
  colleges: {
    collegeId: string;
    name: string;
    city: string;
    counts: DashboardCounts;
  }[];
  totals: DashboardCounts;
  readyToDeployTotal: number;
}

export interface SchoolContacts {
  principal: Contact | null;
  accountant: Contact | null;
}

const R = "/api/v1/recruitment";
const I = "/api/v1/induction";

export const listColleges = (
  f: AuthFetch,
  query?: string,
): Promise<ApiResult<College[]>> =>
  getJson(
    f,
    `${R}/colleges?${queryString({ query })}`,
    "Could not load colleges.",
  );
export const createCollege = (
  f: AuthFetch,
  input: CollegeInput,
): Promise<ApiResult<College>> => sendJson(f, "POST", `${R}/colleges`, input);
export const updateCollege = (
  f: AuthFetch,
  id: string,
  input: CollegeInput,
): Promise<ApiResult<College>> =>
  sendJson(f, "PUT", `${R}/colleges/${id}`, input);

export const listDrives = (
  f: AuthFetch,
  params: { from?: string; to?: string; season?: string; mine?: boolean } = {},
): Promise<ApiResult<Drive[]>> =>
  getJson(f, `${R}/drives?${queryString(params)}`, "Could not load drives.");
export const getDrive = (f: AuthFetch, id: string): Promise<ApiResult<Drive>> =>
  getJson(f, `${R}/drives/${id}`, "Could not load this drive.");
export const scheduleDrive = (
  f: AuthFetch,
  input: DriveInput,
): Promise<ApiResult<Drive>> => sendJson(f, "POST", `${R}/drives`, input);
export const updateDrive = (
  f: AuthFetch,
  id: string,
  input: DriveInput,
): Promise<ApiResult<Drive>> => sendJson(f, "PUT", `${R}/drives/${id}`, input);
export const setDriveStatus = (
  f: AuthFetch,
  id: string,
  status: "HELD" | "CANCELLED",
  reason?: string,
): Promise<ApiResult<Drive>> =>
  sendJson(f, "POST", `${R}/drives/${id}/status`, { status, reason });

export const listCandidates = (
  f: AuthFetch,
  params: { drive?: string; outcome?: string; query?: string },
): Promise<ApiResult<Candidate[]>> =>
  getJson(
    f,
    `${R}/candidates?${queryString(params)}`,
    "Could not load candidates.",
  );
export const addCandidate = (
  f: AuthFetch,
  driveId: string,
  input: NewCandidate,
): Promise<ApiResult<Candidate>> =>
  sendJson(f, "POST", `${R}/drives/${driveId}/candidates`, input);
export const setOutcome = (
  f: AuthFetch,
  id: string,
  outcome: Outcome,
  note?: string,
): Promise<ApiResult<Candidate>> =>
  sendJson(f, "POST", `${R}/candidates/${id}/outcome`, { outcome, note });
export const assessCandidate = (
  f: AuthFetch,
  id: string,
  scores: Partial<Record<Criterion, number>>,
  remarks?: string,
): Promise<ApiResult<Candidate>> =>
  sendJson(f, "POST", `${R}/candidates/${id}/assessment`, { scores, remarks });
export const candidateHistory = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<HistoryRow[]>> =>
  getJson(f, `${R}/candidates/${id}/history`, "Could not load the history.");

/** Uploads a CSV file of candidates; the server reports the saved rows and each row it could not save. */
export async function importCandidates(
  f: AuthFetch,
  driveId: string,
  file: File,
): Promise<ApiResult<ImportResult>> {
  try {
    const form = new FormData();
    form.append("file", file);
    const response = await f(`${R}/drives/${driveId}/candidates/import`, {
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
    return { ok: true, data: (await response.json()) as ImportResult };
  } catch {
    return { ok: false, reason: GENERIC_FAILURE };
  }
}

export const listOffers = (
  f: AuthFetch,
  params: { status?: string; candidate?: string } = {},
): Promise<ApiResult<Offer[]>> =>
  getJson(f, `${R}/offers?${queryString(params)}`, "Could not load offers.");
export const createOffer = (
  f: AuthFetch,
  candidateId: string,
  input: OfferInput,
): Promise<ApiResult<Offer>> =>
  sendJson(f, "POST", `${R}/candidates/${candidateId}/offers`, input);
export const updateOffer = (
  f: AuthFetch,
  id: string,
  input: OfferInput,
): Promise<ApiResult<Offer>> => sendJson(f, "PUT", `${R}/offers/${id}`, input);
export const issueOffer = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<Offer>> =>
  sendJson(f, "POST", `${R}/offers/${id}/issue`, {});
export const supersedeOffer = (
  f: AuthFetch,
  id: string,
  input: OfferInput,
): Promise<ApiResult<Offer>> =>
  sendJson(f, "POST", `${R}/offers/${id}/supersede`, input);
export const acceptOffer = (
  f: AuthFetch,
  id: string,
  confirmNewRecord = false,
): Promise<ApiResult<Offer>> =>
  sendJson(f, "POST", `${R}/offers/${id}/accept`, { confirmNewRecord });
export const declineOffer = (
  f: AuthFetch,
  id: string,
  reason: string,
): Promise<ApiResult<Offer>> =>
  sendJson(f, "POST", `${R}/offers/${id}/decline`, { reason });

/** The printable letter as HTML text; shown in a new window from a blob so the bearer token never goes in a URL. */
export async function fetchOfferLetter(
  f: AuthFetch,
  id: string,
): Promise<ApiResult<string>> {
  try {
    const response = await f(`${R}/offers/${id}/letter`);
    if (!response.ok) {
      return {
        ok: false,
        reason: "Could not load the letter.",
        status: response.status,
      };
    }
    return { ok: true, data: await response.text() };
  } catch {
    return { ok: false, reason: "Could not load the letter." };
  }
}

export const listBatches = (f: AuthFetch): Promise<ApiResult<Batch[]>> =>
  getJson(f, `${I}/batches`, "Could not load batches.");
export const createBatch = (
  f: AuthFetch,
  input: BatchInput,
): Promise<ApiResult<Batch>> => sendJson(f, "POST", `${I}/batches`, input);
export const cancelBatch = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<Batch>> =>
  sendJson(f, "POST", `${I}/batches/${id}/cancel`, {});
export const enrolRecruit = (
  f: AuthFetch,
  batchId: string,
  teacherId: string,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${I}/batches/${batchId}/enrol`, { teacherId });
export const batchRoster = (
  f: AuthFetch,
  id: string,
): Promise<ApiResult<RosterRow[]>> =>
  getJson(f, `${I}/batches/${id}/roster`, "Could not load the roster.");
export const recordAttendance = (
  f: AuthFetch,
  enrolmentId: string,
  date: string,
  status: DayStatus,
  reason?: string,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${I}/enrolments/${enrolmentId}/attendance`, {
    date,
    status,
    reason,
  });
export const signOff = (
  f: AuthFetch,
  enrolmentId: string,
  result: "COMPLETED" | "NOT_COMPLETED",
  remarks?: string,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${I}/enrolments/${enrolmentId}/signoff`, {
    result,
    remarks,
  });
export const followUp = (
  f: AuthFetch,
  enrolmentId: string,
  action: "NEXT_BATCH" | "RELEASE",
  reason?: string,
): Promise<ApiResult<unknown>> =>
  sendJson(f, "POST", `${I}/enrolments/${enrolmentId}/follow-up`, {
    action,
    reason,
  });
export const readyToDeploy = (f: AuthFetch): Promise<ApiResult<RecruitRef[]>> =>
  getJson(
    f,
    `${I}/ready-to-deploy`,
    "Could not load the ready-to-deploy list.",
  );
export const toBeEnrolled = (f: AuthFetch): Promise<ApiResult<RecruitRef[]>> =>
  getJson(
    f,
    `${I}/to-be-enrolled`,
    "Could not load the recruits waiting for a batch.",
  );

export const getDashboard = (
  f: AuthFetch,
  params: { season?: string; from?: string; to?: string; mine?: boolean },
): Promise<ApiResult<Dashboard>> =>
  getJson(
    f,
    `${R}/dashboard?${queryString(params)}`,
    "Could not load the dashboard.",
  );

export const getSchoolContacts = (
  f: AuthFetch,
  schoolId: string,
): Promise<ApiResult<SchoolContacts>> =>
  getJson(
    f,
    `/api/v1/schools/${schoolId}/contacts`,
    "Could not load the School contacts.",
  );
export const saveSchoolContacts = (
  f: AuthFetch,
  schoolId: string,
  contacts: SchoolContacts,
): Promise<ApiResult<SchoolContacts>> =>
  sendJson(f, "PUT", `/api/v1/schools/${schoolId}/contacts`, contacts);

export const listInterviewers = (
  f: AuthFetch,
): Promise<ApiResult<PersonRef[]>> =>
  getJson(f, `${R}/interviewers`, "Could not load the interviewers.");
