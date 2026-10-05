import { StyleSheet, View } from "react-native";
import { HelperText, Text } from "react-native-paper";
import type { AttendanceCalendar } from "../api/attendanceApi";
import type { LeavePreview } from "../api/leaveApi";
import { formatIsoDate } from "../formats/dates";
import { spacingUnit } from "../theme/tokens";
import { leaveRefusalText } from "./leaveMessages";
import { previewDayList } from "./previewDays";

interface Props {
  firstDate: string;
  lastDate: string;
  preview: LeavePreview;
  calendar: AttendanceCalendar | null;
}

/** The server's preview of a draft: its working-day total, the days of the range, and any problems. */
export function PreviewPanel({ firstDate, lastDate, preview, calendar }: Props) {
  const days = previewDayList(firstDate, lastDate, preview, calendar);
  return (
    <View style={styles.panel} accessibilityLabel="Leave preview">
      <Text variant="titleMedium" accessibilityLabel={`Working days ${preview.workingDays}`}>
        Working days: {preview.workingDays}
      </Text>
      {preview.problems.map((problem) => (
        <HelperText key={problem} type="error" visible accessibilityRole="alert">
          {leaveRefusalText(problem)}
        </HelperText>
      ))}
      {days.map((day) => (
        <View key={day.date} style={styles.row} accessible accessibilityLabel={day.spoken}>
          <Text variant="bodyMedium">{formatIsoDate(day.date)}</Text>
          <Text variant="bodyMedium">{day.label}</Text>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  panel: { gap: spacingUnit / 2 },
  row: { flexDirection: "row", justifyContent: "space-between", paddingVertical: 2 },
});
