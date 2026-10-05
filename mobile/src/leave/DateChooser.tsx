import { useState } from "react";
import { Pressable, ScrollView, StyleSheet, View } from "react-native";
import { Button, Modal, Portal, Text, useTheme } from "react-native-paper";
import { MonthPicker } from "../attendance/MonthPicker";
import { allowedMonths, currentMonth } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { WEEKDAY_SHORT, formatIsoDate, formatLongDate, weekdayIndex } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

interface Props {
  /** Names the field, for example "First date". */
  label: string;
  /** "YYYY-MM-DD" or null when nothing is chosen. */
  value: string | null;
  onChange: (date: string) => void;
}

const pad = (n: number) => String(n).padStart(2, "0");

function daysOf(month: string): string[] {
  const [year, m] = month.split("-").map(Number);
  const length = new Date(Date.UTC(year, m, 0)).getUTCDate();
  return Array.from({ length }, (_, i) => `${month}-${pad(i + 1)}`);
}

/**
 * A date field with a small in-app chooser (spec 020 research §5): the month picker of spec 019 over the
 * previous, current and next year, and the month's days. It places no limit on the date: the server's
 * preview says whether the dates are acceptable.
 */
export function DateChooser({ label, value, onChange }: Props) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const [month, setMonth] = useState(() => {
    const months = allowedMonths("leave", serverNow());
    const wanted = value ? value.slice(0, 7) : currentMonth(serverNow());
    return months.includes(wanted) ? wanted : currentMonth(serverNow());
  });

  const days = daysOf(month);
  const leading = weekdayIndex(days[0]);
  const cells: (string | null)[] = [...Array<null>(leading).fill(null), ...days];

  return (
    <View>
      <Button
        mode="outlined"
        onPress={() => setOpen(true)}
        contentStyle={styles.button}
        accessibilityLabel={`${label}, ${value ? formatLongDate(value) : "not chosen"}`}
      >
        {value ? `${label}: ${formatIsoDate(value)}` : `${label}: choose`}
      </Button>
      {open ? (
        <Portal>
          <Modal
            visible
            onDismiss={() => setOpen(false)}
            style={styles.wrapper}
            contentContainerStyle={[styles.sheet, { backgroundColor: theme.colors.surface }]}
          >
            <ScrollView contentContainerStyle={styles.content}>
              <Text variant="titleMedium" accessibilityRole="header">
                {label}
              </Text>
              <MonthPicker value={month} kind="leave" onChange={setMonth} />
              <View style={styles.week} importantForAccessibility="no-hide-descendants">
                {WEEKDAY_SHORT.map((name) => (
                  <Text key={name} variant="labelSmall" style={styles.weekday}>
                    {name}
                  </Text>
                ))}
              </View>
              <View style={styles.grid}>
                {cells.map((date, index) =>
                  date ? (
                    <Pressable
                      key={date}
                      onPress={() => {
                        onChange(date);
                        setOpen(false);
                      }}
                      accessibilityRole="button"
                      accessibilityLabel={formatLongDate(date)}
                      accessibilityState={{ selected: date === value }}
                      style={[
                        styles.day,
                        {
                          borderColor: theme.colors.outline,
                          backgroundColor: date === value ? theme.colors.primaryContainer : "transparent",
                        },
                      ]}
                    >
                      <Text variant="labelLarge">{Number(date.slice(8))}</Text>
                    </Pressable>
                  ) : (
                    <View key={`blank-${index}`} style={styles.day} />
                  ),
                )}
              </View>
              <Button onPress={() => setOpen(false)} contentStyle={styles.button} accessibilityLabel="Close date chooser">
                Close
              </Button>
            </ScrollView>
          </Modal>
        </Portal>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  button: { minHeight: minTouchTarget },
  wrapper: { justifyContent: "flex-end" },
  sheet: { maxHeight: "90%", borderTopLeftRadius: 16, borderTopRightRadius: 16 },
  content: { padding: spacingUnit * 3, gap: spacingUnit },
  week: { flexDirection: "row" },
  weekday: { width: "14.2857%", textAlign: "center" },
  grid: { flexDirection: "row", flexWrap: "wrap" },
  day: {
    width: "14.2857%",
    minHeight: minTouchTarget,
    alignItems: "center",
    justifyContent: "center",
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 6,
  },
});
