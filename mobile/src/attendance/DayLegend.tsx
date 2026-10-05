import { StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { DayState, DayView, MarkView, StatusCategory } from "../api/attendanceApi";
import { spacingUnit } from "../theme/tokens";
import { presentCalendarDay, presentDay } from "./dayPresentation";

interface Entry {
  key: string;
  text: string;
  day: DayView;
}

const sampleMark = (category: StatusCategory, code: string, name: string): MarkView => ({
  date: "2026-01-05",
  code,
  codeName: name,
  category,
  dayValue: 1,
  schoolId: "",
  schoolName: "",
  setByKind: "SELF",
  setByUserId: "",
  setByName: "",
  setAt: "",
  note: null,
  version: 0,
});

const sample = (state: DayState, mark: MarkView | null = null): DayView => ({
  date: "2026-01-05",
  state,
  mark,
  editableBy: "NONE",
});

const ATTENDANCE: Entry[] = [
  { key: "worked", text: "Worked", day: sample("MARKED", sampleMark("WORKED", "P", "Present")) },
  { key: "leave", text: "Leave", day: sample("MARKED", sampleMark("LEAVE", "A", "Absent")) },
  { key: "training", text: "Training", day: sample("MARKED", sampleMark("TRAINING", "T", "Training")) },
  { key: "unmarked", text: "Not marked", day: sample("UNMARKED") },
  { key: "off", text: "Weekly off", day: sample("WEEKLY_OFF") },
  { key: "holiday", text: "Holiday", day: sample("NON_WORKING") },
  { key: "notplaced", text: "Not placed", day: sample("NOT_PLACED") },
];

const CALENDAR: Entry[] = [
  { key: "off", text: "Weekly off", day: sample("WEEKLY_OFF") },
  { key: "holiday", text: "Holiday", day: sample("NON_WORKING") },
];

/** What each colour means, in the current theme. A half day shows "½" next to its letter. */
export function DayLegend({ variant = "attendance" }: { variant?: "attendance" | "calendar" }) {
  const theme = useTheme();
  const mode = theme.dark ? "dark" : "light";
  const entries = variant === "calendar" ? CALENDAR : ATTENDANCE;
  return (
    <View style={styles.wrap} accessibilityLabel="Legend">
      {entries.map((entry) => {
        const look =
          variant === "calendar" ? presentCalendarDay(entry.day, mode) : presentDay(entry.day, mode, "self");
        return (
          <View key={entry.key} style={styles.item} accessible accessibilityLabel={`Legend: ${entry.text}`}>
            <View style={[styles.swatch, { backgroundColor: look.background, borderColor: look.borderColor }]}>
              <Text variant="labelSmall" style={{ color: look.textColor }}>
                {look.letter}
              </Text>
            </View>
            <Text variant="bodySmall">{entry.text}</Text>
          </View>
        );
      })}
      {variant === "attendance" ? (
        <View style={styles.item} accessible accessibilityLabel="Legend: ½ means a half day">
          <Text variant="labelLarge">½</Text>
          <Text variant="bodySmall">Half day</Text>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit * 1.5 },
  item: { flexDirection: "row", alignItems: "center", gap: spacingUnit / 2 },
  swatch: {
    minWidth: 24,
    height: 24,
    borderRadius: 4,
    borderWidth: StyleSheet.hairlineWidth,
    alignItems: "center",
    justifyContent: "center",
  },
});
