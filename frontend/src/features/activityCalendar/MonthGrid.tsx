import { Box, Chip, Typography } from "@mui/material";
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
    <Box
      role="grid"
      aria-label="Month calendar"
      sx={{
        display: "grid",
        gridTemplateColumns: "repeat(7, minmax(0, 1fr))",
        gap: 0.5,
        overflowX: "auto",
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
              {(byDay.get(day) ?? []).map((item) => (
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
                    <Typography variant="caption" component="div" color="warning.main">
                      Rescheduled, first planned {formatDate(item.originalDate)}
                    </Typography>
                  )}
                </Box>
              ))}
            </>
          )}
        </Box>
      ))}
    </Box>
  );
}
