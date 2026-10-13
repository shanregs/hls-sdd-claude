import { Stack, Typography } from "@mui/material";
import { dateRange } from "./statusDisplay";
import type { PendingRequest } from "./managerAttendanceApi";

const KINDS: Record<string, string> = {
  LEAVE: "Leave",
  FORGOTTEN_CHECK_IN: "Forgotten check-in",
};

/** The requests that are still waiting for a decision and so stop a month being locked (spec 032 US6). */
export function PendingRequests({
  items,
  showManager = true,
}: {
  items: PendingRequest[];
  showManager?: boolean;
}) {
  if (items.length === 0) return null;
  return (
    <Stack
      component="ul"
      spacing={0.5}
      sx={{ m: 0, pl: 2 }}
      aria-label="Waiting requests"
    >
      {items.map((p) => (
        <Typography
          key={`${p.kind}-${p.requestId}`}
          component="li"
          variant="body2"
        >
          {KINDS[p.kind] ?? p.kind}
          {showManager && p.managerName ? ` of ${p.managerName}` : ""},{" "}
          {dateRange(p.firstDate, p.lastDate)}
        </Typography>
      ))}
    </Stack>
  );
}
