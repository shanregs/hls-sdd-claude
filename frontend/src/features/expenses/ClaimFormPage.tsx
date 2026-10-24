import { useEffect, useMemo, useRef, useState } from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Link,
  List,
  ListItem,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { formatDate, todayIso } from "../teachers/formatters";
import {
  CATEGORY_LABELS,
  MAX_BILLS,
  MY_EXPENSES_ROUTE,
  billProblem,
  createClaim,
  getAreas,
  getLinkOptions,
  getMine,
  submitClaim,
  updateClaim,
  uploadBill,
  type Area,
  type Category,
  type ClaimBody,
  type ClaimDetail,
  type LinkOption,
} from "./expensesApi";

const HEAD_OFFICE = "head-office";

function newRequestId(): string {
  return typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random()}`.replace(/\./g, "");
}

/**
 * Raise or correct a claim (spec 015 US1, US2): date, amount, category, area, note, who paid, bills, the reasons the
 * category needs and the link. Save keeps a Draft; Submit also sends it for review. The request id is made once, so a retry
 * after a lost connection never creates a second claim.
 */
export function ClaimFormPage() {
  const { id } = useParams();
  const editing = id !== undefined;
  const { authFetch } = useAuth();
  const navigate = useNavigate();
  const requestId = useRef(newRequestId());
  const [areas, setAreas] = useState<Area[]>([]);
  const [existing, setExisting] = useState<ClaimDetail | null>(null);
  const [date, setDate] = useState(todayIso());
  const [amount, setAmount] = useState("");
  const [category, setCategory] = useState<Category>("TRAVEL_FUEL");
  const [area, setArea] = useState(HEAD_OFFICE);
  const [note, setNote] = useState("");
  const [paidBy, setPaidBy] = useState<"CLAIMANT" | "COMPANY">("CLAIMANT");
  const [reason, setReason] = useState("");
  const [noBill, setNoBill] = useState("");
  const [link, setLink] = useState("");
  const [options, setOptions] = useState<LinkOption[]>([]);
  const [files, setFiles] = useState<File[]>([]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [duplicate, setDuplicate] = useState(false);

  useEffect(() => {
    (async () => {
      const result = await getAreas(authFetch);
      if (result.ok) setAreas(result.data);
    })();
  }, [authFetch]);

  useEffect(() => {
    if (!editing) return;
    (async () => {
      const result = await getMine(authFetch, id);
      if (!result.ok) {
        setFormError(result.reason);
        return;
      }
      const c = result.data;
      setExisting(c);
      setDate(c.expenseDate);
      setAmount(c.amount);
      setCategory(c.category);
      setArea(c.zoneId ?? HEAD_OFFICE);
      setNote(c.note ?? "");
      setPaidBy(c.paidBy);
      setReason(c.reason ?? "");
      setNoBill(c.noBillReason ?? "");
      setLink(c.link ? `${c.link.kind}|${c.link.ref}` : "");
    })();
  }, [authFetch, editing, id]);

  const takesLink = category === "TRAVEL_FUEL" || category === "TRAINING_STAY";
  useEffect(() => {
    if (!takesLink || date === "") {
      // eslint-disable-next-line react-hooks/set-state-in-effect -- clears the choices when the category takes no link
      setOptions([]);
      return;
    }
    (async () => {
      const result = await getLinkOptions(authFetch, category, date);
      setOptions(result.ok ? result.data : []);
    })();
  }, [authFetch, category, date, takesLink]);

  const liveBills = useMemo(
    () => (existing ? existing.bills.filter((b) => !b.removed).length : 0),
    [existing],
  );

  function addFiles(list: FileList | null) {
    if (!list) return;
    const next: File[] = [];
    let problem: string | null = null;
    for (const file of Array.from(list)) {
      const why = billProblem(file);
      if (why) {
        problem = `${file.name}: ${why}`;
      } else if (liveBills + files.length + next.length >= MAX_BILLS) {
        problem = `A claim can have at most ${MAX_BILLS} bills.`;
      } else {
        next.push(file);
      }
    }
    setFiles((prev) => [...prev, ...next]);
    setErrors((prev) => {
      const copy = { ...prev };
      if (problem) copy.bills = problem;
      else delete copy.bills;
      return copy;
    });
  }

  /** The rules a submission enforces, named the way the server names them. */
  function submissionProblems(): Record<string, string> {
    const found: Record<string, string> = {};
    const today = todayIso();
    if (date > today) found.date = "The expense date cannot be in the future.";
    else if (date < shift(today, -60)) found.date = "The expense date is older than 60 days.";
    if (category === "OTHER" && reason.trim() === "") found.reason = "A reason is required for the category Other.";
    if (category === "TRAINING_STAY" && link === "") found.link = "Choose the induction batch for a Training Stay claim.";
    if (liveBills + files.length === 0 && noBill.trim() === "") {
      found.bills = "Attach a bill, or give the reason there is no bill.";
    }
    return found;
  }

  function draftProblems(): Record<string, string> {
    const found: Record<string, string> = {};
    if (date === "") found.date = "The expense date is required.";
    const n = Number(amount);
    if (amount.trim() === "" || Number.isNaN(n) || n <= 0) found.amount = "The amount must be more than zero.";
    else if (!/^\d+(\.\d{1,2})?$/.test(amount.trim())) found.amount = "The amount can have at most two decimal places.";
    return found;
  }

  function body(extra: Partial<ClaimBody>): ClaimBody {
    const [kind, ref] = link.split("|");
    return {
      clientRequestId: editing ? undefined : requestId.current,
      expenseDate: date,
      amount: amount.trim(),
      category,
      zoneId: area === HEAD_OFFICE ? null : area,
      note: note.trim() || null,
      paidBy,
      reason: reason.trim() || null,
      noBillReason: noBill.trim() || null,
      link: takesLink && link ? { kind, ref } : null,
      ...extra,
    };
  }

  async function save(submit: boolean, confirmDuplicate = false) {
    const found = { ...draftProblems(), ...(submit ? submissionProblems() : {}) };
    setErrors(found);
    setFormError(null);
    if (Object.keys(found).length > 0) return;
    setBusy(true);
    let claim: ClaimDetail;
    const saved = existing
      ? await updateClaim(authFetch, existing.id, body({ version: existing.version }))
      : await createClaim(authFetch, body({}));
    if (!saved.ok) {
      setBusy(false);
      setFormError(saved.reason);
      return;
    }
    claim = saved.data;
    setExisting(claim);
    for (const file of files) {
      const uploaded = await uploadBill(authFetch, claim.id, file);
      if (!uploaded.ok) {
        setBusy(false);
        setFormError(`${file.name}: ${uploaded.reason}`);
        setFiles((prev) => prev.filter((f) => f !== file));
        return;
      }
      claim = uploaded.data;
      setExisting(claim);
    }
    setFiles([]);
    if (!submit) {
      setBusy(false);
      navigate(`${MY_EXPENSES_ROUTE}/${claim.id}`);
      return;
    }
    const sent = await submitClaim(authFetch, claim.id, claim.version, confirmDuplicate);
    setBusy(false);
    if (sent.ok) {
      navigate(`${MY_EXPENSES_ROUTE}/${claim.id}`);
    } else if (sent.status === 409 && /same date, amount and category/.test(sent.reason)) {
      setDuplicate(true);
    } else {
      setFormError(sent.reason);
    }
  }

  const linkLabel = category === "TRAINING_STAY" ? "Induction batch" : "Linked work (optional)";

  return (
    <Box component="form" noValidate onSubmit={(e) => e.preventDefault()}>
      <Link component={RouterLink} to={MY_EXPENSES_ROUTE} underline="hover">
        Back to My Expenses
      </Link>
      <Typography variant="h5" component="h1" sx={{ my: 2 }}>
        {editing ? "Edit claim" : "New claim"}
      </Typography>
      {formError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {formError}
        </Alert>
      )}
      <Stack spacing={2} sx={{ maxWidth: 560 }}>
        <TextField
          label="Expense date"
          type="date"
          value={date}
          onChange={(e) => setDate(e.target.value)}
          required
          error={Boolean(errors.date)}
          helperText={errors.date}
          slotProps={{ inputLabel: { shrink: true } }}
        />
        <TextField
          label="Amount (rupees)"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          required
          error={Boolean(errors.amount)}
          helperText={errors.amount ?? "Up to two decimals, for example 850.00"}
          slotProps={{ htmlInput: { inputMode: "decimal" } }}
        />
        <TextField
          select
          label="Category"
          value={category}
          onChange={(e) => {
            setCategory(e.target.value as Category);
            setLink("");
          }}
        >
          {(Object.keys(CATEGORY_LABELS) as Category[]).map((c) => (
            <MenuItem key={c} value={c}>
              {CATEGORY_LABELS[c]}
            </MenuItem>
          ))}
        </TextField>
        <TextField select label="Area" value={area} onChange={(e) => setArea(e.target.value)}>
          <MenuItem value={HEAD_OFFICE}>Head office</MenuItem>
          {areas
            .filter((a) => a.id !== null)
            .map((a) => (
              <MenuItem key={a.id} value={a.id as string}>
                {a.name}
              </MenuItem>
            ))}
        </TextField>
        <TextField
          select
          label="Paid by"
          value={paidBy}
          onChange={(e) => setPaidBy(e.target.value as "CLAIMANT" | "COMPANY")}
        >
          <MenuItem value="CLAIMANT">I paid it myself</MenuItem>
          <MenuItem value="COMPANY">The company paid it</MenuItem>
        </TextField>
        <TextField
          label="Note"
          value={note}
          onChange={(e) => setNote(e.target.value)}
          multiline
          minRows={2}
          slotProps={{ htmlInput: { maxLength: 1000 } }}
        />
        {category === "OTHER" && (
          <TextField
            label="Reason for the category Other"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
            error={Boolean(errors.reason)}
            helperText={errors.reason}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        )}
        {takesLink && (
          <TextField
            select
            label={linkLabel}
            value={link}
            onChange={(e) => setLink(e.target.value)}
            required={category === "TRAINING_STAY"}
            error={Boolean(errors.link)}
            helperText={errors.link}
          >
            {category === "TRAVEL_FUEL" && <MenuItem value="">Not linked</MenuItem>}
            {options.map((o) => (
              <MenuItem key={`${o.kind}|${o.ref}`} value={`${o.kind}|${o.ref}`}>
                {o.label} ({formatDate(o.date)})
              </MenuItem>
            ))}
            {existing?.link && !options.some((o) => o.ref === existing.link!.ref) && (
              <MenuItem value={`${existing.link.kind}|${existing.link.ref}`}>
                {existing.link.label} (kept as submitted)
              </MenuItem>
            )}
          </TextField>
        )}

        <Box>
          <Typography variant="subtitle1" component="h2">
            Bills
          </Typography>
          <Typography variant="body2" color="text.secondary">
            jpg, png or pdf, at most 10 MB each, up to {MAX_BILLS} bills.
          </Typography>
          {existing && existing.bills.length > 0 && (
            <List dense>
              {existing.bills
                .filter((b) => !b.removed)
                .map((b) => (
                  <ListItem key={b.id}>
                    <ListItemText primary={b.name} secondary={`Added by ${b.addedByName}`} />
                  </ListItem>
                ))}
            </List>
          )}
          {files.length > 0 && (
            <List dense aria-label="Bills to upload">
              {files.map((f) => (
                <ListItem
                  key={f.name + f.size}
                  secondaryAction={
                    <Button size="small" onClick={() => setFiles((prev) => prev.filter((x) => x !== f))}>
                      Remove {f.name}
                    </Button>
                  }
                >
                  <ListItemText primary={f.name} />
                </ListItem>
              ))}
            </List>
          )}
          <Button component="label" variant="outlined">
            Add a bill
            <input
              hidden
              type="file"
              multiple
              accept=".jpg,.jpeg,.png,.pdf"
              aria-label="Add a bill"
              onChange={(e) => {
                addFiles(e.target.files);
                e.target.value = "";
              }}
            />
          </Button>
          {errors.bills && (
            <Typography role="alert" color="error" variant="body2" sx={{ mt: 1 }}>
              {errors.bills}
            </Typography>
          )}
        </Box>
        <TextField
          label="Reason there is no bill"
          value={noBill}
          onChange={(e) => setNoBill(e.target.value)}
          helperText="Only when no bill exists, for example a toll with no receipt."
          slotProps={{ htmlInput: { maxLength: 500 } }}
        />

        <Stack direction="row" spacing={1}>
          <Button variant="outlined" disabled={busy} onClick={() => void save(false)}>
            Save draft
          </Button>
          <Button variant="contained" disabled={busy} onClick={() => void save(true)}>
            Submit
          </Button>
        </Stack>
      </Stack>

      {duplicate && (
        <Dialog open onClose={() => setDuplicate(false)} aria-labelledby="duplicate-title">
          <DialogTitle id="duplicate-title">This looks like a claim you already made</DialogTitle>
          <DialogContent>
            <DialogContentText>
              You already have a claim with the same date, amount and category. Submit this one as well?
            </DialogContentText>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDuplicate(false)}>Cancel</Button>
            <Button
              variant="contained"
              onClick={() => {
                setDuplicate(false);
                void save(true, true);
              }}
            >
              Submit anyway
            </Button>
          </DialogActions>
        </Dialog>
      )}
    </Box>
  );
}

/** The date `days` from an ISO date, as an ISO date. */
function shift(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  const date = new Date(y, m - 1, d + days);
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}
