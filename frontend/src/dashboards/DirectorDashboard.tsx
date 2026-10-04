import { Box, Typography } from "@mui/material";
import { PlaceholderWidget } from "./PlaceholderWidget";
import { PendingLeaveWidget } from "./PendingLeaveWidget";

/** Director's org-wide landing dashboard (FR-012) — placeholders until specs 005+ ship real widgets. */
export function DirectorDashboard() {
  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        Director overview
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 2,
        }}
      >
        <PlaceholderWidget title="Organization headcount" />
        <PlaceholderWidget title="Payroll status" />
        <PlaceholderWidget title="Attendance today" />
        <PendingLeaveWidget title="Open leave requests" />
      </Box>
    </Box>
  );
}
