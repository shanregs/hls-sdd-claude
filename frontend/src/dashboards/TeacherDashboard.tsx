import { Box, Typography } from "@mui/material";
import { PlaceholderWidget } from "./PlaceholderWidget";

/** Teacher's self-service landing dashboard (FR-012) — own data only, placeholders until specs
 * 008+ ship real widgets. */
export function TeacherDashboard() {
  return (
    <Box>
      <Typography variant="h6" component="h2" gutterBottom>
        My overview
      </Typography>
      <Box
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))",
          gap: 2,
        }}
      >
        <PlaceholderWidget title="My attendance this month" />
        <PlaceholderWidget title="My leave balance" />
        <PlaceholderWidget title="My notifications" />
      </Box>
    </Box>
  );
}
