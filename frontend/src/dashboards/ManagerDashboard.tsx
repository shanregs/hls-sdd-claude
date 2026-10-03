import { Box, Typography } from "@mui/material";
import { PlaceholderWidget } from "./PlaceholderWidget";

/** Manager's assigned-scope landing dashboard (FR-012, Acceptance Scenario 3) — never org-wide
 * content, placeholders until specs 006+ ship real widgets. */
export function ManagerDashboard() {
  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        My assigned overview
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 2,
        }}
      >
        <PlaceholderWidget title="My assigned schools" />
        <PlaceholderWidget title="My assigned teachers" />
        <PlaceholderWidget title="Pending leave approvals" />
        <PlaceholderWidget title="This month's attendance" />
      </Box>
    </Box>
  );
}
