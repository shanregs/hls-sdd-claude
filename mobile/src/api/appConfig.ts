import {
  DEFAULT_LOCATION_REUSE_SECONDS,
  DEFAULT_LOCATION_WAIT_SECONDS,
} from "../config/constants";
import { api } from "./httpClient";

export interface AppConfig {
  minimumVersion: string;
  locationWaitSeconds: number;
  locationReuseSeconds: number;
}

export const DEFAULT_APP_CONFIG: AppConfig = {
  minimumVersion: "0.0.0",
  locationWaitSeconds: DEFAULT_LOCATION_WAIT_SECONDS,
  locationReuseSeconds: DEFAULT_LOCATION_REUSE_SECONDS,
};

/**
 * Public, unauthenticated app configuration (spec 018 FR-030). Falls back to safe defaults when the
 * server cannot be reached, so the app can still show its sign-in or no-connection screen.
 */
export async function fetchAppConfig(): Promise<AppConfig> {
  try {
    const config = await api.get<Partial<AppConfig>>("/api/v1/mobile/app-config", { auth: false });
    return {
      minimumVersion: config.minimumVersion ?? DEFAULT_APP_CONFIG.minimumVersion,
      locationWaitSeconds: positiveOr(config.locationWaitSeconds, DEFAULT_APP_CONFIG.locationWaitSeconds),
      locationReuseSeconds: positiveOr(config.locationReuseSeconds, DEFAULT_APP_CONFIG.locationReuseSeconds),
    };
  } catch {
    return DEFAULT_APP_CONFIG;
  }
}

function positiveOr(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) && value > 0 ? value : fallback;
}
