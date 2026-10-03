import { Stack } from "@mui/material";
import { useAuth } from "../auth/useAuth";
import { AdminDashboard } from "./AdminDashboard";
import { DirectorDashboard } from "./DirectorDashboard";
import { ManagerDashboard } from "./ManagerDashboard";
import { TeacherDashboard } from "./TeacherDashboard";
import { SystemDashboard } from "./SystemDashboard";

/** Composes the union of the signed-in user's role dashboards (FR-011/FR-012, Acceptance
 * Scenario 2) — never a role-selection step. */
export function DashboardRouter() {
  const { user } = useAuth();
  const roles = user?.roles ?? [];

  return (
    <Stack spacing={4}>
      {roles.includes("ADMIN") && <AdminDashboard />}
      {roles.includes("DIRECTOR") && <DirectorDashboard />}
      {roles.includes("MANAGER") && <ManagerDashboard />}
      {roles.includes("TEACHER") && <TeacherDashboard />}
      {roles.includes("SYSTEM") && <SystemDashboard />}
    </Stack>
  );
}
