import { useMemo, useState } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Button, IconButton, List } from "react-native-paper";
import { formatMonth } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { allowedMonths, shiftMonth, type RangeKind } from "./monthRange";
import { serverNow } from "./serverClock";

interface Props {
  value: string;
  /** "current": the current year only. "history": the current and the previous year. */
  kind: RangeKind;
  onChange: (month: string) => void;
}

/**
 * Previous and next arrows plus a list of the months the screen may show. The arrows stop at the
 * ends of the allowed range, so no other year is ever reachable (spec FR-002, FR-006).
 */
export function MonthPicker({ value, kind, onChange }: Props) {
  const [open, setOpen] = useState(false);
  const months = useMemo(() => allowedMonths(kind, serverNow()), [kind]);
  const previous = shiftMonth(value, -1, months);
  const next = shiftMonth(value, 1, months);

  return (
    <View>
      <View style={styles.row}>
        <IconButton
          icon="chevron-left"
          size={28}
          disabled={previous === null}
          onPress={() => previous && onChange(previous)}
          accessibilityLabel="Previous month"
          style={styles.arrow}
        />
        <Button
          mode="text"
          onPress={() => setOpen((o) => !o)}
          contentStyle={styles.titleContent}
          style={styles.title}
          accessibilityLabel={`Choose month, ${formatMonth(value)}`}
          accessibilityState={{ expanded: open }}
        >
          {formatMonth(value)}
        </Button>
        <IconButton
          icon="chevron-right"
          size={28}
          disabled={next === null}
          onPress={() => next && onChange(next)}
          accessibilityLabel="Next month"
          style={styles.arrow}
        />
      </View>
      {open ? (
        <ScrollView style={styles.list} nestedScrollEnabled accessibilityLabel="Months">
          {months.map((month) => (
            <List.Item
              key={month}
              title={formatMonth(month)}
              onPress={() => {
                setOpen(false);
                onChange(month);
              }}
              accessibilityLabel={formatMonth(month)}
              accessibilityState={{ selected: month === value }}
              right={month === value ? (props) => <List.Icon {...props} icon="check" /> : undefined}
              style={styles.item}
            />
          ))}
        </ScrollView>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", justifyContent: "space-between" },
  arrow: { width: minTouchTarget, height: minTouchTarget },
  title: { flex: 1 },
  titleContent: { minHeight: minTouchTarget },
  list: { maxHeight: spacingUnit * 36 },
  item: { minHeight: minTouchTarget },
});
