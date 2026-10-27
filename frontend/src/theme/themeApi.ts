import {
  GENERIC_FAILURE,
  type AuthFetch,
} from "../features/common/masterDataApi";
import type { ThemeCatalogue } from "./tokens";

export type PreferredMode = "light" | "dark" | "device";

export interface ThemePreference {
  preset: string;
  mode: PreferredMode;
  customAccent: string | null;
  sidebarDark: boolean;
  /** ISO instant of the last change, used for last-write-wins; null for a user who never chose. */
  changedAt: string | null;
}

export const DEFAULT_PREFERENCE: ThemePreference = {
  preset: "classic",
  mode: "device",
  customAccent: null,
  sidebarDark: false,
  changedAt: null,
};

export interface ThemeResponse {
  preference: ThemePreference;
  isDefault: boolean;
  /** False when the stored row was newer and nothing was written. */
  applied?: boolean;
  catalogue: ThemeCatalogue;
}

export interface ContrastFailure {
  variant: "light" | "dark";
  pair: string;
  ratio: number;
  required: number;
}

export interface VariantVerdict {
  variant: "light" | "dark";
  pass: boolean;
  ratios: {
    accentOnSurface: number;
    accentOnBackground: number;
    headerTextOnAccent: number;
  };
  failures: ContrastFailure[];
  suggested: string | null;
}

export interface ContrastCheck {
  accent: string;
  light: VariantVerdict;
  dark: VariantVerdict;
}

export type ThemeSaveResult =
  | { ok: true; data: ThemeResponse }
  | {
      ok: false;
      code: string;
      reason: string;
      /** For ACCENT_UNREADABLE: the colour the server offers instead. */
      suggested: string | null;
    };

const BASE = "/api/v1/me/theme";

export async function getTheme(
  authFetch: AuthFetch,
): Promise<ThemeResponse | null> {
  try {
    const response = await authFetch(BASE);
    if (!response.ok) return null;
    return (await response.json()) as ThemeResponse;
  } catch {
    return null;
  }
}

export interface ThemeSaveBody extends Omit<ThemePreference, "changedAt"> {
  changedAt: string;
  /** The variant the accent is checked in; the other variant is re-derived on each client. */
  variant: "light" | "dark";
}

export async function putTheme(
  authFetch: AuthFetch,
  body: ThemeSaveBody,
): Promise<ThemeSaveResult> {
  try {
    const response = await authFetch(BASE, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (response.ok) {
      return { ok: true, data: (await response.json()) as ThemeResponse };
    }
    let code = "FAILED";
    let reason = GENERIC_FAILURE;
    let suggested: string | null = null;
    try {
      const failure = (await response.json()) as {
        code?: string;
        reason?: string;
        message?: string;
        suggested?: string | null;
      };
      code = failure.code ?? code;
      reason = failure.reason ?? failure.message ?? reason;
      suggested = failure.suggested ?? null;
    } catch {
      // keep the generic failure
    }
    return { ok: false, code, reason, suggested };
  } catch {
    return {
      ok: false,
      code: "NETWORK",
      reason: GENERIC_FAILURE,
      suggested: null,
    };
  }
}

export async function deleteTheme(authFetch: AuthFetch): Promise<boolean> {
  try {
    const response = await authFetch(BASE, { method: "DELETE" });
    return response.ok;
  } catch {
    return false;
  }
}

export async function checkContrast(
  authFetch: AuthFetch,
  accent: string,
  preset: string,
): Promise<ContrastCheck | null> {
  try {
    const response = await authFetch(`${BASE}/contrast-check`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ accent, preset }),
    });
    if (!response.ok) return null;
    return (await response.json()) as ContrastCheck;
  } catch {
    return null;
  }
}
