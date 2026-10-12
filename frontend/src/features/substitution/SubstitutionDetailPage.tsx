import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Chip,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { useParams } from "react-router-dom";
import { useAuth } from "../../auth/useAuth";
import { formatDate } from "../attendance/monthUtils";
import type { ApiResult } from "../common/masterDataApi";
import {
  assignSubstitute,
  coverRequirement,
  getCandidates,
  getRequirement,
  informSchool,
  METHOD_LABELS,
  releaseRequirement,
  STATUS_LABELS,
  type Candidate,
  type Detail,
  type InformMethod,
  type Requirement,
} from "./substitutionApi";

/** One requirement (spec 017a): who is free, the actions, the School informed record and the history. */
export function SubstitutionDetailPage() {
  const { id = "" } = useParams();
  const { authFetch } = useAuth();
  const [detail, setDetail] = useState<Detail | null>(null);
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [contact, setContact] = useState("");
  const [method, setMethod] = useState<InformMethod>("PHONE");
  const [informNote, setInformNote] = useState("");

  const [reload, setReload] = useState(0);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const d = await getRequirement(authFetch, id);
      if (cancelled) return;
      if (!d.ok) {
        setError(d.reason);
        return;
      }
      setDetail(d.data);
      setError(null);
      setContact((c) => c || d.data.requirement.principalContact || "");
      const c = await getCandidates(authFetch, id);
      if (!cancelled) setCandidates(c.ok ? c.data : []);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, id, reload]);

  async function act(call: () => Promise<ApiResult<Requirement>>) {
    const result = await call();
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setReload((n) => n + 1);
  }

  if (error && !detail) return <Alert severity="error">{error}</Alert>;
  if (!detail) return <Typography>Loading...</Typography>;
  const r = detail.requirement;
  const live = r.status !== "CANCELLED";

  return (
    <Box>
      <Typography variant="h1" sx={{ fontSize: "1.5rem", mb: 1 }}>
        Substitution for {formatDate(r.dutyDate)}
      </Typography>
      <Stack direction="row" spacing={1} sx={{ mb: 2 }}>
        <Chip label={STATUS_LABELS[r.status]} />
        <Chip variant="outlined" label={r.kind === "LEAVE" ? "Approved leave" : "Absent"} />
        <Chip variant="outlined" label={r.dayValue === 0.5 ? "Half day" : "Whole day"} />
      </Stack>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Typography>
        {r.teacherName} at {r.schoolName}
      </Typography>
      {r.substituteName && <Typography>Substitute: {r.substituteName}</Typography>}
      {r.substituteNowUnavailable && (
        <Alert severity="warning" sx={{ my: 1 }}>
          The substitute is now away that day. Choose another Teacher.
        </Alert>
      )}
      {r.coverName && <Typography>Covered by {r.coverName}</Typography>}
      {!live && <Alert severity="info" sx={{ my: 1 }}>This requirement was cancelled because the absence was withdrawn.</Alert>}

      {live && (
        <Paper sx={{ p: 2, my: 2 }}>
          <Typography variant="h2" sx={{ fontSize: "1.1rem", mb: 1 }}>
            Teachers
          </Typography>
          <Table size="small" aria-label="Teachers who could cover">
            <TableHead>
              <TableRow>
                <TableCell>Teacher</TableCell>
                <TableCell>Availability</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {candidates.map((c) => (
                <TableRow key={c.teacherId}>
                  <TableCell>{c.name}</TableCell>
                  <TableCell>{c.available ? "Available" : `Not available: ${c.reason}`}</TableCell>
                  <TableCell>
                    <Button
                      size="small"
                      disabled={!c.available || c.current}
                      onClick={() => act(() => assignSubstitute(authFetch, id, c.teacherId, r.version))}
                      aria-label={`Assign ${c.name}`}
                    >
                      {c.current ? "Assigned" : "Assign"}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
          <Stack direction="row" spacing={2} sx={{ mt: 2, alignItems: "flex-start" }}>
            <Button
              variant="outlined"
              disabled={r.status === "MANAGER_COVER"}
              onClick={() => act(() => coverRequirement(authFetch, id, "", r.version))}
            >
              I will cover the class
            </Button>
            <TextField
              size="small"
              label="Reason for releasing"
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
            <Button
              variant="outlined"
              disabled={r.status === "OPEN" || reason.trim() === ""}
              onClick={() => act(() => releaseRequirement(authFetch, id, reason, r.version))}
            >
              Release
            </Button>
          </Stack>
        </Paper>
      )}

      {live && r.status !== "OPEN" && (
        <Paper sx={{ p: 2, my: 2 }}>
          <Typography variant="h2" sx={{ fontSize: "1.1rem", mb: 1 }}>
            School informed
          </Typography>
          {r.informed ? (
            <Typography>
              {r.informed.contact} was told by {METHOD_LABELS[r.informed.method]} ({r.informed.byName},{" "}
              {formatDate(r.informed.at.slice(0, 10))}).
            </Typography>
          ) : (
            <Stack direction="row" spacing={2} sx={{ alignItems: "flex-start" }}>
              <TextField size="small" label="Who was told" value={contact} onChange={(e) => setContact(e.target.value)} />
              <TextField
                select
                size="small"
                label="How"
                value={method}
                onChange={(e) => setMethod(e.target.value as InformMethod)}
                sx={{ minWidth: 140 }}
              >
                {Object.entries(METHOD_LABELS).map(([k, v]) => (
                  <MenuItem key={k} value={k}>
                    {v}
                  </MenuItem>
                ))}
              </TextField>
              <TextField size="small" label="Note" value={informNote} onChange={(e) => setInformNote(e.target.value)} />
              <Button
                variant="contained"
                disabled={contact.trim() === ""}
                onClick={() =>
                  act(() => informSchool(authFetch, id, { contact, method, note: informNote, version: r.version }))
                }
              >
                Record School informed
              </Button>
            </Stack>
          )}
        </Paper>
      )}

      <Typography variant="h2" sx={{ fontSize: "1.1rem", mt: 3, mb: 1 }}>
        History
      </Typography>
      <Table size="small" aria-label="History">
        <TableHead>
          <TableRow>
            <TableCell>When</TableCell>
            <TableCell>What</TableCell>
            <TableCell>Who</TableCell>
            <TableCell>Note</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {detail.history.map((h, i) => (
            <TableRow key={i}>
              <TableCell>{formatDate(h.at.slice(0, 10))}</TableCell>
              <TableCell>
                {h.action}
                {h.substituteName ? ` (${h.substituteName})` : ""}
              </TableCell>
              <TableCell>{h.actorName}</TableCell>
              <TableCell>{h.note ?? ""}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
