import { useRef, useState, type KeyboardEvent } from "react";
import {
  Box,
  Button,
  Link,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import type { GridCell, GridRow } from "./attendanceApi";
import { describeCell } from "./dayText";
import { formatDate } from "./monthUtils";

function shortText(cell: GridCell): string {
  switch (cell.state) {
    case "MARKED":
      return `${cell.code}${(cell.dayValue ?? 1) < 1 ? "½" : ""}`;
    case "UNMARKED":
      return "?";
    case "NOT_PLACED":
      return "–";
    case "WEEKLY_OFF":
      return "Off";
    case "NON_WORKING":
      return "Hol";
    case "FUTURE":
      return "";
  }
}

interface AttendanceGridProps {
  rows: GridRow[];
  /** Cells in a locked row, or without a placement, are never editable. */
  canEdit: boolean;
  onCell: (row: GridRow, cell: GridCell) => void;
  onOpenTeacher: (row: GridRow) => void;
}

function editable(row: GridRow, cell: GridCell, canEdit: boolean): boolean {
  return (
    canEdit &&
    !row.locked &&
    cell.state !== "NOT_PLACED" &&
    cell.state !== "FUTURE"
  );
}

/**
 * The month grid (spec 008 FR-013): one row per Teacher, one column per real day of the month, a
 * sticky Teacher column, and a rollup. Editable cells are buttons with full accessible names, and
 * arrow keys move between them.
 */
export function AttendanceGrid({
  rows,
  canEdit,
  onCell,
  onOpenTeacher,
}: AttendanceGridProps) {
  const [active, setActive] = useState<{ r: number; c: number }>({
    r: 0,
    c: 0,
  });
  const tableRef = useRef<HTMLTableElement>(null);
  const days = rows[0]?.cells.length ?? 0;

  // One tab stop for the whole grid: the active cell, or the first editable one when it is not editable.
  const activeRow = rows[active.r];
  const activeCell = activeRow?.cells[active.c];
  const activeEditable =
    !!activeRow && !!activeCell && editable(activeRow, activeCell, canEdit);
  let tabTarget = active;
  if (!activeEditable) {
    outer: for (let r = 0; r < rows.length; r++) {
      for (let c = 0; c < rows[r].cells.length; c++) {
        if (editable(rows[r], rows[r].cells[c], canEdit)) {
          tabTarget = { r, c };
          break outer;
        }
      }
    }
  }

  const focusCell = (r: number, c: number) => {
    const el = tableRef.current?.querySelector<HTMLButtonElement>(
      `button[data-r="${r}"][data-c="${c}"]`,
    );
    if (el) {
      setActive({ r, c });
      el.focus();
    }
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTableElement>) => {
    const target = event.target as HTMLElement;
    const r = Number(target.dataset.r);
    const c = Number(target.dataset.c);
    if (Number.isNaN(r) || Number.isNaN(c)) return;
    const steps: Record<string, [number, number]> = {
      ArrowRight: [0, 1],
      ArrowLeft: [0, -1],
      ArrowDown: [1, 0],
      ArrowUp: [-1, 0],
    };
    const step = steps[event.key];
    if (!step) return;
    event.preventDefault();
    let nr = r + step[0];
    let nc = c + step[1];
    while (nr >= 0 && nr < rows.length && nc >= 0 && nc < days) {
      if (
        tableRef.current?.querySelector(
          `button[data-r="${nr}"][data-c="${nc}"]`,
        )
      ) {
        focusCell(nr, nc);
        return;
      }
      nr += step[0];
      nc += step[1];
    }
  };

  return (
    <Paper variant="outlined">
      <TableContainer sx={{ maxHeight: "70vh" }}>
        <Table
          size="small"
          stickyHeader
          ref={tableRef}
          onKeyDown={onKeyDown}
          aria-label="Attendance grid"
        >
          <TableHead>
            <TableRow>
              <TableCell
                sx={{ position: "sticky", left: 0, zIndex: 3, minWidth: 180 }}
              >
                Teacher
              </TableCell>
              {Array.from({ length: days }, (_, i) => (
                <TableCell
                  key={i}
                  align="center"
                  sx={{ px: 0.5, minWidth: 34 }}
                >
                  {i + 1}
                </TableCell>
              ))}
              <TableCell align="right">Worked</TableCell>
              <TableCell align="right">Unmarked</TableCell>
              <TableCell align="right">Total</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {rows.map((row, r) => (
              <TableRow key={row.teacherId} hover>
                <TableCell
                  component="th"
                  scope="row"
                  sx={{
                    position: "sticky",
                    left: 0,
                    zIndex: 2,
                    bgcolor: "background.paper",
                  }}
                >
                  <Link
                    component="button"
                    type="button"
                    onClick={() => onOpenTeacher(row)}
                    underline="hover"
                    sx={{ textAlign: "left" }}
                  >
                    {row.name}
                  </Link>
                  <Typography
                    variant="caption"
                    component="div"
                    color="text.secondary"
                  >
                    {row.school?.name ?? "Not placed"}
                    {row.locked ? " · Locked" : ""}
                  </Typography>
                </TableCell>
                {row.cells.map((cell, c) => {
                  const label = `${row.name}, ${formatDate(cell.date)}: ${describeCell(cell)}`;
                  const text = shortText(cell);
                  return (
                    <TableCell key={c} align="center" sx={{ p: 0 }}>
                      {editable(row, cell, canEdit) ? (
                        <Button
                          size="small"
                          data-r={r}
                          data-c={c}
                          tabIndex={
                            tabTarget.r === r && tabTarget.c === c ? 0 : -1
                          }
                          aria-label={label}
                          onFocus={() => setActive({ r, c })}
                          onClick={() => onCell(row, cell)}
                          sx={{ minWidth: 34, p: 0.25, textTransform: "none" }}
                        >
                          {text}
                        </Button>
                      ) : (
                        <Box
                          role="img"
                          aria-label={label}
                          sx={{
                            minWidth: 34,
                            py: 0.5,
                            color: "text.secondary",
                          }}
                        >
                          <Typography variant="caption">{text}</Typography>
                        </Box>
                      )}
                    </TableCell>
                  );
                })}
                <TableCell align="right">{row.rollup.daysWorked}</TableCell>
                <TableCell align="right">{row.rollup.unmarked}</TableCell>
                <TableCell align="right">{row.rollup.weightedTotal}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Paper>
  );
}
