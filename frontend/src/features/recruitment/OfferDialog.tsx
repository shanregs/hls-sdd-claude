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
import {
  createOffer,
  listCandidates,
  listOffers,
  supersedeOffer,
  updateOffer,
  type Candidate,
  type Offer,
  type OfferInput,
} from "./recruitmentApi";

const DEFAULT_ROLE = "Trainee / English Trainer";

interface OfferDialogProps {
  /** An existing draft to change, or an issued offer to replace; absent for a new offer. */
  offer?: Offer;
  mode: "create" | "edit" | "supersede";
  onClose: () => void;
  onSaved: () => void;
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

/** Create a draft offer, change a draft, or replace an issued offer; the package is written once and never edited after issue. */
export function OfferDialog({
  offer,
  mode,
  onClose,
  onSaved,
}: OfferDialogProps) {
  const { authFetch } = useAuth();
  const [candidates, setCandidates] = useState<Candidate[]>([]);
  const [candidateId, setCandidateId] = useState(offer?.candidateId ?? "");
  const [form, setForm] = useState<OfferInput>({
    role: offer?.role ?? DEFAULT_ROLE,
    monthlySalary: offer?.monthlySalary ?? "",
    allowances: offer?.allowances ?? "",
    terms: offer?.terms ?? "",
    expectedJoining: offer?.expectedJoining ?? "",
    offerDate: offer?.offerDate ?? todayIso(),
    responseDeadline: offer?.responseDeadline ?? "",
  });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (mode !== "create") return;
    void (async () => {
      const [selected, open] = await Promise.all([
        listCandidates(authFetch, { outcome: "SELECTED" }),
        listOffers(authFetch),
      ]);
      if (!selected.ok) return;
      const busy = new Set(
        open.ok
          ? open.data
              .filter(
                (o) =>
                  o.status === "DRAFT" ||
                  o.status === "ISSUED" ||
                  o.status === "ACCEPTED",
              )
              .map((o) => o.candidateId)
          : [],
      );
      setCandidates(
        selected.data.filter((c) => !busy.has(c.id) && !c.teacherId),
      );
    })();
  }, [authFetch, mode]);

  const set =
    (key: keyof OfferInput) => (e: React.ChangeEvent<HTMLInputElement>) =>
      setForm((f) => ({ ...f, [key]: e.target.value }));

  const save = async () => {
    if (mode === "create" && !candidateId) {
      setError("Choose a selected candidate.");
      return;
    }
    if (!form.monthlySalary.trim() || !form.responseDeadline) {
      setError("The monthly salary and the response deadline are required.");
      return;
    }
    setSaving(true);
    const input: OfferInput = {
      ...form,
      allowances: form.allowances || undefined,
      terms: form.terms || undefined,
      expectedJoining: form.expectedJoining || undefined,
    };
    const result =
      mode === "create"
        ? await createOffer(authFetch, candidateId, input)
        : mode === "edit"
          ? await updateOffer(authFetch, offer!.id, input)
          : await supersedeOffer(authFetch, offer!.id, input);
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    onSaved();
  };

  const title =
    mode === "create"
      ? "New offer"
      : mode === "edit"
        ? "Change draft offer"
        : "Replace offer";

  return (
    <Dialog
      open
      onClose={onClose}
      fullWidth
      maxWidth="sm"
      aria-labelledby="offer-title"
    >
      <DialogTitle id="offer-title">{title}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {mode === "supersede" && (
            <Alert severity="info">
              A new offer replaces {offer?.candidateName}&apos;s offer; the old
              one stays on record as superseded.
            </Alert>
          )}
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {mode === "create" ? (
            <TextField
              select
              required
              label="Candidate"
              value={candidateId}
              onChange={(e) => setCandidateId(e.target.value)}
            >
              {candidates.map((c) => (
                <MenuItem key={c.id} value={c.id}>
                  {c.name}
                </MenuItem>
              ))}
            </TextField>
          ) : (
            <TextField
              label="Candidate"
              value={offer?.candidateName ?? ""}
              disabled
            />
          )}
          <TextField label="Role" value={form.role} onChange={set("role")} />
          <TextField
            required
            label="Monthly salary (Rs.)"
            value={form.monthlySalary}
            onChange={set("monthlySalary")}
            helperText="The salary HLS pays. Induction is unpaid; salary starts at the first School."
          />
          <TextField
            label="Allowances"
            value={form.allowances}
            onChange={set("allowances")}
          />
          <TextField
            label="Notice, bond and other terms"
            value={form.terms}
            onChange={set("terms")}
            multiline
            minRows={2}
          />
          <TextField
            type="date"
            label="Expected joining"
            value={form.expectedJoining}
            onChange={set("expectedJoining")}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            type="date"
            required
            label="Offer date"
            value={form.offerDate}
            onChange={set("offerDate")}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <TextField
            type="date"
            required
            label="Response deadline"
            value={form.responseDeadline}
            onChange={set("responseDeadline")}
            slotProps={{ inputLabel: { shrink: true } }}
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
          {mode === "supersede" ? "Issue replacement" : "Save"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
