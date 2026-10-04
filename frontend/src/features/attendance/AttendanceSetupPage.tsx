import { Box, Typography } from "@mui/material";
import { useGrantedActions } from "../common/useGrantedActions";
import { StatusCodesPanel } from "./StatusCodesPanel";

const ROUTE = "/operations/attendance-setup";

/** Attendance setup (spec 008 US5): status codes (the calendar lives under Master Data > Holiday Calendar). */
export function AttendanceSetupPage() {
  const canEdit = useGrantedActions(ROUTE).has("EDIT");
  return (
    <Box>
      <Typography variant="h5" component="h1" sx={{ mb: 2 }}>
        Attendance Setup
      </Typography>
      <StatusCodesPanel canEdit={canEdit} />
    </Box>
  );
}
