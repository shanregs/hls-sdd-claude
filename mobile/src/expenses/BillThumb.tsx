import { useEffect, useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { openBill, type BillRef } from "../api/expensesApi";
import { formatFileType, formatSize } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

type Thumb = { key: string; state: "ready"; uri: string } | { key: string; state: "error" };

/**
 * One bill of a claim: its name, type and size, a thumbnail for an image (downloaded and held in memory only, never
 * written to the phone) and, for a pdf, the name only. A bill removed by an approver is struck through with the reason.
 */
export function BillThumb({ claimId, bill, index }: { claimId: string; bill: BillRef; index: number }) {
  const isImage = bill.contentType.toLowerCase().startsWith("image/");
  const [attempt, setAttempt] = useState(0);
  const key = `${bill.id}|${attempt}`;
  const [thumb, setThumb] = useState<Thumb | null>(null);

  useEffect(() => {
    if (!isImage || bill.removed) return undefined;
    let cancelled = false;
    openBill(claimId, bill.id)
      .then((view) => {
        if (!cancelled) setThumb({ key, state: "ready", uri: view.dataUri });
      })
      .catch(() => {
        if (!cancelled) setThumb({ key, state: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, [claimId, bill.id, bill.removed, isImage, key]);

  const current = thumb?.key === key ? thumb : null;
  const label = `${bill.name}, ${formatFileType(bill.contentType)}, ${formatSize(bill.sizeBytes)}`;
  return (
    <View style={styles.row} accessibilityLabel={`Bill ${index + 1}${bill.removed ? " (removed)" : ""}: ${label}`}>
      <Text variant="bodyMedium" style={bill.removed ? styles.struck : undefined}>
        {label}
      </Text>
      {bill.removed ? (
        <Text variant="bodySmall">{`Removed${bill.removedByName ? ` by ${bill.removedByName}` : ""}${bill.removalReason ? `: ${bill.removalReason}` : ""}`}</Text>
      ) : null}
      {isImage && !bill.removed && !current ? <Text variant="bodySmall">Loading the bill…</Text> : null}
      {current?.state === "ready" ? <Image source={{ uri: current.uri }} style={styles.thumb} accessibilityLabel={`Bill photo ${bill.name}`} /> : null}
      {current?.state === "error" ? (
        <View>
          <HelperText type="error" visible accessibilityRole="alert">
            The bill could not be loaded.
          </HelperText>
          <Button onPress={() => setAttempt((n) => n + 1)} contentStyle={styles.button} accessibilityLabel={`Try again for ${bill.name}`}>
            Try again
          </Button>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { gap: spacingUnit / 2 },
  struck: { textDecorationLine: "line-through" },
  thumb: { width: 140, height: 140, borderRadius: spacingUnit },
  button: { minHeight: minTouchTarget },
});
