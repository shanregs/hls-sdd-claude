import { Box, Chip, Stack, Typography } from "@mui/material";
import {
  KIND_LABELS,
  type CalendarKind,
  type CalendarSummary,
} from "./activityCalendarApi";

/** The counts above the calendar; marked partial when a source could not be read. */
export function SummaryBar({
  summary,
  partial,
}: {
  summary: CalendarSummary;
  partial: boolean;
}) {
  const total = summary.planned + summary.cancelled;
  const kinds = Object.entries(summary.byKind) as [
    CalendarKind,
    { planned: number },
  ][];
  return (
    <Box aria-label="Summary" role="region" sx={{ mb: 2 }}>
      <Stack direction="row" useFlexGap spacing={1} sx={{ flexWrap: "wrap" }}>
        <Chip label={`Planned ${summary.planned}`} />
        <Chip color="success" label={`Done ${summary.done}`} />
        <Chip label={`Done late ${summary.doneLate}`} />
        <Chip color="error" label={`Missed ${summary.missed}`} />
        <Chip label={`Cancelled ${summary.cancelled}`} />
        <Chip color="warning" label={`Rescheduled ${summary.rescheduled}`} />
        <Chip variant="outlined" label={`Total ${total}`} />
      </Stack>
      {kinds.length > 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {kinds
            .map(([kind, c]) => `${KIND_LABELS[kind]} ${c.planned}`)
            .join(" · ")}
        </Typography>
      )}
      {partial && (
        <Typography variant="body2" color="warning.main" sx={{ mt: 1 }}>
          Counts are partial: some activities could not be loaded.
        </Typography>
      )}
    </Box>
  );
}
