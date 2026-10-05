import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { ContactFields } from "../common/ContactFields";
import {
  createCollege,
  updateCollege,
  type College,
  type Contact,
} from "./recruitmentApi";

interface CollegeDialogProps {
  college?: College;
  onClose: () => void;
  onSaved: () => void;
}

const blank: Contact = { name: "", phone: "", email: "" };

/** Add a college or change its details: name, city, placement officer and principal. */
export function CollegeDialog({
  college,
  onClose,
  onSaved,
}: CollegeDialogProps) {
  const { authFetch } = useAuth();
  const [name, setName] = useState(college?.name ?? "");
  const [city, setCity] = useState(college?.city ?? "");
  const [officer, setOfficer] = useState<Contact>(
    college?.placementOfficer ?? blank,
  );
  const [principal, setPrincipal] = useState<Contact>(
    college?.principal ?? blank,
  );
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (!name.trim() || !city.trim()) {
      setError("The college name and city are required.");
      return;
    }
    setSaving(true);
    const input = {
      name,
      city,
      placementOfficer: officer.name.trim() ? officer : null,
      principal: principal.name.trim() ? principal : null,
      version: college?.version,
    };
    const result = college
      ? await updateCollege(authFetch, college.id, input)
      : await createCollege(authFetch, input);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="college-title"
    >
      <DialogTitle id="college-title">
        {college ? "Change college" : "Add a college"}
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
            label="College name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <TextField
            required
            label="City"
            value={city}
            onChange={(e) => setCity(e.target.value)}
          />
          <ContactFields
            label="Placement officer"
            value={officer}
            onChange={setOfficer}
          />
          <ContactFields
            label="Principal"
            value={principal}
            onChange={setPrincipal}
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
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}
