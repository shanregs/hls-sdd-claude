import { Stack } from "@mui/material";
import { ManagementDashboard } from "../features/dashboard/ManagementDashboard";
import { PendingLeaveWidget } from "./PendingLeaveWidget";

/**
 * Admin's org-wide landing dashboard (spec 028): the areas and tiles come from the server's manifest, so the Admin and
 * Director see the same screen and differ only by the permission matrix. The pending-leave widget stays until the leave
 * tile (T038, US4) carries the same figure; it is then removed.
 */
export function AdminDashboard() {
  return (
    <Stack spacing={3}>
      <ManagementDashboard title="Admin overview" />
      <PendingLeaveWidget title="Pending leave approvals" />
    </Stack>
  );
}
