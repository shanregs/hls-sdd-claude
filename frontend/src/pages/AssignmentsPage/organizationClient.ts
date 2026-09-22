// research.md §8: reuses authClient's Bearer-token pattern (identity, spec 002) —
// no changes needed to Identity's frontend code, just a new client for this page.

export interface CurrentAssignment {
  id: string;
  managerId: string;
  effectiveFrom: string;
}

export interface AccountabilityAnswer {
  state: "CURRENT_MANAGER" | "UNASSIGNED" | "UNKNOWN_IDENTIFIER";
  managerId?: string;
}

export interface AssignmentHistoryEntry {
  id: string;
  managerId: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  assignedBy: string;
  assignedAt: string;
}

export interface PortfolioItem {
  itemType: "SCHOOL" | "TEACHER";
  itemId: string;
  since: string;
}

export interface UnassignedItem {
  itemType: "SCHOOL" | "TEACHER";
  itemId: string;
  lastEndedAt: string | null;
}

export interface ZoneManagerAssignmentView {
  id: string;
  zoneId: string;
  managerId: string;
  effectiveFrom: string;
}

export interface ZoneCoverage {
  zoneId: string;
  managerIds: string[];
  schoolIds: string[];
}

async function request<T>(
  accessToken: string,
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${accessToken}`,
      ...(init.headers ?? {}),
    },
  });
  if (!response.ok) {
    if (response.status === 409) {
      const body = await response
        .json()
        .catch(() => ({ message: "Conflict." }));
      throw new Error(
        body.message ?? "This assignment was already changed by someone else.",
      );
    }
    if (response.status === 422) {
      const body = await response
        .json()
        .catch(() => ({ message: "This request cannot succeed as written." }));
      throw new Error(
        body.message ?? "This request cannot succeed as written.",
      );
    }
    if (response.status === 404) {
      const body = await response
        .json()
        .catch(() => ({ message: "Not found." }));
      throw new Error(body.message ?? "Not found.");
    }
    throw new Error(`Request failed: ${response.status}`);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

export const organizationClient = {
  assignSchoolManager(
    accessToken: string,
    schoolId: string,
    managerId: string,
    endsAssignmentId?: string,
  ) {
    return request<CurrentAssignment>(
      accessToken,
      "/api/v1/organization/school-assignments",
      {
        method: "POST",
        body: JSON.stringify({ schoolId, managerId, endsAssignmentId }),
      },
    );
  },

  assignTeacherManager(
    accessToken: string,
    teacherId: string,
    managerId: string,
    endsAssignmentId?: string,
  ) {
    return request<CurrentAssignment>(
      accessToken,
      "/api/v1/organization/teacher-assignments",
      {
        method: "POST",
        body: JSON.stringify({ teacherId, managerId, endsAssignmentId }),
      },
    );
  },

  getSchoolAccountableManager(accessToken: string, schoolId: string) {
    return request<AccountabilityAnswer>(
      accessToken,
      `/api/v1/organization/schools/${schoolId}/accountable-manager`,
    );
  },

  getTeacherAccountableManager(accessToken: string, teacherId: string) {
    return request<AccountabilityAnswer>(
      accessToken,
      `/api/v1/organization/teachers/${teacherId}/accountable-manager`,
    );
  },

  getSchoolAssignmentHistory(accessToken: string, schoolId: string) {
    return request<AssignmentHistoryEntry[]>(
      accessToken,
      `/api/v1/organization/schools/${schoolId}/assignment-history`,
    );
  },

  getTeacherAssignmentHistory(accessToken: string, teacherId: string) {
    return request<AssignmentHistoryEntry[]>(
      accessToken,
      `/api/v1/organization/teachers/${teacherId}/assignment-history`,
    );
  },

  getManagerPortfolio(accessToken: string, managerId: string) {
    return request<PortfolioItem[]>(
      accessToken,
      `/api/v1/organization/managers/${managerId}/portfolio`,
    );
  },

  listUnassigned(accessToken: string, itemType?: "SCHOOL" | "TEACHER") {
    const query = itemType ? `?itemType=${itemType}` : "";
    return request<UnassignedItem[]>(
      accessToken,
      `/api/v1/organization/unassigned${query}`,
    );
  },

  assignManagerToZone(accessToken: string, zoneId: string, managerId: string) {
    return request<ZoneManagerAssignmentView>(
      accessToken,
      "/api/v1/organization/zone-manager-assignments",
      {
        method: "POST",
        body: JSON.stringify({ zoneId, managerId }),
      },
    );
  },

  removeManagerFromZone(accessToken: string, assignmentId: string) {
    return request<void>(
      accessToken,
      `/api/v1/organization/zone-manager-assignments/${assignmentId}`,
      {
        method: "DELETE",
      },
    );
  },

  getZoneCoverage(accessToken: string, zoneId: string) {
    return request<ZoneCoverage>(
      accessToken,
      `/api/v1/organization/zones/${zoneId}/coverage`,
    );
  },
};
