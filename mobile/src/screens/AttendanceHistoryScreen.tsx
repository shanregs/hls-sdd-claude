import { getMyMonth } from "../api/attendanceApi";
import { MonthPane } from "../attendance/MonthPane";
import { Screen } from "./Screen";

/** Earlier months of the Teacher's own attendance, read-only: no day can be changed from here. */
export function AttendanceHistoryScreen() {
  return (
    <Screen>
      <MonthPane kind="history" viewer="self" loader={getMyMonth} />
    </Screen>
  );
}
