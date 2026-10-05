import { StyleSheet } from "react-native";
import { Button, Text } from "react-native-paper";
import {
  clearTeacherMark,
  getDayHistory,
  getTeacherMonth,
  saveTeacherMark,
  type MarkRequest,
} from "../api/attendanceApi";
import { MonthPane } from "../attendance/MonthPane";
import { minTouchTarget } from "../theme/tokens";
import { Screen } from "./Screen";

interface Props {
  teacherId: string;
  name: string;
  initialMonth: string;
  onMonthChange: (month: string) => void;
  onBack: () => void;
}

/** One assigned Teacher's month. A day can be marked, corrected or cleared when the server says `editableBy: SUPERVISOR`. */
export function TeacherMonthScreen({ teacherId, name, initialMonth, onMonthChange, onBack }: Props) {
  return (
    <Screen>
      <Button icon="arrow-left" onPress={onBack} contentStyle={styles.button} accessibilityLabel="Back to Teachers">
        Teachers
      </Button>
      <Text variant="headlineSmall" accessibilityRole="header">
        {name}
      </Text>
      <MonthPane
        kind="current"
        viewer="supervisor"
        loader={(month) => getTeacherMonth(teacherId, month)}
        initialMonth={initialMonth}
        onMonthChange={onMonthChange}
        notFoundText="Not found"
        actions={{
          editableBy: "SUPERVISOR",
          save: (date: string, body: MarkRequest) => saveTeacherMark(teacherId, date, body),
          clear: (date: string) => clearTeacherMark(teacherId, date),
          history: (date: string) => getDayHistory(teacherId, date),
        }}
      />
    </Screen>
  );
}

const styles = StyleSheet.create({ button: { minHeight: minTouchTarget, justifyContent: "flex-start" } });
