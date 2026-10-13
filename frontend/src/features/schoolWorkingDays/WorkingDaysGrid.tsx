import { useState } from "react";
import {
  DataGrid,
  type GridColDef,
  type GridPaginationModel,
  type GridSortModel,
} from "@mui/x-data-grid";
import {
  Box,
  Button,
  Chip,
  IconButton,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import HistoryOutlined from "@mui/icons-material/HistoryOutlined";
import { RowActionButton } from "../common/RowActionButton";
import type { WorkingDaysRow } from "./schoolWorkingDaysApi";
import {
  FINALISED_NOTICE,
  figureProblem,
  formatFigure,
  formatWhen,
  monthName,
} from "./workingDaysRules";

interface WorkingDaysGridProps {
  rows: WorkingDaysRow[];
  total: number;
  loading: boolean;
  paging: GridPaginationModel;
  onPagingChange: (model: GridPaginationModel) => void;
  sortModel: GridSortModel;
  onSortChange: (model: GridSortModel) => void;
  canCreate: boolean;
  canEdit: boolean;
  canDelete: boolean;
  /** The row being edited in place, if any. */
  editingId: string | null;
  onStartEdit: (row: WorkingDaysRow) => void;
  onCancelEdit: () => void;
  /** Saves the typed figure; resolves to a message to show under the cell, or null when saved. */
  onSaveEdit: (row: WorkingDaysRow, text: string) => Promise<string | null>;
  onDelete: (row: WorkingDaysRow) => void;
  onHistory: (row: WorkingDaysRow) => void;
}

/** The data grid of the School Working Days screen: sorting, paging and in-place editing of the figure. */
export function WorkingDaysGrid({
  rows,
  total,
  loading,
  paging,
  onPagingChange,
  sortModel,
  onSortChange,
  canCreate,
  canEdit,
  canDelete,
  editingId,
  onStartEdit,
  onCancelEdit,
  onSaveEdit,
  onDelete,
  onHistory,
}: WorkingDaysGridProps) {
  const [draft, setDraft] = useState("");
  const [problem, setProblem] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [draftFor, setDraftFor] = useState<string | null>(null);

  // when a different row starts being edited, start its entry from the stored figure; a refreshed version of the
  // same row keeps what the user typed
  if (draftFor !== editingId) {
    const editing = rows.find((r) => r.id === editingId);
    setDraftFor(editingId);
    setDraft(editing ? formatFigure(editing.workingDays) : "");
    setProblem(null);
  }

  const save = async (row: WorkingDaysRow) => {
    const invalid = figureProblem(draft, row.year, row.month);
    if (invalid) {
      setProblem(invalid);
      return;
    }
    setSaving(true);
    const message = await onSaveEdit(row, draft.trim());
    setSaving(false);
    setProblem(message);
  };

  const columns: GridColDef<WorkingDaysRow>[] = [
    { field: "schoolName", headerName: "School", flex: 1.4, minWidth: 150 },
    { field: "zoneName", headerName: "Zone", flex: 0.9, minWidth: 100 },
    {
      field: "month",
      headerName: "Month",
      flex: 0.9,
      minWidth: 110,
      valueGetter: (_value, row) => `${monthName(row.month)} ${row.year}`,
    },
    {
      field: "workingDays",
      headerName: "Working days",
      flex: 1.2,
      minWidth: 150,
      renderCell: (params) => {
        const row = params.row;
        if (row.id !== editingId) return formatFigure(row.workingDays);
        return (
          <TextField
            size="small"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              e.stopPropagation();
              if (e.key === "Enter") void save(row);
              if (e.key === "Escape") onCancelEdit();
            }}
            error={Boolean(problem)}
            helperText={problem ?? undefined}
            autoFocus
            sx={{ my: 0.5, maxWidth: 140 }}
            slotProps={{
              htmlInput: {
                "aria-label": `Working days for ${row.schoolName} ${monthName(row.month)} ${row.year}`,
                inputMode: "decimal",
              },
            }}
          />
        );
      },
    },
    {
      field: "daysInMonth",
      headerName: "Days in month",
      flex: 0.7,
      minWidth: 90,
      sortable: false,
    },
    {
      field: "calendarDerived",
      headerName: "Calendar count",
      flex: 0.9,
      minWidth: 110,
      sortable: false,
      renderCell: (params) => (
        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
          <span>{params.row.calendarDerived}</span>
          {params.row.calendarDerived !== params.row.workingDays && (
            <Tooltip title="The stored figure differs from the count the working calendar gives">
              <Chip size="small" label="differs" />
            </Tooltip>
          )}
        </Stack>
      ),
    },
    {
      field: "updatedAt",
      headerName: "Last changed",
      flex: 1.3,
      minWidth: 150,
      renderCell: (params) => (
        <span>
          {params.row.updatedBy}, {formatWhen(params.row.updatedAt)}
        </span>
      ),
    },
    {
      field: "actions",
      headerName: "Actions",
      flex: 1.6,
      minWidth: 170,
      sortable: false,
      renderCell: (params) => {
        const row = params.row;
        const subject = `${row.schoolName} ${monthName(row.month)} ${row.year}`;
        if (row.id === editingId) {
          return (
            <Stack spacing={0.5} sx={{ py: 0.5 }}>
              <Stack direction="row" spacing={1}>
                <Button
                  size="small"
                  variant="contained"
                  onClick={() => void save(row)}
                  disabled={saving}
                >
                  Save
                </Button>
                <Button size="small" onClick={onCancelEdit}>
                  Cancel
                </Button>
              </Stack>
              <Typography variant="caption">{FINALISED_NOTICE}</Typography>
            </Stack>
          );
        }
        return (
          <Stack direction="row" spacing={0.5}>
            {canEdit && (
              <RowActionButton
                action="Edit"
                subject={subject}
                onClick={() => onStartEdit(row)}
              />
            )}
            <Tooltip title="History">
              <IconButton
                size="small"
                color="primary"
                aria-label={`History ${subject}`}
                onClick={() => onHistory(row)}
              >
                <HistoryOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
            {canDelete && (
              <RowActionButton
                action="Delete"
                subject={subject}
                onClick={() => onDelete(row)}
              />
            )}
          </Stack>
        );
      },
    },
  ];

  return (
    <Box sx={{ width: "100%", maxWidth: "100%", bgcolor: "background.paper" }}>
      <DataGrid
        rows={rows}
        columns={columns}
        loading={loading}
        rowCount={total}
        paginationMode="server"
        sortingMode="server"
        paginationModel={paging}
        onPaginationModelChange={onPagingChange}
        sortModel={sortModel}
        onSortModelChange={onSortChange}
        pageSizeOptions={[25, 50, 100]}
        disableRowSelectionOnClick
        disableColumnFilter
        disableColumnMenu
        getRowHeight={() => "auto"}
        autoHeight
        localeText={{
          noRowsLabel: canCreate
            ? "No working days entered for this year. Use Add working days to enter the first one."
            : "No working days entered for this year.",
        }}
      />
    </Box>
  );
}
