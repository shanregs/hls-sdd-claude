import { useEffect, useState } from "react";
import {
  Alert,
  Autocomplete,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listSchools, type SchoolSummary } from "../schools/schoolsApi";
import {
  getSchoolContracts,
  type ContractRow,
} from "../schoolbilling/schoolContractsApi";
import { formatDate, formatRupees, todayIso } from "./formatters";
import {
  cancelScheduledPlacement,
  placeTeacher,
  type TeacherSummary,
} from "./teachersApi";

interface PlacementDialogProps {
  teacher: TeacherSummary;
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Assigns or moves a Teacher with an effective date (FR-012). A future date schedules the move; a
 * scheduled move can be cancelled. Since spec 012 the assignment sits under the School's contract (MoU).
 */
export function PlacementDialog({
  teacher,
  onClose,
  onSaved,
}: PlacementDialogProps) {
  const { authFetch } = useAuth();
  const [schools, setSchools] = useState<SchoolSummary[]>([]);
  const [school, setSchool] = useState<SchoolSummary | null>(null);
  const [effectiveOn, setEffectiveOn] = useState(todayIso());
  const [contract, setContract] = useState<ContractRow | null>(null);
  const [positionId, setPositionId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const result = await listSchools(authFetch, {
        query: "",
        zoneId: "",
        active: "true",
        page: 0,
        size: 100,
      });
      if (!cancelled && result.ok) setSchools(result.data.content);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch]);

  // the School's live MoU decides whether a position has to be chosen (spec 012 FR-006)
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- clears the position when the School changes
    setContract(null);
    setPositionId("");
    if (!school) return;
    let cancelled = false;
    (async () => {
      const result = await getSchoolContracts(authFetch, school.id);
      if (cancelled || !result.ok) return;
      const live = (result.data.contracts ?? []).find(
        (c) => c.state === "ACTIVE" && c.status !== "ENDED",
      );
      setContract(live ?? null);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, school]);

  const vacant = contract?.positions.filter((p) => p.teacherId === null) ?? [];
  const perTeacher = contract?.salaryMode === "PER_TEACHER";

  const save = async () => {
    if (!school) {
      setError("Choose a school.");
      return;
    }
    if (perTeacher && !positionId) {
      setError("Choose the position this Teacher fills.");
      return;
    }
    setSaving(true);
    const result = await placeTeacher(
      authFetch,
      teacher.id,
      school.id,
      effectiveOn,
      perTeacher ? positionId : null,
    );
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  const cancelScheduled = async () => {
    setSaving(true);
    const result = await cancelScheduledPlacement(authFetch, teacher.id);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>School placement for {teacher.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Typography variant="body2">
            School assignment, under the School's contract.{" "}
            {teacher.school
              ? `Currently at ${teacher.school.name}.`
              : "Not placed in a school."}
          </Typography>
          {teacher.pendingPlacement && (
            <Alert
              severity="info"
              role="status"
              action={
                <Button
                  color="inherit"
                  size="small"
                  onClick={cancelScheduled}
                  disabled={saving}
                >
                  Cancel scheduled move
                </Button>
              }
            >
              Scheduled: {teacher.pendingPlacement.schoolName} from{" "}
              {formatDate(teacher.pendingPlacement.startsOn)}.
            </Alert>
          )}
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <Autocomplete
            options={schools}
            value={school}
            onChange={(_event, value) => {
              setSchool(value);
              setError(null);
            }}
            getOptionLabel={(option) => `${option.name} (${option.zone.name})`}
            isOptionEqualToValue={(a, b) => a.id === b.id}
            renderInput={(params) => (
              <TextField {...params} label="School" required />
            )}
          />
          {contract && (
            <Typography variant="body2" color="text.secondary">
              MoU: {contract.teacherCount} Teachers,{" "}
              {contract.salaryMode === "SAME_FOR_ALL"
                ? `${formatRupees(Number(contract.rate))} each`
                : "a salary for each position"}
              . {vacant.length} of {contract.positions.length} positions vacant.
            </Typography>
          )}
          {perTeacher && (
            <TextField
              select
              label="Position"
              required
              value={positionId}
              onChange={(e) => {
                setPositionId(e.target.value);
                setError(null);
              }}
              helperText={
                vacant.length === 0
                  ? "Every position is filled. Record a new contract to add Teachers."
                  : "The salary the School pays for this Teacher."
              }
              slotProps={{
                select: { native: true },
                inputLabel: { shrink: true },
              }}
            >
              <option value=""></option>
              {vacant.map((p) => (
                <option key={p.id} value={p.id}>
                  {`Position ${p.number}${p.title ? ` (${p.title})` : ""} - ${formatRupees(Number(p.salary))}`}
                </option>
              ))}
            </TextField>
          )}
          <TextField
            label="Effective date"
            type="date"
            value={effectiveOn}
            onChange={(e) => setEffectiveOn(e.target.value)}
            helperText="A future date schedules the move."
            slotProps={{ inputLabel: { shrink: true } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={save} disabled={saving}>
          Save placement
        </Button>
      </DialogActions>
    </Dialog>
  );
}
