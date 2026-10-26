import type { Approval, MouAction } from "./mouApprovalApi";
import type { ContractRow } from "./schoolContractsApi";

/** Fixtures shared by the spec 031 component tests. */
export const CONTRACT: ContractRow = {
  id: "c1",
  schoolId: "s1",
  state: "ACTIVE",
  status: "ACTIVE",
  salaryMode: "SAME_FOR_ALL",
  teacherCount: 2,
  rate: "15000.00",
  signedOn: "2026-09-20",
  startsOn: "2026-10-01",
  endsOn: null,
  version: 0,
  positions: [
    {
      id: "p1",
      number: 1,
      title: null,
      salary: "15000.00",
      teacherId: null,
      teacherName: null,
    },
    {
      id: "p2",
      number: 2,
      title: null,
      salary: "15000.00",
      teacherId: null,
      teacherName: null,
    },
  ],
  signatories: [],
};

const PERSON = { id: "u1", name: "Divya Director" };

export function approval(
  actions: MouAction[],
  overrides: Partial<Approval> = {},
): Approval {
  return {
    contractId: "c1",
    state: "DRAFT",
    round: 0,
    submittedBy: null,
    submittedAt: null,
    decidedBy: null,
    decidedAt: null,
    decisionReason: null,
    selfDecided: false,
    document: { state: "NONE", current: null },
    version: 3,
    actions,
    documents: [],
    history: [],
    ...overrides,
  };
}

export const WITH_DOCUMENTS: Partial<Approval> = {
  document: { state: "ATTACHED", current: null },
  documents: [
    {
      id: "d2",
      versionNo: 2,
      name: "mou-corrected.pdf",
      contentType: "application/pdf",
      sizeBytes: 482113,
      addedBy: PERSON,
      addedAt: "2026-10-02T05:30:00Z",
      current: true,
      removed: null,
    },
    {
      id: "d1",
      versionNo: 1,
      name: "mou-signed.pdf",
      contentType: "application/pdf",
      sizeBytes: 1024,
      addedBy: PERSON,
      addedAt: "2026-10-01T05:30:00Z",
      current: false,
      removed: {
        by: PERSON,
        at: "2026-10-02T05:35:00Z",
        reason: "wrong file",
      },
    },
  ],
};

export function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}
