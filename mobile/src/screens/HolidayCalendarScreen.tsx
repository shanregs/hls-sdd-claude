import { useEffect, useMemo, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Text, useTheme } from "react-native-paper";
import { getCalendar, type AttendanceCalendar } from "../api/attendanceApi";
import { NoConnectionError } from "../api/errors";
import { DayLegend } from "../attendance/DayLegend";
import { MonthGrid } from "../attendance/MonthGrid";
import { Problem } from "../attendance/MonthPane";
import { MonthPicker } from "../attendance/MonthPicker";
import { calendarDays, holidaysOfMonth, holidaysOfYear } from "../attendance/holidayModel";
import { currentMonth } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { formatIsoDate, formatLongDate } from "../formats/dates";
import { useInnerBack } from "../navigation/InnerBack";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

type LoadState = "loading" | "ready" | "error" | "noConnection";

/**
 * The organization's non-working dates and weekly offs, read-only. `schoolId` is the School whose
 * weekly offs apply when the calendar lists an override for it; otherwise the default is shown.
 */
export function HolidayCalendarScreen({ schoolId }: { schoolId?: string | null }) {
  const [month, setMonth] = useState(() => currentMonth(serverNow()));
  const [calendar, setCalendar] = useState<AttendanceCalendar | null>(null);
  const [state, setState] = useState<LoadState>("loading");
  const [yearList, setYearList] = useState(false);

  useInnerBack(yearList, () => setYearList(false));

  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getCalendar()
      .then((loaded) => {
        if (cancelled) return;
        setCalendar(loaded);
        setState("ready");
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setCalendar(null);
        setState(error instanceof NoConnectionError ? "noConnection" : "error");
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const retry = () => {
    setState("loading");
    setAttempt((n) => n + 1);
  };

  const days = useMemo(() => (calendar ? calendarDays(calendar, month, schoolId) : []), [calendar, month, schoolId]);
  const monthHolidays = useMemo(() => (calendar ? holidaysOfMonth(calendar, month) : []), [calendar, month]);
  const year = Number(month.slice(0, 4));

  if (state === "loading") {
    return (
      <Screen>
        <ActivityIndicator size="large" accessibilityLabel="Loading holidays" />
      </Screen>
    );
  }
  if (state === "noConnection" || state === "error" || !calendar) {
    return (
      <Screen>
        <Problem
          title={state === "noConnection" ? "No connection" : "Something went wrong"}
          text={
            state === "noConnection"
              ? "Check your internet connection and try again."
              : "We couldn't load the holiday calendar."
          }
          onRetry={retry}
        />
      </Screen>
    );
  }

  if (yearList) {
    const all = holidaysOfYear(calendar, year);
    return (
      <Screen>
        <Button
          icon="arrow-left"
          onPress={() => setYearList(false)}
          contentStyle={styles.button}
          accessibilityLabel="Back to calendar"
        >
          Calendar
        </Button>
        <Text variant="headlineSmall" accessibilityRole="header">
          All holidays in {year}
        </Text>
        {all.length === 0 ? <Text variant="bodyLarge">No holidays this year.</Text> : null}
        {all.length > 0 ? <HolidayTable holidays={all} /> : null}
      </Screen>
    );
  }

  return (
    <Screen>
      <MonthPicker value={month} kind="current" onChange={setMonth} />
      <MonthGrid days={days} variant="calendar" />
      <DayLegend variant="calendar" />
      <Text variant="titleMedium" accessibilityRole="header">
        Holidays this month
      </Text>
      {monthHolidays.length === 0 ? <Text variant="bodyMedium">No holidays this month.</Text> : null}
      {monthHolidays.map((holiday) => (
        <HolidayLine key={holiday.date} date={holiday.date} description={holiday.description} />
      ))}
      <Button
        mode="outlined"
        onPress={() => setYearList(true)}
        contentStyle={styles.button}
        accessibilityLabel="All holidays this year"
      >
        All holidays this year
      </Button>
    </Screen>
  );
}

function HolidayLine({ date, description }: { date: string; description: string }) {
  return (
    <View style={styles.line} accessible accessibilityLabel={`${formatLongDate(date)}, ${description}`}>
      <Text variant="bodyLarge">{description}</Text>
      <Text variant="bodySmall">{formatLongDate(date)}</Text>
    </View>
  );
}

/** The year's holidays as a table: Sl No, Date (DD/MM/YYYY) and Holiday. */
function HolidayTable({ holidays }: { holidays: { date: string; description: string }[] }) {
  const theme = useTheme();
  const border = { borderColor: theme.colors.outlineVariant };
  return (
    <View style={[styles.table, border]} accessibilityRole="list">
      <View style={[styles.row, { backgroundColor: theme.colors.surfaceVariant }]}>
        <Text variant="labelLarge" style={[styles.cell, styles.slNo]}>
          Sl No
        </Text>
        <Text variant="labelLarge" style={[styles.cell, styles.date]}>
          Date
        </Text>
        <Text variant="labelLarge" style={[styles.cell, styles.holiday]}>
          Holiday
        </Text>
      </View>
      {holidays.map((holiday, index) => (
        <View
          key={holiday.date}
          style={[styles.row, styles.bodyRow, border]}
          accessible
          accessibilityLabel={`${formatLongDate(holiday.date)}, ${holiday.description}`}
        >
          <Text variant="bodyMedium" style={[styles.cell, styles.slNo]}>
            {index + 1}
          </Text>
          <Text variant="bodyMedium" style={[styles.cell, styles.date]}>
            {formatIsoDate(holiday.date)}
          </Text>
          <Text variant="bodyMedium" style={[styles.cell, styles.holiday]}>
            {holiday.description}
          </Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  button: { minHeight: minTouchTarget },
  line: { gap: 2, paddingVertical: spacingUnit / 2 },
  table: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 4, overflow: "hidden" },
  row: { flexDirection: "row", alignItems: "center", minHeight: minTouchTarget },
  bodyRow: { borderTopWidth: StyleSheet.hairlineWidth },
  cell: { paddingHorizontal: spacingUnit, paddingVertical: spacingUnit / 2 },
  slNo: { width: 56 },
  date: { width: 110 },
  holiday: { flex: 1 },
});
