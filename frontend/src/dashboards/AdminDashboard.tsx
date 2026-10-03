import { Box, Typography } from "@mui/material";
import { PlaceholderWidget } from "./PlaceholderWidget";

/** Admin's org-wide landing dashboard (FR-012) — placeholders until specs 005+ ship real widgets. */
export function AdminDashboard() {
  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        Admin overview
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 2,
        }}
      >
        <PlaceholderWidget title="Organization headcount" />
        <PlaceholderWidget title="Attendance today" />
        <PlaceholderWidget title="Pending approvals" />
        <PlaceholderWidget title="Recent audit activity" />
      </Box>
    </Box>
  );
}
