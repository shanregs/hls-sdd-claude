import { useEffect, useState } from "react";
import { StyleSheet } from "react-native";
import { Button, Dialog, Portal, Text } from "react-native-paper";
import { getClaim, type ClaimDetail } from "../api/expensesApi";
import { minTouchTarget } from "../theme/tokens";
import { formatIsoDate } from "../formats/dates";
import { formatRupees } from "./expenseMessages";

interface Props {
  /** The ids the server listed as matching. */
  matches: string[];
  confirmLabel: string;
  dismissLabel: string;
  onConfirm: () => void;
  onDismiss: () => void;
  busy?: boolean;
}

/**
 * "You may have raised this already" (spec 036 FR-011): the claimant's own matching claims, read by id, with the choice to
 * submit anyway or not. Nothing is sent until the claimant confirms.
 */
export function DuplicateDialog({ matches, confirmLabel, dismissLabel, onConfirm, onDismiss, busy }: Props) {
  const [claims, setClaims] = useState<ClaimDetail[]>([]);
  useEffect(() => {
    let cancelled = false;
    Promise.all(matches.map((id) => getClaim(id).catch(() => null))).then((list) => {
      if (!cancelled) setClaims(list.filter((c): c is ClaimDetail => c !== null));
    });
    return () => {
      cancelled = true;
    };
  }, [matches]);

  return (
    <Portal>
      <Dialog visible onDismiss={onDismiss}>
        <Dialog.Title accessibilityRole="header">Possible duplicate</Dialog.Title>
        <Dialog.Content>
          <Text variant="bodyMedium">You may have raised this already: same date, amount and category.</Text>
          {claims.map((claim) => (
            <Text key={claim.id} variant="bodyMedium" accessibilityLabel={`Matching claim ${claim.categoryLabel} ${formatRupees(claim.amount)} ${claim.statusLabel}`}>
              {`${claim.categoryLabel}, ${formatRupees(claim.amount)}, ${formatIsoDate(claim.expenseDate)}, ${claim.statusLabel}`}
            </Text>
          ))}
        </Dialog.Content>
        <Dialog.Actions>
          <Button onPress={onDismiss} disabled={busy} contentStyle={styles.button} accessibilityLabel={`${dismissLabel} (possible duplicate)`}>
            {dismissLabel}
          </Button>
          <Button onPress={onConfirm} disabled={busy} loading={busy} contentStyle={styles.button} accessibilityLabel={confirmLabel}>
            {confirmLabel}
          </Button>
        </Dialog.Actions>
      </Dialog>
    </Portal>
  );
}

const styles = StyleSheet.create({ button: { minHeight: minTouchTarget } });
