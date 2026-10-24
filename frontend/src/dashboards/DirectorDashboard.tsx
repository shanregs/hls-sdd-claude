import { Stack } from "@mui/material";
import { ManagementDashboard } from "../features/dashboard/ManagementDashboard";
import { PendingLeaveWidget } from "./PendingLeaveWidget";

/** Director's org-wide landing dashboard (spec 028): the same management Dashboard as the Admin's. */
export function DirectorDashboard() {
  return (
    <Stack spacing={3}>
      <ManagementDashboard title="Director overview" />
      <PendingLeaveWidget title="Open leave requests" />
    </Stack>
  );
}
