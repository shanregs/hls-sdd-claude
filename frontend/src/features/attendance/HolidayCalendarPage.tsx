import { useState } from "react";
import { Box, Stack, Typography } from "@mui/material";
import { useGrantedActions } from "../common/useGrantedActions";
import type { AttendanceCalendar } from "./attendanceApi";
import { CalendarPanel } from "./CalendarPanel";
import { HolidayCalendarView } from "./HolidayCalendarView";

const ROUTE = "/master-data/holiday-calendar";

/**
 * Holiday Calendar (spec 008 US9): everyone sees the year or month with holidays highlighted and can
 * download it as a PDF; Admin and Director also edit weekly off days and holidays below it.
 */
export function HolidayCalendarPage() {
  const canEdit = useGrantedActions(ROUTE).has("EDIT");
  const [calendar, setCalendar] = useState<AttendanceCalendar | null>(null);
  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Holiday Calendar
      </Typography>
      <Stack spacing={4}>
        {calendar && <HolidayCalendarView calendar={calendar} />}
        <CalendarPanel canEdit={canEdit} onChange={setCalendar} />
      </Stack>
    </Box>
  );
}
