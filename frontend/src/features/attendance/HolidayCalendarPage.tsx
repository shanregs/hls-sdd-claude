import { Box, Typography } from "@mui/material";
import { useGrantedActions } from "../common/useGrantedActions";
import { CalendarPanel } from "./CalendarPanel";

const ROUTE = "/master-data/holiday-calendar";

/** Holiday Calendar (spec 008 FR-008): weekly off days, School overrides and non-working dates. */
export function HolidayCalendarPage() {
  const canEdit = useGrantedActions(ROUTE).has("EDIT");
  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Holiday Calendar
      </Typography>
      <CalendarPanel canEdit={canEdit} />
    </Box>
  );
}
