import { useState } from "react";
import {
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Typography,
} from "@mui/material";
import { formatDate } from "../teachers/formatters";
import {
  KIND_LABELS,
  STATUS_LABELS,
  type CalendarItem,
  type CalendarStatus,
} from "./activityCalendarApi";

const STATUS_COLOR: Record<
  CalendarStatus,
  "default" | "success" | "error" | "info"
> = {
  PLANNED: "info",
  DONE: "success",
  MISSED: "error",
  CANCELLED: "default",
};

/** More items than this on one day collapse into an "N more" button. */
const MAX_PER_DAY = 3;

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** The month as a Monday-first grid; every item of a day is listed with its status chip. */
export function MonthGrid({
  month,
  items,
  management,
  onOpen,
}: {
  month: string;
  items: CalendarItem[];
  management: boolean;
  onOpen: (item: CalendarItem) => void;
}) {
  const [expanded, setExpanded] = useState<number | null>(null);
  const [year, mon] = month.split("-").map(Number);
  const daysInMonth = new Date(year, mon, 0).getDate();
  // Date.getDay: Sunday is 0; shift so Monday is the first column
  const lead = (new Date(year, mon - 1, 1).getDay() + 6) % 7;
  const byDay = new Map<number, CalendarItem[]>();
  for (const item of items) {
    const day = Number(item.date.slice(8, 10));
    byDay.set(day, [...(byDay.get(day) ?? []), item]);
  }
  const cells: (number | null)[] = [
    ...Array<null>(lead).fill(null),
    ...Array.from({ length: daysInMonth }, (_, i) => i + 1),
  ];
  return (
    <Box sx={{ overflowX: "auto", maxWidth: "100%", pb: 1 }}>
      <Box
        role="grid"
        aria-label="Month calendar"
        sx={{
          display: "grid",
          // wide enough to read a day; narrower screens scroll sideways inside this box
          gridTemplateColumns: "repeat(7, minmax(120px, 1fr))",
          minWidth: 7 * 120 + 6 * 4,
          gap: 0.5,
        }}
      >
        {WEEKDAYS.map((d) => (
          <Typography
            key={d}
            role="columnheader"
            variant="caption"
            sx={{ fontWeight: 600, textAlign: "center" }}
          >
            {d}
          </Typography>
        ))}
        {cells.map((day, index) => (
          <Box
            key={index}
            role="gridcell"
            sx={{
              minHeight: 96,
              minWidth: 0,
              p: 0.5,
              border: 1,
              borderColor: "divider",
              borderRadius: 1,
              opacity: day === null ? 0.3 : 1,
            }}
          >
            {day !== null && (
              <>
                <Typography variant="caption" color="text.secondary">
                  {day}
                </Typography>
                {(byDay.get(day) ?? []).slice(0, MAX_PER_DAY).map((item) => (
                  <Box key={item.key} sx={{ mt: 0.5 }}>
                    <Chip
                      size="small"
                      clickable
                      color={STATUS_COLOR[item.status]}
                      onClick={() => onOpen(item)}
                      aria-label={`${KIND_LABELS[item.kind]} ${formatDate(item.date)} ${STATUS_LABELS[item.status]}`}
                      label={`${KIND_LABELS[item.kind]} · ${STATUS_LABELS[item.status]}${item.doneLate ? " (late)" : ""}`}
                      sx={{
                        maxWidth: "100%",
                        textDecoration:
                          item.status === "CANCELLED" ? "line-through" : "none",
                      }}
                    />
                    <Typography
                      variant="caption"
                      component="div"
                      color="text.secondary"
                      sx={{ overflowWrap: "anywhere" }}
                    >
                      {item.people.map((p) => p.name).join(", ")}
                      {" · "}
                      {item.place ?? "No place recorded"}
                      {management && !item.zoneName ? " · No Zone" : ""}
                    </Typography>
                    {item.rescheduled && item.originalDate && (
                      <Typography
                        variant="caption"
                        component="div"
                        color="warning.main"
                      >
                        Rescheduled, first planned{" "}
                        {formatDate(item.originalDate)}
                      </Typography>
                    )}
                  </Box>
                ))}
                {(byDay.get(day)?.length ?? 0) > MAX_PER_DAY && (
                  <Button size="small" onClick={() => setExpanded(day)}>
                    {`${(byDay.get(day)?.length ?? 0) - MAX_PER_DAY} more`}
                  </Button>
                )}
              </>
            )}
          </Box>
        ))}
        {expanded !== null && (
          <Dialog
            open
            onClose={() => setExpanded(null)}
            fullWidth
            maxWidth="sm"
          >
            <DialogTitle>{`Activities on ${String(expanded).padStart(2, "0")}/${String(mon).padStart(2, "0")}/${year}`}</DialogTitle>
            <DialogContent>
              {(byDay.get(expanded) ?? []).map((item) => (
                <Box key={item.key} sx={{ mb: 1 }}>
                  <Button
                    onClick={() => {
                      setExpanded(null);
                      onOpen(item);
                    }}
                    aria-label={`Open ${KIND_LABELS[item.kind]} ${STATUS_LABELS[item.status]} ${item.people.map((p) => p.name).join(", ")}`}
                  >
                    {`${KIND_LABELS[item.kind]} · ${STATUS_LABELS[item.status]} · ${item.people.map((p) => p.name).join(", ")}`}
                  </Button>
                </Box>
              ))}
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setExpanded(null)}>Close</Button>
            </DialogActions>
          </Dialog>
        )}
      </Box>
    </Box>
  );
}
