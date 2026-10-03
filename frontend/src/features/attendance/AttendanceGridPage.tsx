import { useState } from "react";
import { AttendanceGridScreen } from "./AttendanceGridScreen";
import { getGrid } from "./attendanceApi";
import { GridFilters } from "./GridFilters";
import { NO_FILTERS, type GridFilterValues } from "./gridFilterValues";
import { useGrantedActions } from "../common/useGrantedActions";

const ROUTE = "/operations/attendance";

/** Attendance (spec 008 US4): the organization-wide month grid for Admin and Director. */
export function AttendanceGridPage() {
  const actions = useGrantedActions(ROUTE);
  const [filters, setFilters] = useState<GridFilterValues>(NO_FILTERS);
  return (
    <AttendanceGridScreen
      title="Attendance"
      loadGrid={getGrid}
      canMark={actions.has("EDIT") || actions.has("CREATE")}
      canClear={actions.has("DELETE")}
      extraParams={{
        zoneId: filters.zoneId || undefined,
        schoolId: filters.schoolId || undefined,
        managerId: filters.managerId || undefined,
        status: filters.status || undefined,
      }}
      filters={<GridFilters value={filters} onChange={setFilters} />}
    />
  );
}
