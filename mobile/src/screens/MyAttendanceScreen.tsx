import { getMyMonth, saveMyMark } from "../api/attendanceApi";
import { MonthPane } from "../attendance/MonthPane";
import { Screen } from "./Screen";

/** The signed-in Teacher's own month. Marks can be changed only on days the server says are theirs (`editableBy: SELF`). */
export function MyAttendanceScreen() {
  return (
    <Screen>
      <MonthPane
        kind="current"
        viewer="self"
        loader={getMyMonth}
        actions={{ editableBy: "SELF", save: saveMyMark }}
      />
    </Screen>
  );
}
