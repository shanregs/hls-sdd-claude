import { useEffect, useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  FormControlLabel,
  Link,
  MenuItem,
  Paper,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatRupees } from "../teachers/formatters";
import {
  KIND_LABELS,
  listSalaryStructures,
  type DesignationKind,
  type SalaryListEntry,
} from "./salaryStructuresApi";

const ROUTE = "/operations/salary-structures";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: SalaryListEntry[] };

/**
 * Every designation with its suggested default salary (spec 013a). The amount is only a starting figure offered when a
 * person's own salary is entered; nobody is paid it. A flag shows the designations whose holders have no salary of their
 * own, so the gap is found before payroll.
 */
export function DesignationDefaultsTab() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [includeRetired, setIncludeRetired] = useState(false);
  const [flaggedOnly, setFlaggedOnly] = useState(false);
  const [kind, setKind] = useState<DesignationKind | "">("");

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setState({ kind: "loading" });
      const result = await listSalaryStructures(authFetch, includeRetired);
      if (cancelled) return;
      setState(
        result.ok
          ? { kind: "ready", rows: result.data }
          : { kind: "error", reason: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, includeRetired]);

  const visible = useMemo(() => {
    if (state.kind !== "ready") return [];
    return state.rows
      .filter((r) => !kind || r.kind === kind)
      .filter((r) => !flaggedOnly || r.missingSalary.flag);
  }, [state, kind, flaggedOnly]);

  const flaggedCount =
    state.kind === "ready"
      ? state.rows.filter((r) => r.missingSalary.flag).length
      : 0;

  return (
    <Box>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        These amounts are only suggestions, offered when a person&apos;s salary
        is entered. Nobody is paid a designation&apos;s amount: everyone is paid
        their own salary.
      </Typography>

      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{ mb: 2, alignItems: { sm: "center" } }}
      >
        <TextField
          select
          size="small"
          label="For"
          value={kind}
          onChange={(e) => setKind(e.target.value as DesignationKind | "")}
          sx={{ minWidth: 160 }}
        >
          <MenuItem value="">Managers and Teachers</MenuItem>
          {(Object.keys(KIND_LABELS) as DesignationKind[]).map((k) => (
            <MenuItem key={k} value={k}>
              {KIND_LABELS[k]}
            </MenuItem>
          ))}
        </TextField>
        <FormControlLabel
          control={
            <Switch
              checked={flaggedOnly}
              onChange={(e) => setFlaggedOnly(e.target.checked)}
            />
          }
          label={`Only designations held by people without a salary (${flaggedCount})`}
        />
        <FormControlLabel
          control={
            <Switch
              checked={includeRetired}
              onChange={(e) => setIncludeRetired(e.target.checked)}
            />
          }
          label="Show retired designations"
        />
      </Stack>

      {state.kind === "loading" && (
        <Box sx={{ p: 3, textAlign: "center" }}>
          <CircularProgress aria-label="Loading designation defaults" />
        </Box>
      )}
      {state.kind === "error" && (
        <Alert severity="error" role="alert">
          {state.reason}
        </Alert>
      )}
      {state.kind === "ready" && (
        <Paper variant="outlined">
          <TableContainer>
            <Table
              size="small"
              aria-label="Designations and their suggested salaries"
            >
              <TableHead>
                <TableRow>
                  <TableCell>Designation</TableCell>
                  <TableCell>For</TableCell>
                  <TableCell>Suggested default</TableCell>
                  <TableCell>Holders without a salary of their own</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visible.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4}>
                      {flaggedOnly
                        ? "No designation is held by people without a salary."
                        : "No designations yet."}
                    </TableCell>
                  </TableRow>
                )}
                {visible.map((row) => (
                  <TableRow key={row.designationId} hover>
                    <TableCell>
                      <Link
                        component={RouterLink}
                        to={`${ROUTE}/${row.designationId}`}
                      >
                        {row.name}
                      </Link>{" "}
                      {row.retired && <Chip size="small" label="Retired" />}
                    </TableCell>
                    <TableCell>{KIND_LABELS[row.kind]}</TableCell>
                    <TableCell>
                      {row.inEffect ? (
                        <>
                          {formatRupees(Number(row.inEffect.amount))}{" "}
                          <Typography
                            component="span"
                            variant="body2"
                            color="text.secondary"
                          >
                            from {formatDate(row.inEffect.effectiveOn)}
                          </Typography>
                        </>
                      ) : (
                        <Typography component="span" color="text.secondary">
                          No default yet
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      {row.missingSalary.flag ? (
                        <>
                          <Chip
                            size="small"
                            color="warning"
                            label={`${row.missingSalary.people} ${
                              row.missingSalary.people === 1
                                ? "person"
                                : "people"
                            }`}
                          />
                          {row.missingSalary.note && (
                            <Typography
                              variant="caption"
                              color="text.secondary"
                              sx={{ display: "block" }}
                            >
                              {row.missingSalary.note}
                            </Typography>
                          )}
                        </>
                      ) : (
                        <Typography component="span" color="text.secondary">
                          None
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}
    </Box>
  );
}
