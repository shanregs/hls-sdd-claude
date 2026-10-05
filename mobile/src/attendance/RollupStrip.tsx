import { StyleSheet, View } from "react-native";
import { Chip, Text } from "react-native-paper";
import type { RollupView } from "../api/attendanceApi";
import { spacingUnit } from "../theme/tokens";

/** The month's totals exactly as the server sent them: the app does no arithmetic of its own. */
export function RollupStrip({ rollup, locked }: { rollup: RollupView; locked?: boolean }) {
  const isLocked = locked ?? rollup.locked;
  const stats: { label: string; value: number }[] = [
    { label: "Working days", value: rollup.workingDays },
    { label: "Days worked", value: rollup.daysWorked },
    { label: "Leave", value: rollup.daysLeave },
    { label: "Unmarked", value: rollup.unmarked },
  ];
  return (
    <View style={styles.wrap} accessibilityLabel="Month totals">
      {stats.map((stat) => (
        <View key={stat.label} style={styles.stat} accessible accessibilityLabel={`${stat.label} ${stat.value}`}>
          <Text variant="titleMedium">{stat.value}</Text>
          <Text variant="labelSmall">{stat.label}</Text>
        </View>
      ))}
      {isLocked ? (
        <Chip icon="lock" compact accessibilityLabel="Locked">
          Locked
        </Chip>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { flexDirection: "row", flexWrap: "wrap", alignItems: "center", gap: spacingUnit * 2 },
  stat: { alignItems: "center", minWidth: 64 },
});
