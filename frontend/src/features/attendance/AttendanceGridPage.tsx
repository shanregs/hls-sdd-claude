import { useState } from "react";
import { Button } from "@mui/material";
import { AttendanceGridScreen } from "./AttendanceGridScreen";
import { getGrid } from "./attendanceApi";
import { GridFilters } from "./GridFilters";
import { NO_FILTERS, type GridFilterValues } from "./gridFilterValues";
import { LockControls } from "./LockControls";
import { LockMonthDialog } from "./LockMonthDialog";
import { useGrantedActions } from "../common/useGrantedActions";

const ROUTE = "/operations/attendance";

/** Attendance (spec 008 US4): the organization-wide month grid for Admin and Director. */
export function AttendanceGridPage() {
  const actions = useGrantedActions(ROUTE);
  const [filters, setFilters] = useState<GridFilterValues>(NO_FILTERS);
  const [locking, setLocking] = useState(false);
  const canProcess = actions.has("PROCESS");
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
      actions={({ month, reload, openTeacher }) =>
        canProcess && (
          <>
            <Button variant="outlined" onClick={() => setLocking(true)}>
              Lock month
            </Button>
            {locking && (
              <LockMonthDialog
                month={month}
                onClose={() => setLocking(false)}
                onLocked={() => {
                  setLocking(false);
                  reload();
                }}
                onOpenTeacher={(id, name) => {
                  setLocking(false);
                  openTeacher(id, name);
                }}
              />
            )}
          </>
        )
      }
      panelExtras={({ row, month, reload, refreshKey }) =>
        canProcess && (
          <LockControls
            teacherId={row.teacherId}
            teacherName={row.name}
            month={month}
            refreshKey={refreshKey}
            onChanged={reload}
          />
        )
      }
    />
  );
}
