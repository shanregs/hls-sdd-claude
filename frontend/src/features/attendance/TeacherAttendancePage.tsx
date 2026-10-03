import { AttendanceGridScreen } from "./AttendanceGridScreen";
import { getTeacherGrid } from "./attendanceApi";
import { useGrantedActions } from "../common/useGrantedActions";

const ROUTE = "/operations/teacher-attendance";

/** Teacher Attendance (spec 008 US2): a Manager's month grid for their assigned Teachers. */
export function TeacherAttendancePage() {
  const actions = useGrantedActions(ROUTE);
  return (
    <AttendanceGridScreen
      title="Teacher Attendance"
      loadGrid={getTeacherGrid}
      canMark={actions.has("EDIT") || actions.has("CREATE")}
      canClear={actions.has("EDIT")}
    />
  );
}
