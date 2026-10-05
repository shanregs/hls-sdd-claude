import {
  getJson,
  queryString,
  sendJson,
  type ApiResult,
  type AuthFetch,
  type PageOf,
} from "../common/masterDataApi";

/** Spec 012 contracts/school-contracts-api.md. Amounts are strings with two decimals; dates are ISO. */

export type ContractStatus =
  "ACTIVE" | "ENDS_SOON" | "MOU_PENDING" | "NONE" | "ENDED" | "CANCELLED";

export type SalaryMode = "SAME_FOR_ALL" | "PER_TEACHER";

export interface ContractListRow {
  schoolId: string;
  schoolName: string;
  zoneManagerName: string | null;
  status: ContractStatus;
  contractId: string | null;
  startsOn: string | null;
  endsOn: string | null;
  teacherCount: number | null;
  filled: number;
  vacant: number;
  /** Teachers at the School not mapped to a position of the contract in effect. */
  unmapped: number;
  salaryMode: SalaryMode | null;
  signedOn: string | null;
}

export interface PositionRow {
  id: string;
  number: number;
  title: string | null;
  salary: string;
  /** The Teacher mapped to this position today, if any. */
  teacherId: string | null;
  teacherName: string | null;
}

export interface SignatoryRow {
  id: string;
  party: "SCHOOL" | "HLS";
  name: string;
  designation: string;
  userId: string | null;
}

export interface ContractRow {
  id: string;
  schoolId: string;
  state: "RATE_PENDING" | "ACTIVE" | "CANCELLED";
  status: ContractStatus;
  salaryMode: SalaryMode | null;
  teacherCount: number | null;
  rate: string | null;
  signedOn: string | null;
  startsOn: string;
  endsOn: string | null;
  version: number;
  positions: PositionRow[];
  signatories: SignatoryRow[];
}

export interface UnmappedTeacher {
  teacherId: string;
  teacherName: string;
}

export interface SchoolContracts {
  schoolId: string;
  schoolName: string;
  zoneManagerName: string | null;
  contracts: ContractRow[];
  unmappedTeachers: UnmappedTeacher[];
}

export interface SignatoryCandidate {
  userId: string;
  name: string;
  designation: "Zone Manager" | "Director";
}

export interface SchoolSignatoryInput {
  name: string;
  designation: string;
}

export interface HlsSignatoryInput {
  userId: string;
  designation: string;
}

export interface MouInput {
  teacherCount: number;
  salaryMode: SalaryMode;
  /** The one salary for SAME_FOR_ALL. */
  rate?: string;
  /** One entry per position for PER_TEACHER. */
  positions?: { title?: string; salary: string }[];
  signedOn: string;
  schoolSignatories: SchoolSignatoryInput[];
  hlsSignatories: HlsSignatoryInput[];
}

export interface NewContractInput extends MouInput {
  startsOn: string;
  endsOn?: string | null;
}

export interface RemapEntry {
  teacherId: string;
  positionId: string;
}

const BASE = "/api/v1/school-contracts";

export function listContracts(
  authFetch: AuthFetch,
  params: {
    status?: ContractStatus;
    managerId?: string;
    page: number;
    size: number;
  },
): Promise<ApiResult<PageOf<ContractListRow>>> {
  const query = queryString({
    status: params.status,
    managerId: params.managerId,
    page: params.page,
    size: params.size,
  });
  return getJson(authFetch, `${BASE}?${query}`, "Could not load contracts.");
}

export function getSchoolContracts(
  authFetch: AuthFetch,
  schoolId: string,
): Promise<ApiResult<SchoolContracts>> {
  return getJson(
    authFetch,
    `${BASE}/schools/${schoolId}`,
    "Could not load this School's contracts.",
  );
}

export function createContract(
  authFetch: AuthFetch,
  schoolId: string,
  input: NewContractInput,
): Promise<ApiResult<ContractRow>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/schools/${schoolId}/contracts`,
    input,
  );
}

export function recordMou(
  authFetch: AuthFetch,
  contractId: string,
  input: MouInput,
): Promise<ApiResult<ContractRow>> {
  return sendJson(
    authFetch,
    "PUT",
    `${BASE}/contracts/${contractId}/mou`,
    input,
  );
}

export function endContract(
  authFetch: AuthFetch,
  contractId: string,
  endsOn: string,
): Promise<ApiResult<ContractRow>> {
  return sendJson(authFetch, "POST", `${BASE}/contracts/${contractId}/end`, {
    endsOn,
  });
}

export function cancelContract(
  authFetch: AuthFetch,
  contractId: string,
): Promise<ApiResult<ContractRow>> {
  return sendJson(authFetch, "POST", `${BASE}/contracts/${contractId}/cancel`);
}

export function getSignatoryCandidates(
  authFetch: AuthFetch,
  schoolId: string,
): Promise<ApiResult<SignatoryCandidate[]>> {
  return getJson(
    authFetch,
    `${BASE}/signatory-candidates?schoolId=${schoolId}`,
    "Could not load the HLS signatories.",
  );
}

export function mapTeachersToContract(
  authFetch: AuthFetch,
  contractId: string,
  entries: RemapEntry[],
): Promise<ApiResult<{ remapped: number }>> {
  return sendJson(
    authFetch,
    "POST",
    `${BASE}/contracts/${contractId}/map-teachers`,
    entries,
  );
}
