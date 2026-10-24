import * as Crypto from "expo-crypto";
import { useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text, TextInput } from "react-native-paper";
import type { BillRef } from "../api/expensesApi";
import { chooseBillPdf, chooseBillPhoto, takeBillPhoto, type BillPick } from "../bills/pickBill";
import type { PreparedBill } from "../bills/prepareBill";
import { formatSize } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_BILLS } from "./expenseMessages";

/** A bill in the form: a prepared file not sent yet. `sourceUri` is the prepared file to keep with the outbox item. */
export interface FormBill {
  localId: string;
  fileName: string;
  contentType: "image/jpeg" | "application/pdf";
  sizeBytes: number;
  /** A prepared file made in this form; absent for a bill already saved in the outbox. */
  sourceUri?: string;
  uploaded: boolean;
  serverBillId: string | null;
}

export const MAX_BILLS_TEXT = "A claim can have at most 5 bills.";

export const fromPrepared = (bill: PreparedBill): FormBill => ({
  localId: Crypto.randomUUID(),
  fileName: bill.fileName,
  contentType: bill.contentType,
  sizeBytes: bill.sizeBytes,
  sourceUri: bill.uri,
  uploaded: false,
  serverBillId: null,
});

interface Props {
  bills: FormBill[];
  /** Bills already on the claim at the server: shown, counted, and not removable by the claimant. */
  serverBills: BillRef[];
  noBillReason: string;
  onNoBillReason: (text: string) => void;
  onChange: (bills: FormBill[]) => void;
  disabled?: boolean;
  /** Leave out the no-bill reason (adding a bill to a claim that exists already). */
  hideNoBill?: boolean;
}

/**
 * The bills of a claim (spec 036 FR-005 to FR-009): take a photo, choose a photo or a pdf, up to 5, each with a preview and
 * Retake and Remove. A photo is prepared as it is added (jpg, about 2000 px). With no bill a reason is asked for instead.
 */
export function BillSlots({ bills, serverBills, noBillReason, onNoBillReason, onChange, disabled, hideNoBill }: Props) {
  const [preparing, setPreparing] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const liveServer = serverBills.filter((b) => !b.removed);
  const total = bills.length + liveServer.length;

  const run = async (pick: () => Promise<BillPick>, replaceId?: string) => {
    setProblem(null);
    if (!replaceId && total >= MAX_BILLS) {
      setProblem(MAX_BILLS_TEXT);
      return;
    }
    setPreparing(true);
    try {
      const result = await pick();
      if (result.problem) setProblem(result.problem);
      if (result.bill) {
        const added = fromPrepared(result.bill);
        onChange(replaceId ? bills.map((b) => (b.localId === replaceId ? added : b)) : [...bills, added]);
      }
    } finally {
      setPreparing(false);
    }
  };

  const busy = disabled || preparing;
  return (
    <View style={styles.box}>
      <Text variant="titleSmall" accessibilityRole="header">
        Bills
      </Text>
      <Text variant="bodySmall">{`${total} of ${MAX_BILLS}`}</Text>

      {liveServer.map((bill) => (
        <Text key={bill.id} variant="bodyMedium">
          {`${bill.name}, ${formatSize(bill.sizeBytes)} (already on the claim)`}
        </Text>
      ))}

      {bills.map((bill, index) => (
        <View key={bill.localId} style={styles.bill}>
          {bill.sourceUri && bill.contentType === "image/jpeg" ? (
            <Image source={{ uri: bill.sourceUri }} style={styles.thumb} accessibilityLabel={`Bill ${index + 1} preview`} />
          ) : null}
          <Text variant="bodyMedium">{`Bill ${index + 1}: ${bill.fileName}, ${formatSize(bill.sizeBytes)}`}</Text>
          <View style={styles.row}>
            {bill.contentType === "image/jpeg" ? (
              <Button mode="outlined" disabled={busy} onPress={() => void run(takeBillPhoto, bill.localId)} contentStyle={styles.button} accessibilityLabel={`Retake bill ${index + 1}`}>
                Retake
              </Button>
            ) : null}
            <Button mode="outlined" disabled={busy} onPress={() => onChange(bills.filter((b) => b.localId !== bill.localId))} contentStyle={styles.button} accessibilityLabel={`Remove bill ${index + 1}`}>
              Remove
            </Button>
          </View>
        </View>
      ))}

      <View style={styles.row}>
        <Button mode="outlined" disabled={busy} onPress={() => void run(takeBillPhoto)} contentStyle={styles.button} accessibilityLabel="Take photo">
          Take photo
        </Button>
        <Button mode="outlined" disabled={busy} onPress={() => void run(chooseBillPhoto)} contentStyle={styles.button} accessibilityLabel="Choose photo">
          Choose photo
        </Button>
        <Button mode="outlined" disabled={busy} onPress={() => void run(chooseBillPdf)} contentStyle={styles.button} accessibilityLabel="Choose PDF">
          Choose PDF
        </Button>
      </View>
      {preparing ? <Text variant="bodyMedium">Preparing...</Text> : null}
      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}

      {total === 0 && !hideNoBill ? (
        <TextInput
          mode="outlined"
          label="Reason there is no bill"
          accessibilityLabel="Reason there is no bill"
          value={noBillReason}
          onChangeText={onNoBillReason}
          maxLength={500}
          multiline
          disabled={disabled}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  row: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  bill: { gap: spacingUnit / 2 },
  thumb: { width: 160, height: 160, borderRadius: spacingUnit },
  button: { minHeight: minTouchTarget },
});
