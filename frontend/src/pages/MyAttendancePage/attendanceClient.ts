// Reuses authClient's Bearer-token pattern (identity, spec 002), following
// teacherClient.ts's established request<T> helper shape.

export type AttendanceCategory =
  "WORKED" | "LEAVE" | "TRAINING" | "NON_WORKING";
export type MarkedByRole = "TEACHER" | "MANAGER" | "ADMIN";
export type LockStatusValue = "UNLOCKED" | "LOCKED" | "REOPENED";

export interface EvidenceInput {
  geoLat?: number;
  geoLng?: number;
  photoUrl?: string;
  checkinCode?: string;
}

export interface AttendanceStatusCodeView {
  code: string;
  label: string;
  category: AttendanceCategory;
  weight: number;
  active: boolean;
}

export interface AttendanceMarkView {
  id: string;
  teacherId: string;
  markDate: string;
  schoolId: string;
  statusCode: string;
  fractionalValue: number;
  evidence?: EvidenceInput;
  markedBy: string;
  markedByRole: MarkedByRole;
  markedAt: string;
}

export interface MonthlyAttendanceRollupView {
  teacherId: string;
  period: string;
  trainingDaysTotal: number;
  trainingDaysAttended: number;
  daysWorked: number;
  daysLeave: number;
  overallWorkingDays: number;
  unmarkedDays: number;
  weightedAttendanceTotal: number;
  lockStatus: LockStatusValue;
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

export const attendanceClient = {
  listStatusCodes(accessToken: string) {
    return request<AttendanceStatusCodeView[]>(
      accessToken,
      "/api/v1/attendance/status-codes",
    );
  },

  markMyAttendance(
    accessToken: string,
    markDate: string,
    schoolId: string,
    statusCode: string,
    fractionalValue?: number,
    evidence?: EvidenceInput,
  ) {
    return request<AttendanceMarkView>(
      accessToken,
      "/api/v1/attendance/me/marks",
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

  getMyRollup(accessToken: string, teacherId: string, period: string) {
    return request<MonthlyAttendanceRollupView>(
      accessToken,
      `/api/v1/attendance/teachers/${teacherId}/months/${period}`,
    );
  },
};
