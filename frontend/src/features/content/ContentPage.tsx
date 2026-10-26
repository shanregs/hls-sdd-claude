import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useNavigate, useSearchParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  MenuItem,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tabs,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useAccessModel } from "../../access-model/useAccessModel";
import { formatDate } from "../teachers/formatters";
import {
  CONTENT_ROUTE,
  STATUS_LABELS,
  createContent,
  getAuthors,
  getSummary,
  listContent,
  type ContentAuthor,
  type ContentRow,
  type ContentStatus,
  type ContentSummary,
} from "./contentApi";

const TABS: { status: ContentStatus | ""; label: string; count?: keyof ContentSummary }[] = [
  { status: "", label: "All" },
  { status: "DRAFT", label: "Draft", count: "draft" },
  { status: "ASSIGNED", label: "Assigned", count: "assigned" },
  { status: "SUBMITTED", label: "Submitted", count: "submitted" },
  { status: "RETURNED", label: "Returned", count: "returned" },
  { status: "APPROVED", label: "Approved", count: "approved" },
];

type Rows =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; rows: ContentRow[]; total: number };

/**
 * OPERATIONS -> Content (Admin, Director) or My Content (Manager, Content Contributor), spec 029: the content list by status
 * with counts, filters by author, category and subject for management, and a form to start new content.
 */
export function ContentPage() {
  const { authFetch } = useAuth();
  const { accessModel } = useAccessModel();
  const management = Boolean(accessModel?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR"));
  const [params] = useSearchParams();
  const [status, setStatus] = useState<ContentStatus | "">(() => {
    const wanted = params.get("status");
    return wanted !== null && wanted in STATUS_LABELS ? (wanted as ContentStatus) : "";
  });
  const [author, setAuthor] = useState("");
  const [category, setCategory] = useState("");
  const [subject, setSubject] = useState("");
  const [rows, setRows] = useState<Rows>({ kind: "loading" });
  const [summary, setSummary] = useState<ContentSummary | null>(null);
  const [authors, setAuthors] = useState<ContentAuthor[]>([]);
  const [creating, setCreating] = useState(false);
  const navigate = useNavigate();

  const load = useCallback(async () => {
    setRows({ kind: "loading" });
    const [list, counts] = await Promise.all([
      listContent(authFetch, {
        status: status || undefined,
        authorUserId: author || undefined,
        category: category || undefined,
        subject: subject || undefined,
        size: 100,
      }),
      getSummary(authFetch),
    ]);
    if (counts.ok) setSummary(counts.data);
    setRows(
      list.ok
        ? { kind: "ready", rows: list.data.content, total: list.data.totalElements }
        : { kind: "error", reason: list.reason },
    );
  }, [authFetch, status, author, category, subject]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial and filter-driven fetch
    void load();
  }, [load]);

  useEffect(() => {
    void getAuthors(authFetch).then((result) => {
      if (result.ok) setAuthors(result.data);
    });
  }, [authFetch]);

  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ mb: 2, justifyContent: "space-between", alignItems: "center", flexWrap: "wrap" }}>
        <Typography variant="h5" component="h1">
          {management ? "Content" : "My Content"}
        </Typography>
        <Button variant="contained" onClick={() => setCreating(true)}>
          New content
        </Button>
      </Stack>

      <Tabs
        value={status}
        onChange={(_e, value: ContentStatus | "") => setStatus(value)}
        variant="scrollable"
        scrollButtons="auto"
        aria-label="Content status"
        sx={{ mb: 2 }}
      >
        {TABS.map((tab) => (
          <Tab
            key={tab.label}
            value={tab.status}
            label={tab.count && summary ? `${tab.label} (${summary[tab.count]})` : tab.label}
          />
        ))}
      </Tabs>
      {summary && summary.overdue > 0 && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {summary.overdue} piece{summary.overdue === 1 ? " is" : "s are"} past the due date with nothing submitted.
        </Alert>
      )}

      {management && (
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2} sx={{ mb: 2 }}>
          <TextField select size="small" label="Author" value={author} onChange={(e) => setAuthor(e.target.value)} sx={{ minWidth: 200 }}>
            <MenuItem value="">All authors</MenuItem>
            {authors.map((a) => (
              <MenuItem key={a.userId} value={a.userId}>
                {a.displayName}
              </MenuItem>
            ))}
          </TextField>
          <TextField size="small" label="Category" value={category} onChange={(e) => setCategory(e.target.value)} />
          <TextField size="small" label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} />
        </Stack>
      )}

      {rows.kind === "loading" && <CircularProgress aria-label="Loading content" />}
      {rows.kind === "error" && (
        <Alert severity="error" role="alert">
          {rows.reason}
        </Alert>
      )}
      {rows.kind === "ready" && rows.rows.length === 0 && (
        <Typography color="text.secondary">No content here yet.</Typography>
      )}
      {rows.kind === "ready" && rows.rows.length > 0 && (
        <TableContainer>
          <Table size="small" aria-label="Content">
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Title</TableCell>
                <TableCell>Category</TableCell>
                <TableCell>Subject</TableCell>
                <TableCell>Author</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Version</TableCell>
                <TableCell>Due</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.rows.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>{row.code}</TableCell>
                  <TableCell>
                    <Link component={RouterLink} to={`${CONTENT_ROUTE}/${row.id}`}>
                      {row.title}
                    </Link>
                  </TableCell>
                  <TableCell>{row.category}</TableCell>
                  <TableCell>{row.subject}</TableCell>
                  <TableCell>{row.authorName}</TableCell>
                  <TableCell>
                    <Chip size="small" label={row.statusLabel} />
                    {row.overdue && <Chip size="small" color="warning" label="Overdue" sx={{ ml: 0.5 }} />}
                  </TableCell>
                  <TableCell>{row.currentVersion === 0 ? "None" : row.currentVersion}</TableCell>
                  <TableCell>{row.dueOn ? formatDate(row.dueOn) : ""}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      {creating && (
        <CreateDialog
          management={management}
          authors={authors}
          onClose={() => setCreating(false)}
          onCreated={(id) => navigate(`${CONTENT_ROUTE}/${id}`)}
        />
      )}
    </Box>
  );
}

function CreateDialog({
  management,
  authors,
  onClose,
  onCreated,
}: {
  management: boolean;
  authors: ContentAuthor[];
  onClose: () => void;
  onCreated: (id: string) => void;
}) {
  const { authFetch } = useAuth();
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("");
  const [subject, setSubject] = useState("");
  const [description, setDescription] = useState("");
  const [authorUserId, setAuthorUserId] = useState("");
  const [dueOn, setDueOn] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    setError("");
    const result = await createContent(authFetch, {
      title,
      category,
      subject,
      description,
      authorUserId: authorUserId || undefined,
      dueOn: dueOn || undefined,
    });
    setSaving(false);
    if (result.ok) onCreated(result.data.id);
    else setError(result.reason);
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-labelledby="new-content-title">
      <DialogTitle id="new-content-title">New content</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {error && (
            <Alert severity="error" role="alert">
              {error}
            </Alert>
          )}
          <TextField label="Title" value={title} onChange={(e) => setTitle(e.target.value)} required slotProps={{ htmlInput: { maxLength: 120 } }} />
          <TextField label="Category" value={category} onChange={(e) => setCategory(e.target.value)} required slotProps={{ htmlInput: { maxLength: 60 } }} />
          <TextField label="Subject" value={subject} onChange={(e) => setSubject(e.target.value)} required slotProps={{ htmlInput: { maxLength: 80 } }} />
          <TextField
            label="What is wanted"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            multiline
            minRows={2}
            slotProps={{ htmlInput: { maxLength: 2000 } }}
          />
          {management && (
            <TextField select label="Author" value={authorUserId} onChange={(e) => setAuthorUserId(e.target.value)} helperText="Leave empty to write it yourself.">
              <MenuItem value="">Myself</MenuItem>
              {authors.map((a) => (
                <MenuItem key={a.userId} value={a.userId}>
                  {a.displayName}
                </MenuItem>
              ))}
            </TextField>
          )}
          <TextField
            label="Due date (optional)"
            type="date"
            value={dueOn}
            onChange={(e) => setDueOn(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
            helperText="A Manager author with a due date also gets a task."
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={() => void save()} disabled={saving || title.trim().length < 3 || !category.trim() || !subject.trim()}>
          Create
        </Button>
      </DialogActions>
    </Dialog>
  );
}
