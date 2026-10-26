import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { Alert, Button, Stack } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AttendanceGridScreen } from "./AttendanceGridScreen";
import { exportCsv, getGrid } from "./attendanceApi";
import { GridFilters } from "./GridFilters";
import { NO_FILTERS, type GridFilterValues } from "./gridFilterValues";
import { LockControls } from "./LockControls";
import { LockMonthDialog } from "./LockMonthDialog";
import { useGrantedActions } from "../common/useGrantedActions";

const ROUTE = "/operations/attendance";

function localIsoDate(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const date = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${date}`;
}

/** Attendance (spec 008 US4): the organization-wide month grid for Admin and Director. */
export function AttendanceGridPage() {
  const actions = useGrantedActions(ROUTE);
  // The dashboard opens this grid with ?filter=away to show the Teachers away today (spec 028).
  const [params, setParams] = useSearchParams();
  const awayToday = params.get("filter") === "away";
  const [filters, setFilters] = useState<GridFilterValues>(NO_FILTERS);
  const [locking, setLocking] = useState(false);
  const canProcess = actions.has("PROCESS");
  const canExport = actions.has("EXPORT");
  const { authFetch } = useAuth();
  const [exportError, setExportError] = useState<string | null>(null);

  const download = async (month: string, query: string) => {
    const result = await exportCsv(authFetch, {
      month,
      query,
      zoneId: filters.zoneId || undefined,
      schoolId: filters.schoolId || undefined,
      managerId: filters.managerId || undefined,
      status: filters.status || undefined,
    });
    if (!result.ok) {
      setExportError(result.reason);
      return;
    }
    setExportError(null);
    const url = URL.createObjectURL(result.data.blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = result.data.filename;
    link.click();
    URL.revokeObjectURL(url);
  };
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
        awayOn: awayToday ? localIsoDate() : undefined,
      }}
      filters={
        <>
          <GridFilters value={filters} onChange={setFilters} />
          {awayToday && (
            <Alert
              severity="info"
              action={
                <Button color="inherit" size="small" onClick={() => setParams({})}>
                  Show all
                </Button>
              }
            >
              Showing the Teachers away today.
            </Alert>
          )}
        </>
      }
      actions={({ month, query, reload, openTeacher }) =>
        (canProcess || canExport) && (
          <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
            {exportError && (
              <Alert severity="error" role="alert">
                {exportError}
              </Alert>
            )}
            {canExport && (
              <Button variant="outlined" onClick={() => download(month, query)}>
                Export CSV
              </Button>
            )}
            {canProcess && (
              <Button variant="outlined" onClick={() => setLocking(true)}>
                Lock month
              </Button>
            )}
            {canProcess && locking && (
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
          </Stack>
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
