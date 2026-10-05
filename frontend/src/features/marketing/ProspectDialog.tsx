import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { listZones, type ZoneSummary } from "../zones/zonesApi";
import {
  createProspect,
  updateProspect,
  type ProspectDetail,
} from "./marketingApi";

interface ProspectDialogProps {
  /** The prospect being changed, or undefined to add one. */
  prospect?: ProspectDetail;
  onClose: () => void;
  onSaved: (id: string) => void;
}

/** Add a prospect or change its details. A School already on the list in the same Zone is refused with a pointer to it. */
export function ProspectDialog({
  prospect,
  onClose,
  onSaved,
}: ProspectDialogProps) {
  const { authFetch } = useAuth();
  const [zones, setZones] = useState<ZoneSummary[]>([]);
  const [form, setForm] = useState({
    name: prospect?.row.name ?? "",
    board: prospect?.row.board ?? "",
    address: prospect?.address ?? "",
    zoneId: prospect?.row.zoneId ?? "",
    contactPerson: prospect?.row.contactPerson ?? "",
    designation: prospect?.designation ?? "",
    phone: prospect?.phone ?? "",
    email: prospect?.email ?? "",
    expectedTeachers: prospect?.row.expectedTeachers?.toString() ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void (async () => {
      const result = await listZones(authFetch, "", 0, 100);
      if (result.ok) setZones(result.data.content);
    })();
  }, [authFetch]);

  const set =
    (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const save = async () => {
    if (!form.name.trim()) {
      setError("The School name is required.");
      return;
    }
    if (!form.zoneId) {
      setError("Choose the Zone of the School.");
      return;
    }
    const teachers =
      form.expectedTeachers.trim() === ""
        ? undefined
        : Number(form.expectedTeachers);
    if (
      teachers !== undefined &&
      (!Number.isInteger(teachers) || teachers < 1 || teachers > 500)
    ) {
      setError("The expected number of Teachers must be between 1 and 500.");
      return;
    }
    setSaving(true);
    const input = {
      name: form.name,
      board: form.board || undefined,
      address: form.address || undefined,
      zoneId: form.zoneId,
      contactPerson: form.contactPerson || undefined,
      designation: form.designation || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
      expectedTeachers: teachers,
      version: prospect?.row.version,
    };
    const result = prospect
      ? await updateProspect(authFetch, prospect.row.id, input)
      : await createProspect(authFetch, input);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved(result.data.row.id);
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="prospect-title"
    >
      <DialogTitle id="prospect-title">
        {prospect ? "Change prospect" : "Add a prospect"}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField
            required
            label="School name"
            value={form.name}
            onChange={set("name")}
          />
          <TextField label="Board" value={form.board} onChange={set("board")} />
          <TextField
            select
            required
            label="Zone"
            value={form.zoneId}
            onChange={set("zoneId")}
            disabled={!!prospect}
          >
            {zones.map((z) => (
              <MenuItem key={z.id} value={z.id}>
                {z.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Address"
            value={form.address}
            onChange={set("address")}
            multiline
            minRows={2}
          />
          <TextField
            label="Contact person"
            value={form.contactPerson}
            onChange={set("contactPerson")}
          />
          <TextField
            label="Designation"
            value={form.designation}
            onChange={set("designation")}
          />
          <TextField label="Phone" value={form.phone} onChange={set("phone")} />
          <TextField label="Email" value={form.email} onChange={set("email")} />
          <TextField
            label="Expected number of Teachers"
            type="number"
            value={form.expectedTeachers}
            onChange={set("expectedTeachers")}
            slotProps={{ htmlInput: { min: 1, max: 500 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void save()}
          disabled={saving}
        >
          {prospect ? "Save" : "Add prospect"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
