import { Box, Paper, Typography } from "@mui/material";
import type { RollupView } from "./attendanceApi";

/** The monthly rollup (spec 008 FR-010), live while the month is open and frozen once locked. */
export function RollupSummary({ rollup }: { rollup: RollupView }) {
  const items: [string, number][] = [
    ["Working days", rollup.workingDays],
    ["Days worked", rollup.daysWorked],
    ["Days of leave", rollup.daysLeave],
    ["Training days available", rollup.trainingAvailable],
    ["Training days attended", rollup.trainingAttended],
    ["Unmarked days", rollup.unmarked],
    ["Weighted total", rollup.weightedTotal],
  ];
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle1" component="h2" sx={{ mb: 1 }}>
        Monthly rollup{rollup.frozen ? " (locked)" : ""}
      </Typography>
      <Box
        component="dl"
        sx={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))",
          gap: 1.5,
          m: 0,
        }}
      >
        {items.map(([label, value]) => (
          <Box key={label}>
            <Typography component="dt" variant="body2" color="text.secondary">
              {label}
            </Typography>
            <Typography component="dd" variant="h6" sx={{ m: 0 }}>
              {value}
            </Typography>
          </Box>
        ))}
      </Box>
    </Paper>
  );
}
