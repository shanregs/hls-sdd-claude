import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, Text } from "react-native-paper";
import { formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { CompleteCheckOutDialog } from "./CompleteCheckOutDialog";

interface Props {
  /** Past days with a check-in and no check-out, as the server lists them. */
  dates: string[];
  /** The menu grants Create, so a check-out can be completed. */
  canComplete: boolean;
  /** Called after the server completed a day, so the lists can reload. */
  onDone: () => void;
}

/** "No check-out on DD/MM/YYYY" with a Complete check-out action for each day (spec 033 FR-007). */
export function MissingCheckOuts({ dates, canComplete, onDone }: Props) {
  const [open, setOpen] = useState<string | null>(null);
  if (dates.length === 0) return null;
  return (
    <View style={styles.list}>
      {dates.map((date) => (
        <View key={date} style={styles.row}>
          <Text variant="bodyLarge">No check-out on {formatIsoDate(date)}</Text>
          {canComplete ? (
            <Button
              mode="outlined"
              onPress={() => setOpen(date)}
              contentStyle={styles.button}
              accessibilityLabel={`Complete check-out for ${formatIsoDate(date)}`}
            >
              Complete check-out
            </Button>
          ) : null}
        </View>
      ))}
      {open ? (
        <CompleteCheckOutDialog
          date={open}
          onCancel={() => setOpen(null)}
          onDone={() => {
            setOpen(null);
            onDone();
          }}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacingUnit },
  row: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
