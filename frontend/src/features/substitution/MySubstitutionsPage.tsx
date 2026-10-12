import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../attendance/monthUtils";
import { getMyAssignments, type MyAssignment } from "./substitutionApi";

/** MY ATTENDANCE -> My Substitutions (spec 017a US4): the days the Teacher was assigned to cover. */
export function MySubstitutionsPage() {
  const { authFetch } = useAuth();
  const [rows, setRows] = useState<MyAssignment[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await getMyAssignments(authFetch);
      if (cancelled) return;
      if (result.ok) setRows(result.data);
      else setError(result.reason);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  return (
    <Box>
      <Typography variant="h1" sx={{ fontSize: "1.5rem", mb: 2 }}>
        My Substitutions
      </Typography>
      {error && <Alert severity="error">{error}</Alert>}
      {rows && rows.length === 0 && <Alert severity="info">You have no substitution days.</Alert>}
      {rows && rows.length > 0 && (
        <TableContainer component={Paper}>
          <Table size="small" aria-label="My substitution days">
            <TableHead>
              <TableRow>
                <TableCell>Date</TableCell>
                <TableCell>School</TableCell>
                <TableCell>Day</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r) => (
                <TableRow key={r.id}>
                  <TableCell>{formatDate(r.dutyDate)}</TableCell>
                  <TableCell>{r.schoolName}</TableCell>
                  <TableCell>{r.dayValue === 0.5 ? "Half day" : "Whole day"}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  );
}
