import { useEffect, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Alert,
  Box,
  Chip,
  CircularProgress,
  FormControlLabel,
  Link,
  MenuItem,
  Pagination,
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
  listPeople,
  PERSON_KIND_LABELS,
  type PersonKind,
  type PersonPage,
} from "./salaryStructuresApi";

const ROUTE = "/operations/salary-structures";
const PAGE_SIZE = 25;

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; page: PersonPage };

/**
 * Everyone who is paid, with the salary in effect today (spec 013a User Story 5). People are listed by name; the
 * filter keeps those with no salary yet, so the gap is found before payroll. A Teacher's salary is read-only here and
 * is changed on the Teachers screen. Shown to Admin and Director only.
 */
export function PeopleTab() {
  const { authFetch } = useAuth();
  const [state, setState] = useState<State>({ kind: "loading" });
  const [kind, setKind] = useState<PersonKind | "">("");
  const [search, setSearch] = useState("");
  const [withoutSalary, setWithoutSalary] = useState(false);
  const [page, setPage] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const timer = setTimeout(
      async () => {
        setState({ kind: "loading" });
        const result = await listPeople(authFetch, {
          kind,
          q: search,
          withoutSalary,
          page,
          size: PAGE_SIZE,
        });
        if (cancelled) return;
        setState(
          result.ok
            ? { kind: "ready", page: result.data }
            : { kind: "error", reason: result.reason },
        );
      },
      search ? 250 : 0,
    );
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [authFetch, kind, search, withoutSalary, page]);

  const pages =
    state.kind === "ready"
      ? Math.max(1, Math.ceil(state.page.totalElements / PAGE_SIZE))
      : 1;

  return (
    <Box>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        Each person is paid their own salary, agreed when they were recruited.
        Open a person to record or change it.
      </Typography>

      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={2}
        sx={{ mb: 2, alignItems: { sm: "center" } }}
      >
        <TextField
          size="small"
          label="Search by name"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          sx={{ minWidth: 220 }}
        />
        <TextField
          select
          size="small"
          label="Who"
          value={kind}
          onChange={(e) => {
            setKind(e.target.value as PersonKind | "");
            setPage(0);
          }}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">Everyone</MenuItem>
          {(Object.keys(PERSON_KIND_LABELS) as PersonKind[]).map((k) => (
            <MenuItem key={k} value={k}>
              {PERSON_KIND_LABELS[k]}s
            </MenuItem>
          ))}
        </TextField>
        <FormControlLabel
          control={
            <Switch
              checked={withoutSalary}
              onChange={(e) => {
                setWithoutSalary(e.target.checked);
                setPage(0);
              }}
            />
          }
          label="Only people without a salary"
        />
      </Stack>

      {state.kind === "loading" && (
        <Box sx={{ p: 3, textAlign: "center" }}>
          <CircularProgress aria-label="Loading people" />
        </Box>
      )}
      {state.kind === "error" && (
        <Alert severity="error" role="alert">
          {state.reason}
        </Alert>
      )}
      {state.kind === "ready" && (
        <>
          <Paper variant="outlined">
            <TableContainer>
              <Table size="small" aria-label="People and their salaries">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Who</TableCell>
                    <TableCell>Designation</TableCell>
                    <TableCell>Salary in effect</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {state.page.content.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={4}>
                        {withoutSalary
                          ? "Everyone has a salary."
                          : "No people found."}
                      </TableCell>
                    </TableRow>
                  )}
                  {state.page.content.map((row) => (
                    <TableRow key={`${row.kind}-${row.personId}`} hover>
                      <TableCell>
                        <Link
                          component={RouterLink}
                          to={`${ROUTE}/people/${row.kind}/${row.personId}`}
                        >
                          {row.name}
                        </Link>{" "}
                        {!row.active && (
                          <Chip size="small" label="Not working here" />
                        )}
                      </TableCell>
                      <TableCell>
                        {PERSON_KIND_LABELS[row.kind]}
                        {row.roles.length > 0 && (
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            sx={{ display: "block" }}
                          >
                            {row.roles
                              .map(
                                (r) => r.charAt(0) + r.slice(1).toLowerCase(),
                              )
                              .join(", ")}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{row.designation ?? ""}</TableCell>
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
                          <Chip
                            size="small"
                            color={row.active ? "warning" : "default"}
                            label="No salary yet"
                          />
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
          {pages > 1 && (
            <Pagination
              sx={{ mt: 2 }}
              count={pages}
              page={page + 1}
              onChange={(_event, value) => setPage(value - 1)}
              aria-label="People pages"
            />
          )}
        </>
      )}
    </Box>
  );
}
