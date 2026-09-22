// Reuses authClient's Bearer-token pattern (identity, spec 002), following
// teacherClient.ts's/zoneClient.ts's established request<T> helper shape.
// Shares its view-model types with MyAttendancePage/attendanceClient.ts.

import type {
  AttendanceCategory,
  AttendanceMarkView,
  AttendanceStatusCodeView,
  EvidenceInput,
  LockStatusValue,
  MonthlyAttendanceRollupView,
} from "../MyAttendancePage/attendanceClient";

export interface GridCell {
  statusCode: string | null;
  category: AttendanceCategory | null;
  fractionalValue: number | null;
  schoolId: string | null;
  editable: boolean;
}

export interface AttendanceGridRow {
  teacherId: string;
  teacherName: string;
  cells: Record<string, GridCell>;
}

export interface AttendanceGridView {
  period: string;
  days: string[];
  rows: AttendanceGridRow[];
}

export interface ReopenEntry {
  reason: string;
  reopenedAt: string;
  reopenedBy: string;
  relockedAt?: string;
  relockedBy?: string;
}

export interface LockStatusView {
  teacherId: string;
  period: string;
  status: LockStatusValue;
  lockedAt?: string;
  lockedBy?: string;
  reopenHistory: ReopenEntry[];
}

export interface NonWorkingDateView {
  id: string;
  date: string;
  label: string;
  active: boolean;
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
    if (response.status === 403) {
      throw new Error("You are not allowed to do that.");
    }
    if (response.status === 404) {
      throw new Error("Not found.");
    }
    if (response.status === 400 || response.status === 409) {
      const body = await response
        .json()
        .catch(() => ({ message: "Request could not be completed." }));
      throw new Error(body.message ?? "Request could not be completed.");
    }
    throw new Error(`Request failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

export const attendanceAdminClient = {
  markOnBehalf(
    accessToken: string,
    teacherId: string,
    markDate: string,
    schoolId: string,
    statusCode: string,
    fractionalValue?: number,
    evidence?: EvidenceInput,
  ) {
    return request<AttendanceMarkView>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/marks`,
      {
        method: "POST",
        body: JSON.stringify({
          markDate,
          schoolId,
          statusCode,
          fractionalValue,
          evidence,
        }),
      },
    );
  },

  getRollup(accessToken: string, teacherId: string, period: string) {
    return request<MonthlyAttendanceRollupView>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/months/${period}`,
    );
  },

  listMarks(accessToken: string, teacherId: string, period: string) {
    return request<AttendanceMarkView[]>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/months/${period}/marks`,
    );
  },

  getLockStatus(accessToken: string, teacherId: string, period: string) {
    return request<LockStatusView>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/months/${period}/lock`,
    );
  },

  lockMonth(accessToken: string, teacherId: string, period: string) {
    return request<LockStatusView>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/months/${period}/lock`,
      { method: "POST" },
    );
  },

  reopenMonth(
    accessToken: string,
    teacherId: string,
    period: string,
    reason: string,
  ) {
    return request<LockStatusView>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/months/${period}/reopen`,
      { method: "POST", body: JSON.stringify({ reason }) },
    );
  },

  createStatusCode(
    accessToken: string,
    code: string,
    label: string,
    category: AttendanceCategory,
    weight: number,
  ) {
    return request<AttendanceStatusCodeView>(
      accessToken,
      "/api/v1/attendance/status-codes",
      {
        method: "POST",
        body: JSON.stringify({ code, label, category, weight }),
      },
    );
  },

  getAttendanceGrid(accessToken: string, period: string, managerId?: string) {
    const query = managerId
      ? `&managerId=${encodeURIComponent(managerId)}`
      : "";
    return request<AttendanceGridView>(
      accessToken,
      `/api/v1/attendance/grid?period=${encodeURIComponent(period)}${query}`,
    );
  },

  listNonWorkingDates(accessToken: string, period: string) {
    return request<NonWorkingDateView[]>(
      accessToken,
      `/api/v1/attendance/non-working-dates?period=${encodeURIComponent(period)}`,
    );
  },

  addNonWorkingDate(accessToken: string, date: string, label: string) {
    return request<NonWorkingDateView>(
      accessToken,
      "/api/v1/attendance/non-working-dates",
      {
        method: "POST",
        body: JSON.stringify({ date, label }),
      },
    );
  },

  deactivateNonWorkingDate(accessToken: string, id: string) {
    return request<NonWorkingDateView>(
      accessToken,
      `/api/v1/attendance/non-working-dates/${id}/deactivate`,
      { method: "POST" },
    );
  },
};
