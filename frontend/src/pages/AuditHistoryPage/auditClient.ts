// research.md §7: reuses authClient's Bearer-token pattern (identity, spec 002) —
// no changes needed to Identity's/Organization's frontend code, just a new
// client for this page.

export type AuditAction = "CREATED" | "UPDATED" | "CORRECTED";

export interface AuditEntryView {
  id: string;
  sequenceNo: number;
  sourceModule: string;
  entityType: string;
  entityId: string;
  action: AuditAction;
  summary: string;
  beforeValue: string | null;
  afterValue: string | null;
  actorUserId: string;
  actorRole: string | null;
  occurredAt: string;
  requestId: string | null;
}

async function request<T>(accessToken: string, path: string): Promise<T> {
  const response = await fetch(path, {
    credentials: "include",
    headers: {
      Authorization: `Bearer ${accessToken}`,
    },
  });
  if (!response.ok) {
    if (response.status === 403) {
      throw new Error("Only a Director or Admin may view audit history.");
    }
    throw new Error(`Request failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

export const auditClient = {
  getHistory(accessToken: string, entityType: string, entityId: string) {
    return request<AuditEntryView[]>(
      accessToken,
      `/api/v1/audit/${encodeURIComponent(entityType)}/${encodeURIComponent(entityId)}/history`,
    );
  },
};
