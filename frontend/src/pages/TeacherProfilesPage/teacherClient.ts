// Reuses authClient's Bearer-token pattern (identity, spec 002), following
// zoneClient.ts's established request<T> helper shape.

export type TeacherStatus = "IN_TRAINING" | "ACTIVE" | "ON_LEAVE" | "EXITED";

export interface TeacherProfileView {
  id: string;
  name: string;
  phone: string;
  email?: string;
  hlsOfferedSalary: number;
  status: TeacherStatus;
  createdAt: string;
}

export interface CreateTeacherProfileRequest {
  name: string;
  phone: string;
  email?: string;
  hlsOfferedSalary: number;
  status: TeacherStatus;
}

export interface UpdateTeacherProfileRequest {
  name?: string;
  phone?: string;
  email?: string;
}

export interface TeacherSalaryHistoryView {
  id: string;
  teacherId: string;
  amount: number;
  effectiveFrom: string;
}

export type SalaryAsOfState = "RECORDED" | "NOT_YET_RECORDED";

export interface SalaryAsOfAnswer {
  state: SalaryAsOfState;
  amount?: number;
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
    if (response.status === 400) {
      const body = await response
        .json()
        .catch(() => ({ message: "Invalid request." }));
      throw new Error(body.message ?? "Invalid request.");
    }
    throw new Error(`Request failed: ${response.status}`);
  }
  return (await response.json()) as T;
}

export const teacherClient = {
  createTeacherProfile(
    accessToken: string,
    request_: CreateTeacherProfileRequest,
  ) {
    return request<TeacherProfileView>(accessToken, "/api/v1/teachers", {
      method: "POST",
      body: JSON.stringify(request_),
    });
  },

  getTeacherProfile(accessToken: string, teacherId: string) {
    return request<TeacherProfileView>(
      accessToken,
      `/api/v1/teachers/${teacherId}`,
    );
  },

  updateTeacherProfile(
    accessToken: string,
    teacherId: string,
    request_: UpdateTeacherProfileRequest,
  ) {
    return request<TeacherProfileView>(
      accessToken,
      `/api/v1/teachers/${teacherId}`,
      {
        method: "PATCH",
        body: JSON.stringify(request_),
      },
    );
  },

  changeTeacherStatus(
    accessToken: string,
    teacherId: string,
    status: TeacherStatus,
  ) {
    return request<TeacherProfileView>(
      accessToken,
      `/api/v1/teachers/${teacherId}/status`,
      {
        method: "POST",
        body: JSON.stringify({ status }),
      },
    );
  },

  getMyTeacherProfile(accessToken: string) {
    return request<TeacherProfileView>(accessToken, "/api/v1/teachers/me");
  },

  recordSalaryChange(
    accessToken: string,
    teacherId: string,
    amount: number,
    effectiveFrom?: string,
  ) {
    return request<TeacherSalaryHistoryView>(
      accessToken,
      `/api/v1/teachers/${teacherId}/salary`,
      {
        method: "POST",
        body: JSON.stringify({
          amount,
          effectiveFrom: effectiveFrom || undefined,
        }),
      },
    );
  },

  getSalary(accessToken: string, teacherId: string, asOf?: string) {
    const query = asOf ? `?asOf=${encodeURIComponent(asOf)}` : "";
    return request<SalaryAsOfAnswer>(
      accessToken,
      `/api/v1/teachers/${teacherId}/salary${query}`,
    );
  },
};
