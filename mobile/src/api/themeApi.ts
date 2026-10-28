import type { ThemeCatalogue, ThemeMode } from "../theme/builtinClassic";
import { api } from "./httpClient";

/** The wire value for "follow the device" is `device` (the phone's own wording is "system"). */
export type WireMode = "light" | "dark" | "device";

export interface SavedTheme {
  preset: string;
  mode: WireMode;
  customAccent: string | null;
  sidebarDark: boolean;
  /** ISO instant of the last change, used for last-write-wins; null for a user who never chose. */
  changedAt: string | null;
}

export const DEFAULT_PREFERENCE: SavedTheme = {
  preset: "classic",
  mode: "device",
  customAccent: null,
  sidebarDark: false,
  changedAt: null,
};

export interface ThemeResponse {
  preference: SavedTheme;
  isDefault: boolean;
  /** False when the stored row was newer and nothing was written. */
  applied?: boolean;
  catalogue: ThemeCatalogue;
}

export interface ThemeSaveBody extends Omit<SavedTheme, "changedAt"> {
  changedAt: string;
  /** The variant the accent is checked in; the other variant is re-derived on the phone. */
  variant: ThemeMode;
}

export const getTheme = () => api.get<ThemeResponse>("/api/v1/me/theme");

export const putTheme = (body: ThemeSaveBody) => api.put<ThemeResponse>("/api/v1/me/theme", body);

export const deleteTheme = () => api.delete<void>("/api/v1/me/theme");
