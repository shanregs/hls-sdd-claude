import { MenuItem, TextField } from "@mui/material";

/**
 * Where an audited action came from (spec 018): the client, the app version, and the device
 * location, or the reason there is none. Shown on Login History, User Activity, Audit Logs and
 * API Access for Admin and System only.
 */
export interface LocationView {
  status: string;
  latitude: number | null;
  longitude: number | null;
  accuracyMeters: number | null;
  capturedAt: string | null;
}

export interface OriginFields {
  source?: string | null;
  appVersion?: string | null;
  location?: LocationView | null;
}

const REASONS: Record<string, string> = {
  PERMISSION_DENIED: "permission denied",
  SERVICES_OFF: "location services off",
  NO_FIX: "no position found in time",
  INVALID: "invalid location sent",
  OTHER: "not provided",
};

export const SOURCE_LABELS: Record<string, string> = {
  WEB: "Web",
  ANDROID: "Android app",
};

export function sourceLabel(source: string | null | undefined): string {
  return source ? (SOURCE_LABELS[source] ?? source) : "";
}

/** "12.971599, 77.594566 (±19 m)", or "Location unavailable — <reason>", or empty for the web. */
export function formatLocation(
  location: LocationView | null | undefined,
): string {
  if (!location || location.status === "NOT_APPLICABLE") return "";
  if (
    location.status === "AVAILABLE" &&
    location.latitude != null &&
    location.longitude != null
  ) {
    const accuracy =
      location.accuracyMeters != null
        ? ` (±${Math.round(location.accuracyMeters)} m)`
        : "";
    return `${location.latitude.toFixed(6)}, ${location.longitude.toFixed(6)}${accuracy}`;
  }
  return `Location unavailable — ${REASONS[location.status] ?? location.status.toLowerCase()}`;
}

/** The Source filter shared by the audit screens: All, Web or Android app. */
export function SourceFilterField({
  value,
  onChange,
}: {
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <TextField
      select
      label="Source"
      size="small"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      sx={{ minWidth: 160 }}
    >
      <MenuItem value="">All sources</MenuItem>
      <MenuItem value="WEB">Web</MenuItem>
      <MenuItem value="ANDROID">Android app</MenuItem>
    </TextField>
  );
}
