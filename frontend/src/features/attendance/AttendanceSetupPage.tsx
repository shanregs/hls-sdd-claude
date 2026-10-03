import { Box, Stack, Typography } from "@mui/material";
import { useGrantedActions } from "../common/useGrantedActions";
import { CalendarPanel } from "./CalendarPanel";
import { StatusCodesPanel } from "./StatusCodesPanel";

const ROUTE = "/operations/attendance-setup";

/** Attendance setup (spec 008 US5): status codes and the non-working calendar. */
export function AttendanceSetupPage() {
  const canEdit = useGrantedActions(ROUTE).has("EDIT");
  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Attendance Setup
      </Typography>
      <Stack spacing={4}>
        <StatusCodesPanel canEdit={canEdit} />
        <CalendarPanel canEdit={canEdit} />
      </Stack>
    </Box>
  );
}
