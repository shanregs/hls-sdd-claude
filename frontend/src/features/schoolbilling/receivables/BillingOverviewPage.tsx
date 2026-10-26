import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Link,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import { useGrantedActions } from "../../common/useGrantedActions";
import { RaiseDuesDialog, SettingsDialog } from "./Dialogs";
import {
  BILLING_ROUTE,
  NOT_BILLED_LABELS,
  downloadCsv,
  getAgeing,
  getNotBilled,
  getOverview,
  getSettings,
  type Ageing,
  type NotBilledRow,
  type Overview,
  type Settings,
} from "./billingApi";
import { currentMonth, rupees } from "./money";

type Loaded<T> =
  { kind: "loading" } | { kind: "error"; reason: string } | { kind: "ready"; data: T };

function Money({ value }: { value: number }) {
  return <TableCell align="right">{rupees(value)}</TableCell>;
}

/** OPERATIONS -> School Billing (spec 022): what Schools owe for a month, the ageing and who could not be billed. */
export function BillingOverviewPage() {
  const { authFetch } = useAuth();
  const granted = useGrantedActions(BILLING_ROUTE);
  const [month, setMonth] = useState(currentMonth());
  const [tab, setTab] = useState(0);
  const [overview, setOverview] = useState<Loaded<Overview>>({ kind: "loading" });
  const [ageing, setAgeing] = useState<Loaded<Ageing>>({ kind: "loading" });
  const [notBilled, setNotBilled] = useState<Loaded<NotBilledRow[]>>({ kind: "loading" });
  const [raising, setRaising] = useState(false);
  const [settings, setSettings] = useState<Settings | null>(null);
  const [editing, setEditing] = useState(false);
  const [exportError, setExportError] = useState<string | null>(null);

  const load = useCallback(async () => {
    setOverview({ kind: "loading" });
    setAgeing({ kind: "loading" });
    setNotBilled({ kind: "loading" });
    const [o, a, n] = await Promise.all([
      getOverview(authFetch, month),
      getAgeing(authFetch),
      getNotBilled(authFetch, month),
    ]);
    setOverview(o.ok ? { kind: "ready", data: o.data } : { kind: "error", reason: o.reason });
    setAgeing(a.ok ? { kind: "ready", data: a.data } : { kind: "error", reason: a.reason });
    setNotBilled(n.ok ? { kind: "ready", data: n.data } : { kind: "error", reason: n.reason });
  }, [authFetch, month]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and month-driven fetch
    void load();
  }, [load]);

  async function openSettings() {
    const result = await getSettings(authFetch);
    if (result.ok) {
      setSettings(result.data);
      setEditing(true);
    }
  }

  async function exportCsv() {
    setExportError(null);
    const result = await downloadCsv(
      authFetch,
      tab === 1 ? { view: "ageing" } : { view: "overview", month },
    );
    if (!result.ok) {
      setExportError(result.reason);
      return;
    }
    const link = document.createElement("a");
    link.href = result.data;
    link.download = tab === 1 ? "school-billing-ageing.csv" : `school-billing-${month}.csv`;
    link.click();
  }

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{ alignItems: { sm: "center" }, justifyContent: "space-between", mb: 2 }}
      >
        <Typography variant="h5" component="h1">
          School Billing
        </Typography>
        <Stack direction="row" spacing={1}>
          {granted.has("EXPORT") && (
            <Button variant="outlined" onClick={() => void exportCsv()}>
              Export CSV
            </Button>
          )}
          {granted.has("EDIT") && (
            <>
              <Button variant="outlined" onClick={() => void openSettings()}>
                Settings
              </Button>
              <Button variant="contained" onClick={() => setRaising(true)}>
                Raise monthly dues
              </Button>
            </>
          )}
        </Stack>
      </Stack>
      {exportError && <Alert severity="error" sx={{ mb: 2 }}>{exportError}</Alert>}

      <TextField
        size="small"
        label="Month"
        type="month"
        value={month}
        onChange={(e) => setMonth(e.target.value)}
        slotProps={{ inputLabel: { shrink: true } }}
        sx={{ mb: 2 }}
      />

      <Tabs value={tab} onChange={(_, v: number) => setTab(v)} sx={{ mb: 2 }} aria-label="Billing views">
        <Tab label="Owed and collected" />
        <Tab label="Ageing" />
        <Tab label="Not billed" />
      </Tabs>

      {tab === 0 && (
        <>
          {overview.kind === "loading" && <CircularProgress aria-label="Loading overview" />}
          {overview.kind === "error" && <Alert severity="error">{overview.reason}</Alert>}
          {overview.kind === "ready" && (
            <>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={3} sx={{ mb: 2 }}>
                <Typography>Owed: <strong>{rupees(overview.data.owed)}</strong></Typography>
                <Typography>Collected: <strong>{rupees(overview.data.collected)}</strong></Typography>
                <Typography>Outstanding: <strong>{rupees(overview.data.outstanding)}</strong></Typography>
                <Typography>Overdue: <strong>{rupees(overview.data.overdue)}</strong></Typography>
              </Stack>
              {overview.data.schools.length === 0 ? (
                <Typography color="text.secondary">No monthly dues for this month.</Typography>
              ) : (
                <TableContainer>
                  <Table size="small" aria-label="Monthly dues by School">
                    <TableHead>
                      <TableRow>
                        <TableCell>School</TableCell>
                        <TableCell>Zone</TableCell>
                        <TableCell>Zone Manager</TableCell>
                        <TableCell align="right">Owed</TableCell>
                        <TableCell align="right">Collected</TableCell>
                        <TableCell align="right">Outstanding</TableCell>
                        <TableCell align="right">Overdue</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {overview.data.schools.map((s) => (
                        <TableRow key={s.schoolId} hover>
                          <TableCell>
                            <Link component={RouterLink} to={`${BILLING_ROUTE}/${s.schoolId}`}>
                              {s.schoolName}
                            </Link>
                          </TableCell>
                          <TableCell>{s.zoneName}</TableCell>
                          <TableCell>{s.managerName ?? "No Zone Manager"}</TableCell>
                          <Money value={s.owed} />
                          <Money value={s.collected} />
                          <Money value={s.outstanding} />
                          <Money value={s.overdue} />
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </>
          )}
        </>
      )}

      {tab === 1 && (
        <>
          {ageing.kind === "loading" && <CircularProgress aria-label="Loading ageing" />}
          {ageing.kind === "error" && <Alert severity="error">{ageing.reason}</Alert>}
          {ageing.kind === "ready" && (
            <TableContainer>
              <Table size="small" aria-label="Ageing of what is outstanding">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell align="right">Not yet due</TableCell>
                    <TableCell align="right">0 to 30 days</TableCell>
                    <TableCell align="right">31 to 60</TableCell>
                    <TableCell align="right">61 to 90</TableCell>
                    <TableCell align="right">Over 90</TableCell>
                    <TableCell align="right">Total</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {ageing.data.schools.map((r) => (
                    <TableRow key={r.schoolName} hover>
                      <TableCell>{r.schoolName}</TableCell>
                      <Money value={r.notYetDue} />
                      <Money value={r.b0to30} />
                      <Money value={r.b31to60} />
                      <Money value={r.b61to90} />
                      <Money value={r.over90} />
                      <Money value={r.total} />
                    </TableRow>
                  ))}
                  <TableRow>
                    <TableCell><strong>All Schools</strong></TableCell>
                    <Money value={ageing.data.totals.notYetDue} />
                    <Money value={ageing.data.totals.b0to30} />
                    <Money value={ageing.data.totals.b31to60} />
                    <Money value={ageing.data.totals.b61to90} />
                    <Money value={ageing.data.totals.over90} />
                    <Money value={ageing.data.totals.total} />
                  </TableRow>
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}

      {tab === 2 && (
        <>
          {notBilled.kind === "loading" && <CircularProgress aria-label="Loading not billed" />}
          {notBilled.kind === "error" && <Alert severity="error">{notBilled.reason}</Alert>}
          {notBilled.kind === "ready" && notBilled.data.length === 0 && (
            <Typography color="text.secondary">Every Teacher with a School this month is billed.</Typography>
          )}
          {notBilled.kind === "ready" && notBilled.data.length > 0 && (
            <TableContainer>
              <Table size="small" aria-label="Teachers not billed">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell>Teacher</TableCell>
                    <TableCell>Why</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {notBilled.data.map((r) => (
                    <TableRow key={`${r.schoolId}-${r.teacherName}`}>
                      <TableCell>{r.schoolName}</TableCell>
                      <TableCell>{r.teacherName}</TableCell>
                      <TableCell>{NOT_BILLED_LABELS[r.reason] ?? r.reason}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}

      {raising && (
        <RaiseDuesDialog
          month={month}
          onClose={() => setRaising(false)}
          onRaised={() => void load()}
        />
      )}
      {editing && settings && (
        <SettingsDialog
          settings={settings}
          onClose={() => setEditing(false)}
          onSaved={() => setEditing(false)}
        />
      )}
    </Box>
  );
}
