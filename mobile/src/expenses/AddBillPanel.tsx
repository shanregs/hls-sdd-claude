import * as Crypto from "expo-crypto";
import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText } from "react-native-paper";
import type { ClaimDetail } from "../api/expensesApi";
import { useAction } from "../manager/useAction";
import { useExpenseOutbox } from "../outbox/ExpenseOutboxProvider";
import type { OutboxItem } from "../outbox/types";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { BillSlots, type FormBill } from "./BillSlots";
import { NOT_SAVED_TEXT, SAVED_OFFLINE_TEXT } from "./expenseMessages";

interface Props {
  claim: ClaimDetail;
  onCancel: () => void;
  /** Called once the bills are on the server or safely waiting on the phone, with a line to show. */
  onDone: (notice: string) => void;
}

/**
 * Add bill (spec 036 FR-016): photos or a pdf for a claim that exists. They go through the outbox like every send, as an
 * item that only uploads bills, so a lost connection never loses them.
 */
export function AddBillPanel({ claim, onCancel, onDone }: Props) {
  const outbox = useExpenseOutbox();
  const [bills, setBills] = useState<FormBill[]>([]);
  const [problem, setProblem] = useState<string | null>(null);

  const upload = useAction(async () => {
    setProblem(null);
    const now = new Date().toISOString();
    const item: OutboxItem = {
      itemId: Crypto.randomUUID(),
      ownerUserId: outbox.userId,
      clientRequestId: Crypto.randomUUID(),
      intent: "SAVE_DRAFT",
      createdAt: now,
      updatedAt: now,
      fields: {
        expenseDate: claim.expenseDate,
        amount: claim.amount,
        category: claim.category,
        zoneId: claim.zoneId,
        areaName: claim.areaName,
        note: claim.note ?? "",
        paidBy: claim.paidBy,
        reason: claim.reason ?? "",
        noBillReason: claim.noBillReason ?? "",
        link: null,
      },
      bills: bills.map(({ sourceUri: _unused, ...bill }) => {
        void _unused;
        return bill;
      }),
      serverClaimId: claim.id,
      serverVersion: claim.version,
      baseSnapshot: null,
      confirmDuplicate: false,
      state: "WAITING",
      stage: "BILLS",
      refusal: null,
      attempts: 0,
      nextTryAt: null,
    };
    const sources: Record<string, string> = {};
    for (const bill of bills) if (bill.sourceUri) sources[bill.localId] = bill.sourceUri;
    try {
      const outcome = await outbox.saveAndSend(item, sources);
      if (outcome.kind === "sent") onDone("Bills added.");
      else if (outcome.kind === "waiting") onDone(SAVED_OFFLINE_TEXT);
      else {
        setProblem(outcome.item.refusal?.reason ?? "The server refused these bills.");
        await outbox.remove(item.itemId);
      }
    } catch {
      setProblem(NOT_SAVED_TEXT);
    }
    return true;
  });

  return (
    <View style={styles.box}>
      <BillSlots bills={bills} serverBills={claim.bills} noBillReason="" onNoBillReason={() => undefined} onChange={setBills} disabled={upload.sending} hideNoBill />
      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}
      <View style={styles.row}>
        <Button mode="contained" onPress={() => void upload.run()} disabled={bills.length === 0 || upload.sending} loading={upload.sending} contentStyle={styles.button} accessibilityLabel="Upload bills">
          Upload bills
        </Button>
        <Button onPress={onCancel} disabled={upload.sending} contentStyle={styles.button} accessibilityLabel="Cancel adding bills">
          Cancel
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  row: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
