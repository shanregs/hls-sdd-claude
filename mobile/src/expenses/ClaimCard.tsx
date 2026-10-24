import { Pressable, StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { ClaimRow } from "../api/expensesApi";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { formatRupees } from "./expenseMessages";

const billText = (row: ClaimRow) => (row.billCount === 0 ? "no bill" : row.billCount === 1 ? "1 bill" : `${row.billCount} bills`);

/** What a row says aloud: category, amount, date, area, status, bills and the linked work. */
export function claimSummaryText(row: ClaimRow): string {
  return [
    row.categoryLabel,
    formatRupees(row.amount),
    formatIsoDate(row.expenseDate),
    row.areaName,
    row.statusLabel,
    billText(row),
    row.linkLabel,
  ]
    .filter(Boolean)
    .join(", ");
}

/** One claim in the list: category, amount, date, area, status in words (Rejected in its own style), bills and linked work. */
export function ClaimCard({ row, onPress }: { row: ClaimRow; onPress?: () => void }) {
  const theme = useTheme();
  const rejected = row.status === "REJECTED";
  return (
    <Pressable
      onPress={onPress}
      disabled={!onPress}
      accessibilityRole="button"
      accessibilityLabel={claimSummaryText(row)}
      style={[styles.card, { borderColor: rejected ? theme.colors.error : theme.colors.outline, backgroundColor: theme.colors.surface }]}
    >
      <View style={styles.line}>
        <Text variant="titleMedium">{row.categoryLabel}</Text>
        <Text variant="titleMedium">{formatRupees(row.amount)}</Text>
      </View>
      <Text variant="bodyMedium">
        {formatIsoDate(row.expenseDate)}
        {row.areaName ? ` · ${row.areaName}` : ""}
      </Text>
      <View style={styles.line}>
        <Text variant="labelLarge" style={rejected ? { color: theme.colors.error } : undefined}>
          {row.statusLabel}
        </Text>
        <Text variant="labelLarge">{billText(row)}</Text>
      </View>
      {row.linkLabel ? <Text variant="bodySmall">{row.linkLabel}</Text> : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: { minHeight: minTouchTarget, borderWidth: 1, borderRadius: spacingUnit, padding: spacingUnit * 1.5, gap: spacingUnit / 2 },
  line: { flexDirection: "row", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: spacingUnit },
});
