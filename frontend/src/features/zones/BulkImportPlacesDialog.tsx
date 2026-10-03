import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  List,
  ListItem,
  ListItemText,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { sendJson } from "../common/masterDataApi";
import { parseRows } from "./bulkImportRows";
import type { ZoneSummary } from "./zonesApi";

const MAX_ROWS = 5000;

interface RowResult {
  row: number;
  outcome: "ADDED" | "ALREADY_EXISTS" | "REJECTED";
  reason: string | null;
}

interface ImportReport {
  added: number;
  alreadyExisted: number;
  rejected: number;
  results: RowResult[];
}

interface BulkImportPlacesDialogProps {
  zone: ZoneSummary;
  onClose: () => void;
  onDone: () => void;
}

/** Pastes many Places into a Zone (FR-003) and shows the per-row report. */
export function BulkImportPlacesDialog({
  zone,
  onClose,
  onDone,
}: BulkImportPlacesDialogProps) {
  const { authFetch } = useAuth();
  const [text, setText] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [saving, setSaving] = useState(false);

  const submit = async () => {
    const rows = parseRows(text);
    if (rows.length === 0) {
      setError("Add at least one line: name,pinCode.");
      return;
    }
    if (rows.length > MAX_ROWS) {
      setError(
        `The list has more than ${MAX_ROWS} rows. Split it and import in parts.`,
      );
      return;
    }
    setError(null);
    setSaving(true);
    const result = await sendJson<ImportReport>(
      authFetch,
      "POST",
      "/api/v1/places/bulk-import",
      { zoneId: zone.id, rows },
    );
    setSaving(false);
    if (!result.ok) {
      setError(result.reason);
      return;
    }
    setReport(result.data);
  };

  const problems = report?.results.filter((r) => r.outcome !== "ADDED") ?? [];

  return (
    <Dialog open onClose={report ? onDone : onClose} fullWidth maxWidth="sm">
      <DialogTitle>Import places into {zone.name}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          {!report && (
            <TextField
              label="Places (one per line: name,pinCode)"
              multiline
              minRows={6}
              value={text}
              onChange={(e) => setText(e.target.value)}
              helperText="Example: Madurantakam,603306. A repeat of a place already in this zone is skipped."
            />
          )}
          {report && (
            <>
              <Alert severity="success" role="status">
                {report.added} added, {report.alreadyExisted} already existed,{" "}
                {report.rejected} rejected.
              </Alert>
              {problems.length > 0 && (
                <>
                  <Typography variant="subtitle2">Rows not added</Typography>
                  <List dense>
                    {problems.map((r) => (
                      <ListItem key={r.row}>
                        <ListItemText
                          primary={`Row ${r.row}: ${
                            r.outcome === "ALREADY_EXISTS"
                              ? "already exists"
                              : (r.reason ?? "rejected")
                          }`}
                        />
                      </ListItem>
                    ))}
                  </List>
                </>
              )}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        {report ? (
          <Button variant="contained" onClick={onDone}>
            Done
          </Button>
        ) : (
          <>
            <Button onClick={onClose}>Cancel</Button>
            <Button variant="contained" onClick={submit} disabled={saving}>
              Import
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
