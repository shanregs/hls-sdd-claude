import { Pressable, StyleSheet, View, useWindowDimensions } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { DayView } from "../api/attendanceApi";
import { WEEKDAY_SHORT, weekdayIndex } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { presentCalendarDay, presentDay, type DayLook, type Viewer } from "./dayPresentation";

/** From this text scale up the seven-column grid is too tight, so days are listed one per row. */
export const LIST_LAYOUT_FONT_SCALE = 1.3;

interface Props {
  days: DayView[];
  viewer?: Viewer;
  /** "attendance": a person's marks. "calendar": only holidays and weekly offs are marked. */
  variant?: "attendance" | "calendar";
  /** Tapping a day. Without it the days are shown but not pressable. */
  onSelect?: (day: DayView) => void;
  /** Overrides the device text scale (tests). */
  fontScale?: number;
}

export function MonthGrid({ days, viewer = "self", variant = "attendance", onSelect, fontScale }: Props) {
  const theme = useTheme();
  const window = useWindowDimensions();
  const scale = fontScale ?? window.fontScale;
  const mode = theme.dark ? "dark" : "light";
  const look = (day: DayView): DayLook =>
    variant === "calendar" ? presentCalendarDay(day, mode) : presentDay(day, mode, viewer);

  if (days.length === 0) return null;

  if (scale >= LIST_LAYOUT_FONT_SCALE) {
    return (
      <View accessibilityLabel="Days of the month">
        {days.map((day) => {
          const l = look(day);
          return (
            <Pressable
              key={day.date}
              disabled={!onSelect}
              onPress={() => onSelect?.(day)}
              accessibilityRole={onSelect ? "button" : "text"}
              accessibilityLabel={l.label}
              style={[styles.listRow, { backgroundColor: l.background, borderColor: l.borderColor }]}
            >
              <Text style={{ color: l.textColor }}>{l.label}</Text>
            </Pressable>
          );
        })}
      </View>
    );
  }

  const leading = weekdayIndex(days[0].date);
  const cells: (DayView | null)[] = [...Array<null>(leading).fill(null), ...days];
  while (cells.length % 7 !== 0) cells.push(null);
  const weeks: (DayView | null)[][] = [];
  for (let i = 0; i < cells.length; i += 7) weeks.push(cells.slice(i, i + 7));

  return (
    <View accessibilityLabel="Days of the month">
      <View style={styles.week} importantForAccessibility="no-hide-descendants">
        {WEEKDAY_SHORT.map((name) => (
          <Text key={name} variant="labelSmall" style={styles.weekday}>
            {name}
          </Text>
        ))}
      </View>
      {weeks.map((week, index) => (
        <View key={index} style={styles.week}>
          {week.map((day, column) => {
            if (!day) return <View key={`blank-${column}`} style={styles.cell} />;
            const l = look(day);
            return (
              <Pressable
                key={day.date}
                disabled={!onSelect}
                onPress={() => onSelect?.(day)}
                accessibilityRole={onSelect ? "button" : "text"}
                accessibilityLabel={l.label}
                style={[styles.cell, styles.day, { backgroundColor: l.background, borderColor: l.borderColor }]}
              >
                <Text variant="labelSmall" style={{ color: l.textColor }}>
                  {Number(day.date.slice(8))}
                </Text>
                <Text variant="labelLarge" style={{ color: l.textColor }}>
                  {l.letter}
                  {l.half ? "½" : ""}
                </Text>
              </Pressable>
            );
          })}
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  week: { flexDirection: "row" },
  weekday: { flex: 1, textAlign: "center", paddingVertical: spacingUnit / 2 },
  cell: { flex: 1, minHeight: minTouchTarget, margin: 1 },
  day: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 6, alignItems: "center", justifyContent: "center" },
  listRow: {
    minHeight: minTouchTarget,
    justifyContent: "center",
    paddingHorizontal: spacingUnit * 1.5,
    marginVertical: 1,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 6,
  },
});
