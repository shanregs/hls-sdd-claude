import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../../auth/useAuth";
import { formatDate } from "../../teachers/formatters";
import { getLine, type Line } from "./payrollRunApi";
import { ADJUSTMENT_KIND_LABELS, type LineWorking } from "./lineWorking";
import { money } from "./format";

interface Props {
  runId: string;
  lineId: string | null;
  onClose: () => void;
}

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; line: Line };

function Row({
  label,
  value,
  strong,
}: {
  label: string;
  value: string;
  strong?: boolean;
}) {
  return (
    <Stack
      direction="row"
      sx={{ justifyContent: "space-between", gap: 2, flexWrap: "wrap" }}
    >
      <Typography component="dt" sx={{ fontWeight: strong ? 700 : 400 }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontWeight: strong ? 700 : 400 }}>
        {value}
      </Typography>
    </Stack>
  );
}

function Section({
  id,
  title,
  children,
}: {
  id: string;
  title: string;
  children: React.ReactNode;
}) {
  return (
    <Box component="section" aria-labelledby={id}>
      <Typography id={id} variant="h3" sx={{ fontSize: "1rem", mb: 1 }}>
        {title}
      </Typography>
      {children}
    </Box>
  );
}

/**
 * The frozen working of one line, so a reviewer can reproduce the net pay from this screen alone (spec 013b SC-005):
 * the salary rows and the dates each applied to, the divisor, each unpaid day, the amounts before and after rounding,
 * the policy and each adjustment. It shows what was stored with the line, never a fresh calculation.
 */
export function RunLineWorkingDialog({ runId, lineId, onClose }: Props) {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });

  useEffect(() => {
    if (!lineId) return;
    let cancelled = false;
    void (async () => {
      setState({ kind: "loading" });
      const result = await getLine(authFetch, runId, lineId);
      if (cancelled) return;
      setState(
        result.ok
          ? { kind: "ready", line: result.data }
          : { kind: "error", reason: result.reason },
      );
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, runId, lineId]);

  const line = state.kind === "ready" ? state.line : null;
  const working = line?.working as LineWorking | undefined;

  return (
    <Dialog
      open={lineId !== null}
      onClose={onClose}
      fullWidth
      maxWidth="md"
      aria-labelledby="line-working-title"
    >
      <DialogTitle id="line-working-title">
        {line ? `How ${line.name} was paid` : "Working"}
      </DialogTitle>
      <DialogContent dividers>
        {state.kind === "loading" && (
          <Stack direction="row" sx={{ gap: 1.5, alignItems: "center" }}>
            <CircularProgress size={22} aria-label="Loading the working" />
            <Typography>Loading the working…</Typography>
          </Stack>
        )}
        {state.kind === "error" && (
          <Alert severity="error">{state.reason}</Alert>
        )}
        {line && line.state === "CANNOT_PAY" && (
          <Alert severity="warning">
            {line.name} cannot be paid in this run: {line.reason}
          </Alert>
        )}
        {line && working && line.state === "PAYABLE" && (
          <Stack spacing={2.5}>
            {line.warning && <Alert severity="warning">{line.warning}</Alert>}

            <Section id="w-salary" title="Salary">
              <Stack component="dl" spacing={0.5} sx={{ m: 0 }}>
                {(working.salaryRows ?? []).map((r) => (
                  <Row
                    key={`${r.from}-${r.monthly}`}
                    label={
                      r.from === r.to
                        ? formatDate(r.from)
                        : `${formatDate(r.from)} to ${formatDate(r.to)}`
                    }
                    value={`${money(r.monthly)} a month`}
                  />
                ))}
                <Row
                  label="Days paid"
                  value={`${line.payableDays} of ${line.workingDays} working days`}
                />
              </Stack>
            </Section>

            <Section id="w-earned" title="Earned pay">
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                Each paid day is that day&apos;s salary divided by the
                month&apos;s working days.
              </Typography>
              <Stack component="dl" spacing={0.5} sx={{ m: 0 }}>
                <Row
                  label="Before rounding"
                  value={money(working.earned?.unrounded)}
                />
                <Row label="Earned pay" value={money(line.earned)} strong />
              </Stack>
            </Section>

            <Section id="w-deduction" title="Deduction for days without pay">
              {(working.unpaid ?? []).length === 0 ? (
                <Typography>No unpaid days.</Typography>
              ) : (
                <TableContainer>
                  <Table size="small" aria-label="Unpaid days">
                    <TableHead>
                      <TableRow>
                        <TableCell>Date</TableCell>
                        <TableCell align="right">Day value</TableCell>
                        <TableCell>Reason</TableCell>
                        <TableCell align="right">Amount</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {(working.unpaid ?? []).map((d) => (
                        <TableRow key={d.date}>
                          <TableCell>{formatDate(d.date)}</TableCell>
                          <TableCell align="right">{d.dayValue}</TableCell>
                          <TableCell>{d.reason ?? ""}</TableCell>
                          <TableCell align="right">{money(d.amount)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
              <Stack component="dl" spacing={0.5} sx={{ m: 0, mt: 1 }}>
                <Row
                  label="Before rounding"
                  value={money(working.deduction?.unrounded)}
                />
                <Row label="Deduction" value={money(line.deduction)} strong />
              </Stack>
            </Section>

            <Section id="w-adjustments" title="Adjustments">
              {(working.adjustments ?? []).length === 0 ? (
                <Typography>No adjustments.</Typography>
              ) : (
                <Stack component="dl" spacing={0.5} sx={{ m: 0 }}>
                  {(working.adjustments ?? []).map((a) => (
                    <Row
                      key={a.id}
                      label={`${ADJUSTMENT_KIND_LABELS[a.kind] ?? a.kind}: ${a.reason}`}
                      value={money(a.amount)}
                    />
                  ))}
                </Stack>
              )}
            </Section>

            <Box component="section" aria-label="Net pay">
              <Stack component="dl" spacing={0.5} sx={{ m: 0 }}>
                <Row label="Net pay" value={money(line.net)} strong />
              </Stack>
              <Typography variant="body2" color="text.secondary">
                Net pay is earned pay, less the deduction, plus the adjustments.
              </Typography>
              {working.policy && (
                <Typography variant="body2" color="text.secondary">
                  Pay policy in effect from{" "}
                  {formatDate(working.policy.effectiveOn)}: rounded{" "}
                  {working.policy.rounding.toLowerCase().replace("_", " ")}, a
                  half day counts as {working.policy.halfDayFraction}.
                </Typography>
              )}
            </Box>
          </Stack>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
