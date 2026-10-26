import { Box, Divider } from "@mui/material";
import { HistoryPage } from "./HistoryPage";
import { TrainingCalendarPage } from "./TrainingCalendarPage";

/** OPERATIONS -> My Training (Manager): the sessions that include them or their Teachers, exceptions, and their own history. */
export function MyTrainingPage() {
  return (
    <Box>
      <TrainingCalendarPage />
      <Divider sx={{ my: 3 }} />
      <HistoryPage mine embedded />
    </Box>
  );
}
