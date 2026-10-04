import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  IconButton,
  Paper,
  Stack,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from "@mui/material";
import DownloadOutlined from "@mui/icons-material/DownloadOutlined";
import type { AttendanceCalendar } from "./attendanceApi";
import { dayBackground } from "./dayStyle";
import { DayLegend } from "./DayLegend";
import {
  holidayName,
  holidaysIn,
  kindOf,
  MONTH_NAMES,
  monthsOfYear,
  monthWeeks,
} from "./holidayCalendarModel";
import { downloadHolidayPdf, type PdfScope } from "./holidayPdf";
import {
  currentMonth,
  formatDate,
  monthLabel,
  parseMonth,
  shiftMonth,
  WEEKDAY_LABELS,
  weekdayIndex,
} from "./monthUtils";

type View = "year" | "month";

/** One month as a table; the small form (year view) shows day numbers, the large form names each holiday. */
function MonthTable({
  month,
  calendar,
  large,
}: {
  month: string;
  calendar: AttendanceCalendar;
  large: boolean;
}) {
  const { month: m, year } = parseMonth(month);
  return (
    <Box
      component="table"
      aria-label={`${MONTH_NAMES[m - 1]} ${year}`}
      sx={{ width: "100%", borderCollapse: "collapse", tableLayout: "fixed" }}
    >
      <thead>
        <tr>
          {WEEKDAY_LABELS.map((label) => (
            <Box
              key={label}
              component="th"
              scope="col"
              aria-label={label}
              sx={{ fontSize: large ? "0.85rem" : "0.7rem", p: 0.25 }}
            >
              {large ? label : label[0]}
            </Box>
          ))}
        </tr>
      </thead>
      <tbody>
        {monthWeeks(month).map((week, w) => (
          <tr key={w}>
            {week.map((date, i) => {
              if (!date) return <td key={i} />;
              const kind = kindOf(date, calendar);
              const name = holidayName(date, calendar);
              const description = name
                ? `${formatDate(date)}: ${name} (holiday)`
                : kind === "off"
                  ? `${formatDate(date)}: weekly off, ${WEEKDAY_LABELS[weekdayIndex(date)]}`
                  : formatDate(date);
              return (
                <Box
                  key={i}
                  component="td"
                  aria-label={description}
                  title={description}
                  sx={{
                    border: 1,
                    borderColor: "divider",
                    textAlign: "center",
                    verticalAlign: "top",
                    p: large ? 0.75 : 0.25,
                    height: large ? 76 : undefined,
                    fontSize: large ? "0.95rem" : "0.75rem",
                    fontWeight: kind === "holiday" ? 700 : 400,
                    bgcolor: dayBackground(kind),
                  }}
                >
                  {Number(date.slice(8))}
                  {large && name && (
                    <Typography
                      variant="caption"
                      component="div"
                      sx={{ lineHeight: 1.2, mt: 0.25 }}
                    >
                      {name}
                    </Typography>
                  )}
                  {large && !name && kind === "off" && (
                    <Typography variant="caption" component="div">
                      {WEEKDAY_LABELS[weekdayIndex(date)]}
                    </Typography>
                  )}
                </Box>
              );
            })}
          </tr>
        ))}
      </tbody>
    </Box>
  );
}

/**
 * The holiday calendar as a year (twelve months) or a month, with holidays highlighted, a legend, the
 * holiday list and a PDF download of what is on screen (spec 008 US9, FR-025, FR-026).
 */
export function HolidayCalendarView({
  calendar,
}: {
  calendar: AttendanceCalendar;
}) {
  const [view, setView] = useState<View>("year");
  const [month, setMonth] = useState(currentMonth());
  const [pdfError, setPdfError] = useState<string | null>(null);
  const year = parseMonth(month).year;

  const step = (delta: number) =>
    setMonth(shiftMonth(month, view === "year" ? delta * 12 : delta));

  const scope: PdfScope =
    view === "year" ? { type: "year", year } : { type: "month", month };

  const download = async () => {
    try {
      setPdfError(null);
      await downloadHolidayPdf(calendar, scope);
    } catch {
      setPdfError("Could not create the PDF. Please try again.");
    }
  };

  const holidays = holidaysIn(view === "year" ? String(year) : month, calendar);

  return (
    <Box component="section" aria-labelledby="holiday-view-heading">
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", flexWrap: "wrap", gap: 1, mb: 2 }}
      >
        <Typography
          variant="h6"
          component="h2"
          id="holiday-view-heading"
          sx={{ mr: 1 }}
        >
          Calendar
        </Typography>
        <ToggleButtonGroup
          exclusive
          size="small"
          value={view}
          aria-label="Calendar view"
          onChange={(_, next: View | null) => next && setView(next)}
        >
          <ToggleButton value="year">Year</ToggleButton>
          <ToggleButton value="month">Month</ToggleButton>
        </ToggleButtonGroup>
        <Button onClick={() => step(-1)}>
          {view === "year" ? "Previous year" : "Previous month"}
        </Button>
        <Typography
          component="span"
          aria-live="polite"
          sx={{ fontWeight: 700, minWidth: 130, textAlign: "center" }}
        >
          {view === "year" ? year : monthLabel(month)}
        </Typography>
        <Button onClick={() => step(1)}>
          {view === "year" ? "Next year" : "Next month"}
        </Button>
        <Tooltip title="Download PDF">
          <IconButton
            color="primary"
            aria-label={`Download PDF of ${view === "year" ? year : monthLabel(month)}`}
            onClick={download}
          >
            <DownloadOutlined />
          </IconButton>
        </Tooltip>
      </Stack>

      {pdfError && (
        <Alert severity="error" role="alert" sx={{ mb: 2 }}>
          {pdfError}
        </Alert>
      )}

      {view === "year" ? (
        <Box
          sx={{
            display: "grid",
            gap: 2,
            gridTemplateColumns: {
              xs: "1fr",
              sm: "repeat(2, 1fr)",
              lg: "repeat(3, 1fr)",
            },
          }}
        >
          {monthsOfYear(year).map((m) => (
            <Paper key={m} variant="outlined" sx={{ p: 1.5 }}>
              <Button
                size="small"
                onClick={() => {
                  setMonth(m);
                  setView("month");
                }}
                aria-label={`Open ${MONTH_NAMES[parseMonth(m).month - 1]} ${year}`}
                sx={{ mb: 0.5, fontWeight: 700 }}
              >
                {MONTH_NAMES[parseMonth(m).month - 1]}
              </Button>
              <MonthTable month={m} calendar={calendar} large={false} />
            </Paper>
          ))}
        </Box>
      ) : (
        <Paper variant="outlined" sx={{ p: 1.5 }}>
          <MonthTable month={month} calendar={calendar} large />
        </Paper>
      )}

      <Stack spacing={1} sx={{ mt: 2 }}>
        <DayLegend kinds={["holiday", "off"]} />
        <Typography variant="subtitle1" component="h3">
          {view === "year"
            ? `Holidays in ${year}`
            : `Holidays in ${monthLabel(month)}`}
        </Typography>
        {holidays.length === 0 ? (
          <Typography variant="body2">No holidays in this period.</Typography>
        ) : (
          <Box component="ul" sx={{ m: 0, pl: 3 }}>
            {holidays.map((h) => (
              <li key={h.date}>
                <Typography variant="body2">
                  {formatDate(h.date)} ({WEEKDAY_LABELS[weekdayIndex(h.date)]})
                  - {h.description}
                </Typography>
              </li>
            ))}
          </Box>
        )}
      </Stack>
    </Box>
  );
}
