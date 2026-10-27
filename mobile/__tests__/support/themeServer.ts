import type { SavedTheme, ThemeResponse } from "../../src/api/themeApi";
import { BUILTIN_CATALOGUE, CLASSIC_PRESET, type ThemeCatalogue } from "../../src/theme/builtinClassic";
import type { FakeRequest, FakeServer } from "./fakeServer";

export const TEAL = {
  id: "teal-coral",
  name: "Teal and Coral",
  light: { ...CLASSIC_PRESET.light, primary: "#00796B", accent: "#BF360C", background: "#EAF5F3" },
  dark: { ...CLASSIC_PRESET.dark, primary: "#4DB6AC", accent: "#FF8A65", background: "#0E1A19", surface: "#16262A" },
};

export const CATALOGUE: ThemeCatalogue = { ...BUILTIN_CATALOGUE, presets: [CLASSIC_PRESET, TEAL] };

export function savedTheme(overrides: Partial<SavedTheme> = {}): SavedTheme {
  return { preset: "classic", mode: "device", customAccent: null, sidebarDark: false, changedAt: null, ...overrides };
}

export function themeResponse(overrides: Partial<SavedTheme> = {}, isDefault = false, applied?: boolean): ThemeResponse {
  return { preference: savedTheme(overrides), isDefault, applied, catalogue: CATALOGUE };
}

/** The server's stored row, kept across calls like the real last-write-wins endpoint. */
export interface ThemeRow {
  current: SavedTheme | null;
}

/** GET, PUT and DELETE of `/api/v1/me/theme` on the fake server, with last-write-wins on `changedAt`. */
export function serveTheme(server: FakeServer, row: ThemeRow = { current: null }): ThemeRow {
  server.on("GET /api/v1/me/theme", () => ({
    status: 200,
    body: row.current ? themeResponse(row.current) : themeResponse({}, true),
  }));
  server.on("PUT /api/v1/me/theme", (request: FakeRequest) => {
    const body = request.body as SavedTheme & { variant: string };
    if (body.customAccent === "#FFEB3B") {
      return {
        status: 422,
        body: { code: "ACCENT_UNREADABLE", reason: "That colour is hard to read.", suggested: "#786C00" },
      };
    }
    if (row.current?.changedAt && body.changedAt && row.current.changedAt > body.changedAt) {
      return { status: 200, body: themeResponse(row.current, false, false) };
    }
    row.current = {
      preset: body.preset,
      mode: body.mode,
      customAccent: body.customAccent,
      sidebarDark: body.sidebarDark,
      changedAt: body.changedAt,
    };
    return { status: 200, body: themeResponse(row.current, false, true) };
  });
  server.on("DELETE /api/v1/me/theme", () => {
    row.current = null;
    return { status: 204 };
  });
  return row;
}
