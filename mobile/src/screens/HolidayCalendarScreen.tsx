import { useEffect, useMemo, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Text } from "react-native-paper";
import { getCalendar, type AttendanceCalendar } from "../api/attendanceApi";
import { NoConnectionError } from "../api/errors";
import { DayLegend } from "../attendance/DayLegend";
import { MonthGrid } from "../attendance/MonthGrid";
import { Problem } from "../attendance/MonthPane";
import { MonthPicker } from "../attendance/MonthPicker";
import { calendarDays, holidaysOfMonth, holidaysOfYear } from "../attendance/holidayModel";
import { currentMonth } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { formatLongDate } from "../formats/dates";
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
        {all.map((holiday) => (
          <HolidayLine key={holiday.date} date={holiday.date} description={holiday.description} />
        ))}
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

const styles = StyleSheet.create({
  button: { minHeight: minTouchTarget },
  line: { gap: 2, paddingVertical: spacingUnit / 2 },
});
