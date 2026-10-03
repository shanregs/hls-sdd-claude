import {
  Box,
  Button,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import type { DayView } from "./attendanceApi";
import { describeDay } from "./dayText";
import { formatDate, WEEKDAY_LABELS, weekdayIndex } from "./monthUtils";

interface MonthCalendarProps {
  days: DayView[];
  locked: boolean;
  /** Called with the day when an editable day is chosen. */
  onSelect?: (day: DayView) => void;
  /** Which editors may act; a day is clickable only when its editableBy is in this set. */
  editor?: "SELF" | "SUPERVISOR";
}

/** A Monday-first month calendar (spec 008 FR-014). Editable days are buttons with full names. */
export function MonthCalendar({
  days,
  locked,
  onSelect,
  editor = "SELF",
}: MonthCalendarProps) {
  if (days.length === 0) return null;
  const lead = weekdayIndex(days[0].date);
  const cells: (DayView | null)[] = [...Array(lead).fill(null), ...days];
  while (cells.length % 7 !== 0) cells.push(null);
  const weeks: (DayView | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));

  return (
    <TableContainer>
      <Table
        size="small"
        aria-label="Attendance calendar"
        sx={{ tableLayout: "fixed" }}
      >
        <TableHead>
          <TableRow>
            {WEEKDAY_LABELS.map((label) => (
              <TableCell key={label} align="center" scope="col">
                {label}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {weeks.map((week, w) => (
            <TableRow key={w}>
              {week.map((day, i) => (
                <TableCell
                  key={i}
                  align="center"
                  sx={{ verticalAlign: "top", minWidth: 64, p: 0.5 }}
                >
                  {day && (
                    <DayCell
                      day={day}
                      clickable={
                        !locked && day.editableBy === editor && !!onSelect
                      }
                      onSelect={onSelect}
                    />
                  )}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function DayCell({
  day,
  clickable,
  onSelect,
}: {
  day: DayView;
  clickable: boolean;
  onSelect?: (day: DayView) => void;
}) {
  const number = Number(day.date.slice(8));
  const label = describeDay(day);
  const code =
    day.state === "MARKED"
      ? `${day.mark!.code}${day.mark!.dayValue < 1 ? " ½" : ""}`
      : "";
  const content = (
    <Box>
      <Typography variant="caption" component="div">
        {number}
      </Typography>
      <Typography variant="body2" component="div" sx={{ fontWeight: 700 }}>
        {code || " "}
      </Typography>
      <Typography variant="caption" component="div" color="text.secondary">
        {day.state === "MARKED" && day.mark!.setByKind === "SUPERVISOR"
          ? `by ${day.mark!.setByName}`
          : day.state === "MARKED"
            ? ""
            : label}
      </Typography>
    </Box>
  );
  if (clickable) {
    return (
      <Button
        onClick={() => onSelect?.(day)}
        aria-label={`${formatDate(day.date)}: ${label}`}
        sx={{ width: "100%", minWidth: 0, p: 0.5, textTransform: "none" }}
      >
        {content}
      </Button>
    );
  }
  return (
    <Box
      role="group"
      aria-label={`${formatDate(day.date)}: ${label}`}
      sx={{ p: 0.5 }}
    >
      {content}
    </Box>
  );
}
