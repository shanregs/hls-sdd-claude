import { Box, Typography } from "@mui/material";
import { PlaceholderWidget } from "./PlaceholderWidget";

/** System's own dashboard (FR-012, FR-016) — technical/platform content only, never a
 * business-module widget. Placeholders until spec 011 ships real widgets. */
export function SystemDashboard() {
  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        System overview
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 2,
        }}
      >
        <PlaceholderWidget title="System health" />
        <PlaceholderWidget title="Active users" />
        <PlaceholderWidget title="Recent audit events" />
      </Box>
    </Box>
  );
}
