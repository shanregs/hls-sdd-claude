import type { AuthFetch } from "../common/masterDataApi";

/** System and security settings and the System dashboard (spec 011, contracts/settings-api.md). */
export type SettingsGroup = "system" | "security";

export interface SettingView {
  key: string;
  label: string;
  unit: string;
  type: "INTEGER" | "VERSION";
  min: number;
  max: number;
  default: string;
  value: string;
  isDefault: boolean;
  updatedAt: string | null;
  updatedBy: string | null;
}

export interface GroupResponse {
  group: SettingsGroup;
  settings: SettingView[];
}

export type SettingsResult<T> =
  | { ok: true; data: T }
  | { ok: false; reason: string; fields: Record<string, string> };

const LOAD_FAILURE = "Could not load the settings.";
const SAVE_FAILURE = "Could not save the settings. Please try again.";

export async function getSettings(
  authFetch: AuthFetch,
  group: SettingsGroup,
): Promise<SettingsResult<GroupResponse>> {
  try {
    const response = await authFetch(`/api/v1/settings/${group}`);
    if (!response.ok) {
      return { ok: false, reason: LOAD_FAILURE, fields: {} };
    }
    return { ok: true, data: (await response.json()) as GroupResponse };
  } catch {
    return { ok: false, reason: LOAD_FAILURE, fields: {} };
  }
}

export async function saveSettings(
  authFetch: AuthFetch,
  group: SettingsGroup,
  values: Record<string, string>,
): Promise<SettingsResult<GroupResponse>> {
  try {
    const response = await authFetch(`/api/v1/settings/${group}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ values }),
    });
    if (response.ok) {
      return { ok: true, data: (await response.json()) as GroupResponse };
    }
    if (response.status === 422) {
      const body = (await response.json()) as {
        message?: string;
        fields?: Record<string, string>;
      };
      return {
        ok: false,
        reason: body.message ?? SAVE_FAILURE,
        fields: body.fields ?? {},
      };
    }
    return { ok: false, reason: SAVE_FAILURE, fields: {} };
  } catch {
    return { ok: false, reason: SAVE_FAILURE, fields: {} };
  }
}

/** The problem with a value, or null when it is acceptable. The server stays the authority. */
export function problemWith(setting: SettingView, raw: string): string | null {
  const text = raw.trim();
  if (setting.type === "VERSION") {
    return /^\d{1,6}\.\d{1,6}\.\d{1,6}$/.test(text)
      ? null
      : `${setting.label} must look like 1.2.0.`;
  }
  if (!/^\d{1,9}$/.test(text)) {
    return `${setting.label} must be a whole number from ${setting.min} to ${setting.max} ${setting.unit}.`;
  }
  const n = Number(text);
  return n >= setting.min && n <= setting.max
    ? null
    : `${setting.label} must be a whole number from ${setting.min} to ${setting.max} ${setting.unit}.`;
}

export interface Dashboard {
  health: {
    database: string;
    applicationVersion: string;
    migrationVersion: string | null;
    startedAt: string;
    uptimeSeconds: number;
    serverTime: string;
  };
  users: {
    total: number;
    active: number;
    inactive: number;
    lockedNow: number;
    byRole: Record<string, number>;
  };
  failedSignInsLast24h: number;
  recentAudit: {
    status: string;
    events: {
      kind: string;
      at: string;
      actorName: string | null;
      summary: string;
    }[];
  };
}

export async function getDashboard(
  authFetch: AuthFetch,
): Promise<{ ok: true; data: Dashboard } | { ok: false; reason: string }> {
  try {
    const response = await authFetch("/api/v1/system/dashboard");
    if (!response.ok) {
      return { ok: false, reason: "Could not load the dashboard." };
    }
    const data = (await response.json()) as Dashboard | null;
    if (!data?.health || !data.users || !data.recentAudit) {
      return { ok: false, reason: "Could not load the dashboard." };
    }
    return { ok: true, data };
  } catch {
    return { ok: false, reason: "Could not load the dashboard." };
  }
}
