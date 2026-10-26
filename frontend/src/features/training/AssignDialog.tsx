import { useEffect, useState } from "react";
import {
  Alert,
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
import { listManagers } from "../managers/managersApi";
import { listSchools } from "../schools/schoolsApi";
import { listZones } from "../zones/zonesApi";
import {
  GROUP_LABELS,
  assignPeople,
  previewAssignment,
  type AssignBody,
  type AssignPreview,
} from "./trainingApi";

interface Props {
  sessionId: string;
  onAssigned: () => void;
  onCancel: () => void;
}

interface Choice {
  id: string;
  name: string;
}

const NEEDS_REF = new Set(["SCHOOL", "MANAGER", "ZONE"]);

/** Assign a group to a session: pick the group, see how many are new, already assigned or refused, then confirm. */
export function AssignDialog({ sessionId, onAssigned, onCancel }: Props) {
  const { authFetch } = useAuth();
  const [groupKind, setGroupKind] = useState("ALL_TEACHERS");
  const [groupRef, setGroupRef] = useState("");
  const [choices, setChoices] = useState<Choice[]>([]);
  const [preview, setPreview] = useState<AssignPreview | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    let cancelled = false;
    if (!NEEDS_REF.has(groupKind)) {
      return;
    }
    void (async () => {
      let list: Choice[] = [];
      if (groupKind === "SCHOOL") {
        const r = await listSchools(authFetch, {
          query: "",
          zoneId: "",
          active: "",
          page: 0,
          size: 200,
        });
        if (r.ok)
          list = r.data.content.map((s) => ({ id: s.id, name: s.name }));
      } else if (groupKind === "ZONE") {
        const r = await listZones(authFetch, "", 0, 200);
        if (r.ok)
          list = r.data.content.map((z) => ({ id: z.id, name: z.name }));
      } else {
        const r = await listManagers(authFetch, "", 0, 200);
        if (r.ok)
          list = r.data.content
            .filter((m) => m.active)
            .map((m) => ({ id: m.id, name: m.displayName }));
      }
      if (!cancelled) setChoices(list);
    })();
    return () => {
      cancelled = true;
    };
  }, [authFetch, groupKind]);

  function body(): AssignBody {
    return { groupKind, groupRef: NEEDS_REF.has(groupKind) ? groupRef : null };
  }

  function missingRef(): boolean {
    return NEEDS_REF.has(groupKind) && !groupRef;
  }

  async function check() {
    if (missingRef()) {
      setFailure("Choose which one.");
      return;
    }
    setFailure(null);
    const result = await previewAssignment(authFetch, sessionId, body());
    if (result.ok) setPreview(result.data);
    else setFailure(result.reason);
  }

  async function confirm() {
    setBusy(true);
    const result = await assignPeople(authFetch, sessionId, body());
    setBusy(false);
    if (result.ok) onAssigned();
    else setFailure(result.reason);
  }

  return (
    <Dialog
      open
      onClose={onCancel}
      fullWidth
      maxWidth="sm"
      aria-labelledby="assign-dialog-title"
    >
      <DialogTitle id="assign-dialog-title">Assign people</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {failure && <Alert severity="error">{failure}</Alert>}
          <TextField
            select
            label="Group"
            value={groupKind}
            onChange={(e) => {
              setGroupKind(e.target.value);
              setGroupRef("");
              setPreview(null);
              setChoices([]);
            }}
            slotProps={{ select: { native: true } }}
          >
            {Object.entries(GROUP_LABELS).map(([kind, label]) => (
              <option key={kind} value={kind}>
                {label}
              </option>
            ))}
          </TextField>
          {NEEDS_REF.has(groupKind) && (
            <TextField
              select
              label={GROUP_LABELS[groupKind]}
              value={groupRef}
              onChange={(e) => {
                setGroupRef(e.target.value);
                setPreview(null);
              }}
              slotProps={{ select: { native: true } }}
            >
              <option value="">Choose</option>
              {choices.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </TextField>
          )}
          <Button onClick={() => void check()}>
            Check who will be assigned
          </Button>
          {preview && (
            <div aria-live="polite">
              <Typography>
                {preview.newCount} new, {preview.alreadyCount} already assigned,{" "}
                {preview.refusedCount} cannot be assigned.
              </Typography>
              {preview.people
                .filter((p) => p.state === "REFUSED")
                .map((p) => (
                  <Typography key={p.id} variant="body2" color="text.secondary">
                    {p.name}: {p.reason}
                  </Typography>
                ))}
            </div>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCancel} disabled={busy}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={() => void confirm()}
          disabled={busy || !preview || preview.newCount === 0}
        >
          Assign {preview ? preview.newCount : ""} people
        </Button>
      </DialogActions>
    </Dialog>
  );
}
