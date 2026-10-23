import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Alert, Box, CircularProgress, Link, Paper, Stack, TextField, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, formatRupees, todayIso } from "../teachers/formatters";
import {
  EXPENSES_ROUTE,
  getCostPerSchool,
  getCostPerTeacher,
  type CostMeasure,
} from "./expensesApi";

function monthStart(): string {
  return `${todayIso().slice(0, 8)}01`;
}

/**
 * OPERATIONS -> Expenses -> Cost measures (spec 015 US6): cost per School acquired and per Teacher trained for a period,
 * with the underlying cost, the divisor and the claims behind each. "No Schools acquired" and "No Teachers trained yet"
 * replace a division by zero.
 */
export function CostMeasuresPage() {
  const { authFetch } = useAuth();
  const [from, setFrom] = useState(monthStart());
  const [to, setTo] = useState(todayIso());
  const [school, setSchool] = useState<CostMeasure | null>(null);
  const [teacher, setTeacher] = useState<CostMeasure | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (from === "" || to === "" || to < from) {
      setError("Choose a period that starts before it ends.");
      return;
    }
    setError(null);
    setSchool(null);
    setTeacher(null);
    const [s, t] = await Promise.all([
      getCostPerSchool(authFetch, from, to),
      getCostPerTeacher(authFetch, from, to),
    ]);
    if (s.ok) setSchool(s.data);
    else setError(s.reason);
    if (t.ok) setTeacher(t.data);
    else setError(t.reason);
  }, [authFetch, from, to]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and period-driven fetch
    void load();
  }, [load]);

  return (
    <Box>
      <Link component={RouterLink} to={EXPENSES_ROUTE} underline="hover">
        Back to Expenses
      </Link>
      <Typography variant="h5" component="h1" sx={{ my: 2 }}>
        Cost measures
      </Typography>
      <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
        <TextField
          label="From"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="To"
          type="date"
          value={to}
          onChange={(e) => setTo(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
        />
      </Stack>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      {!school && !teacher && !error && (
        <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
          <CircularProgress aria-label="Loading the cost measures" />
        </Box>
      )}
      <Stack spacing={2}>
        {school && (
          <Measure
            title="Cost per School acquired"
            measure={school}
            unit="Schools acquired"
            emptyText="No Schools acquired"
            emptyState="NO_SCHOOLS"
          />
        )}
        {teacher && (
          <Measure
            title="Cost per Teacher trained"
            measure={teacher}
            unit="Teachers trained"
            emptyText="No Teachers trained yet"
            emptyState="NO_TEACHERS"
          />
        )}
      </Stack>
    </Box>
  );
}

function Measure({
  title,
  measure,
  unit,
  emptyText,
  emptyState,
}: {
  title: string;
  measure: CostMeasure;
  unit: string;
  emptyText: string;
  emptyState: CostMeasure["state"];
}) {
  return (
    <Paper variant="outlined" sx={{ p: 2 }} aria-label={title}>
      <Typography variant="h6" component="h2">
        {title}
      </Typography>
      <Typography variant="h4" component="p" sx={{ my: 1 }}>
        {measure.state === emptyState || measure.perUnit === null ? emptyText : formatRupees(Number(measure.perUnit))}
      </Typography>
      <Typography>
        Underlying cost {formatRupees(Number(measure.cost))}; {unit}: {measure.divisor}.
      </Typography>
      {measure.claims.length > 0 && (
        <ul aria-label={`Claims behind ${title}`}>
          {measure.claims.map((c) => (
            <li key={c.id}>
              {c.claimantName}, {c.categoryLabel}, {formatDate(c.expenseDate)}: {formatRupees(Number(c.amount))}
            </li>
          ))}
        </ul>
      )}
    </Paper>
  );
}
