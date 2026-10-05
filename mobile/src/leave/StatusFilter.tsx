import { ScrollView, StyleSheet } from "react-native";
import { Chip } from "react-native-paper";
import type { LeaveStatus } from "../api/leaveApi";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

export type StatusChoice = LeaveStatus | "ALL";

export const HISTORY_FILTERS: { value: StatusChoice; label: string }[] = [
  { value: "ALL", label: "All" },
  { value: "PENDING", label: "Pending" },
  { value: "APPROVED", label: "Approved" },
  { value: "REJECTED", label: "Rejected" },
  { value: "CANCELLED", label: "Cancelled" },
];

/** Leave Management has no "All": with no status the server answers Pending only (research §2). */
export const MANAGEMENT_FILTERS = HISTORY_FILTERS.filter((f) => f.value !== "ALL");

interface Props {
  options: { value: StatusChoice; label: string }[];
  value: StatusChoice;
  onChange: (value: StatusChoice) => void;
}

export function StatusFilter({ options, value, onChange }: Props) {
  return (
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.row} accessibilityLabel="Filter by status">
      {options.map((option) => (
        <Chip
          key={option.value}
          selected={option.value === value}
          showSelectedCheck
          onPress={() => onChange(option.value)}
          accessibilityLabel={option.label}
          style={styles.chip}
        >
          {option.label}
        </Chip>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  row: { gap: spacingUnit, paddingVertical: spacingUnit / 2 },
  chip: { minHeight: minTouchTarget, justifyContent: "center" },
});
