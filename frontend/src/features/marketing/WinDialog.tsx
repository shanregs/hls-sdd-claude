import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  MenuItem,
  Stack,
  TextField,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { ContactFields, type ContactValue } from "../common/ContactFields";
import { listPlaces, type PlaceSummary } from "../zones/zonesApi";
import { winProspect, type ProspectDetail } from "./marketingApi";

interface WinDialogProps {
  prospect: ProspectDetail;
  onClose: () => void;
  /** Called after the School exists and the hand-off is not available (spec 012 absent). */
  onDone: () => void;
}

const blank: ContactValue = { name: "", phone: "", email: "" };

/**
 * Creates the School of an approved prospect (Admin or Director) and hands over to the MoU form of spec 012 with the
 * latest proposal. If a School of the same name already exists in the Place, the user links it instead; nothing is
 * duplicated. The contract itself is recorded only in School Contracts.
 */
export function WinDialog({ prospect, onClose, onDone }: WinDialogProps) {
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const [places, setPlaces] = useState<PlaceSummary[]>([]);
  const [placeId, setPlaceId] = useState(prospect.placeId ?? "");
  const [billing, setBilling] = useState(
    prospect.email ?? prospect.row.contactPerson ?? "",
  );
  const [principal, setPrincipal] = useState<ContactValue>(blank);
  const [accountant, setAccountant] = useState<ContactValue>(blank);
  const [linkId, setLinkId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    void (async () => {
      const result = await listPlaces(
        authFetch,
        prospect.row.zoneId,
        "",
        0,
        100,
      );
      if (result.ok) setPlaces(result.data.content);
    })();
  }, [authFetch, prospect.row.zoneId]);

  const submit = async () => {
    if (!placeId) {
      setError("Choose the Place of the School.");
      return;
    }
    if (!linkId && !billing.trim()) {
      setError("Confirm the billing contact of the School.");
      return;
    }
    setSaving(true);
    const result = await winProspect(authFetch, prospect.row.id, {
      placeId,
      billingContact: billing || undefined,
      linkSchoolId: linkId ?? undefined,
      principal: principal.name.trim() ? principal : null,
      accountant: accountant.name.trim() ? accountant : null,
    });
    setSaving(false);
    if (!result.ok) {
      // a School of the same name exists in the Place: offer to link it instead of creating a duplicate
      const match = /School ([0-9a-f-]{36})/.exec(result.reason);
      if (
        result.status === 409 &&
        result.reason.includes("link it instead") &&
        match
      ) {
        setLinkId(match[1]);
      }
      setError(result.reason);
      return;
    }
    const handoff = result.data.handoff;
    if (handoff.available && handoff.path) {
      navigate(handoff.path, { state: { proposal: handoff.proposal } });
    } else {
      onDone();
    }
  };

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="win-title"
    >
      <DialogTitle id="win-title">Create the School</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {prospect.row.name} was approved. Create its School and go on to
          record the signed MoU. The School takes its name, address and contact
          from the prospect.
        </DialogContentText>
        <Stack spacing={2}>
          {error && (
            <Alert severity={linkId ? "warning" : "error"} role="alert">
              {error}
            </Alert>
          )}
          <TextField
            select
            required
            label="Place"
            value={placeId}
            onChange={(e) => setPlaceId(e.target.value)}
          >
            {places.map((p) => (
              <MenuItem key={p.id} value={p.id}>
                {p.name} ({p.pinCode})
              </MenuItem>
            ))}
          </TextField>
          <TextField
            required={!linkId}
            label="Billing contact"
            helperText="Who the School's bills are sent to. Confirm or change it."
            value={billing}
            onChange={(e) => setBilling(e.target.value)}
            disabled={!!linkId}
          />
          <ContactFields
            label="Principal"
            value={principal}
            onChange={setPrincipal}
          />
          <ContactFields
            label="Accountant"
            value={accountant}
            onChange={setAccountant}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          onClick={() => void submit()}
          disabled={saving}
        >
          {linkId ? "Link the existing School" : "Create School"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
