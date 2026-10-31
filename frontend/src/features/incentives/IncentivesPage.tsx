import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Link,
  MenuItem,
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
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { formatDate, formatRupees } from "../teachers/formatters";
import { IncentiveDrawer } from "./IncentiveDrawer";
import {
  INCENTIVES_ROUTE,
  STATUS_LABELS,
  downloadCsv,
  getNoEarner,
  getSummary,
  listIncentives,
  type IncentiveRow,
  type IncentiveStatus,
  type IncentiveSummary,
  type NoEarnerRow,
} from "./incentivesApi";

type View = "all" | "mine" | "no-earner";

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: IncentiveRow[]; total: number };

type NoEarner =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: NoEarnerRow[] };

/**
 * OPERATIONS -> Incentives (spec 030): the Manager incentives by status with totals, the Manager's own, and the Schools that
 * earned nobody anything. A row opens the detail drawer, which also opens from a notification link (`/:id`).
 */
export function IncentivesPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const navigate = useNavigate();
  const { id: openId } = useParams();
  const [params] = useSearchParams();
  const roles = accessModel?.roles ?? [];
  const orgWide = roles.includes("ADMIN") || roles.includes("DIRECTOR");
  const isManager = roles.includes("MANAGER");
  const item = accessModel?.navigation.flatMap((s) => s.items).find((i) => i.route === INCENTIVES_ROUTE);
  const canExport = Boolean(item?.actions.includes("EXPORT"));

  const [tab, setTab] = useState<View>(() => {
    const wanted = params.get("tab");
    if (wanted === "no-earner") return "no-earner";
    return orgWide || !isManager ? "all" : "mine";
  });
  const [status, setStatus] = useState<IncentiveStatus | "">(() => {
    const wanted = params.get("status");
    return wanted !== null && wanted in STATUS_LABELS ? (wanted as IncentiveStatus) : "";
  });
  const [month, setMonth] = useState("");
  const [q, setQ] = useState("");
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [summary, setSummary] = useState<IncentiveSummary | null>(null);
  const [noEarner, setNoEarner] = useState<NoEarner>({ kind: "loading" });
  const [exportError, setExportError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (tab === "no-earner") return;
    setRows({ kind: "loading" });
    const filters = { status: status || undefined, month: month || undefined, q: q || undefined, size: 100 };
    const [list, totals] = await Promise.all([
      listIncentives(authFetch, filters, tab === "mine"),
      getSummary(authFetch, month || undefined, month || undefined),
    ]);
    if (totals.ok) setSummary(totals.data);
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data.content, total: list.data.totalElements }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, tab, status, month, q]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  useEffect(() => {
    if (tab !== "no-earner") return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fetch when the tab opens
    setNoEarner({ kind: "loading" });
    void getNoEarner(authFetch).then((result) =>
      setNoEarner(result.ok ? { kind: "ready", rows: result.data } : { kind: "error", reason: result.reason }),
    );
  }, [authFetch, tab]);

  async function exportCsv() {
    setExportError(null);
    const result = await downloadCsv(authFetch, { status: status || undefined, month: month || undefined, q: q || undefined });
    if (!result.ok) setExportError(result.reason);
  }

  const heading = orgWide ? "Incentives" : "My Incentives";

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ mb: 2, justifyContent: "space-between", alignItems: "center", flexWrap: "wrap" }}>
        <Typography variant="h5" component="h1">
          {heading}
        </Typography>
        {canExport && tab !== "no-earner" && (
          <Button variant="outlined" onClick={() => void exportCsv()}>
            Export CSV
          </Button>
        )}
      </Stack>
      {exportError && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {exportError}
        </Alert>
      )}

      <Tabs value={tab} onChange={(_e, value: View) => setTab(value)} aria-label="Incentive views" sx={{ mb: 2 }} variant="scrollable" scrollButtons="auto">
        <Tab value="all" label={orgWide ? "All incentives" : "My Zone"} />
        {isManager && <Tab value="mine" label="Mine" />}
        {orgWide && <Tab value="no-earner" label="No earner" />}
      </Tabs>

      {tab !== "no-earner" && (
        <>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
            <TextField select size="small" label="Status" value={status} onChange={(e) => setStatus(e.target.value as IncentiveStatus | "")} sx={{ minWidth: 200 }}>
              <MenuItem value="">All statuses</MenuItem>
              {(Object.keys(STATUS_LABELS) as IncentiveStatus[]).map((s) => (
                <MenuItem key={s} value={s}>
                  {STATUS_LABELS[s]}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              size="small"
              type="month"
              label="Month"
              value={month}
              onChange={(e) => setMonth(e.target.value)}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField size="small" label="Search School or Manager" value={q} onChange={(e) => setQ(e.target.value)} />
          </Stack>

          {summary && (
            <Typography sx={{ mb: 2 }} aria-live="polite">
              Total {month ? `for ${month}` : ""}: <strong>{formatRupees(summary.total)}</strong> (rejected not counted)
            </Typography>
          )}

          {rows.kind === "loading" && <CircularProgress aria-label="Loading incentives" />}
          {rows.kind === "error" && (
            <Alert severity="error" role="alert">
              {rows.reason}
            </Alert>
          )}
          {rows.kind === "ready" && rows.rows.length === 0 && (
            <Typography color="text.secondary">
              {tab === "mine" ? "You have not earned an incentive yet." : "No incentives here yet."}
            </Typography>
          )}
          {rows.kind === "ready" && rows.rows.length > 0 && (
            <TableContainer>
              <Table size="small" aria-label="Incentives">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell>Zone</TableCell>
                    <TableCell>Manager</TableCell>
                    <TableCell>Closed on</TableCell>
                    <TableCell align="right">Amount</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.rows.map((row) => (
                    <TableRow key={row.id} hover>
                      <TableCell>
                        <Link component="button" type="button" onClick={() => navigate(`${INCENTIVES_ROUTE}/${row.id}`)}>
                          {row.schoolName}
                        </Link>
                      </TableCell>
                      <TableCell>{row.zoneName}</TableCell>
                      <TableCell>{row.earnerName}</TableCell>
                      <TableCell>{formatDate(row.closureDate)}</TableCell>
                      <TableCell align="right">{formatRupees(row.amount)}</TableCell>
                      <TableCell>
                        <Chip size="small" label={STATUS_LABELS[row.status]} />
                        {row.mouChanged && <Chip size="small" color="warning" label="MoU changed" sx={{ ml: 0.5 }} />}
                        {row.approvedByEarner && <Chip size="small" color="info" label="By the earner" sx={{ ml: 0.5 }} />}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}

      {tab === "no-earner" && (
        <>
          {noEarner.kind === "loading" && <CircularProgress aria-label="Loading Schools without an earner" />}
          {noEarner.kind === "error" && (
            <Alert severity="error" role="alert">
              {noEarner.reason}
            </Alert>
          )}
          {noEarner.kind === "ready" && noEarner.rows.length === 0 && (
            <Typography color="text.secondary">Every School with an approved MoU has an earner.</Typography>
          )}
          {noEarner.kind === "ready" && noEarner.rows.length > 0 && (
            <TableContainer>
              <Table size="small" aria-label="Schools without an earner">
                <TableHead>
                  <TableRow>
                    <TableCell>School</TableCell>
                    <TableCell>Zone</TableCell>
                    <TableCell>Why</TableCell>
                    <TableCell>Detail</TableCell>
                    <TableCell>Status</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {noEarner.rows.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell>{row.schoolName}</TableCell>
                      <TableCell>{row.zoneName}</TableCell>
                      <TableCell>{row.reasonText}</TableCell>
                      <TableCell>{row.detail}</TableCell>
                      <TableCell>{row.resolved ? "Resolved" : "Open"}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </>
      )}

      {openId && <IncentiveDrawer id={openId} onClose={() => navigate(INCENTIVES_ROUTE)} onChanged={() => void load()} />}
    </Box>
  );
}
