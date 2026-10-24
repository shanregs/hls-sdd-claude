import { useState } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Button, Text, useTheme } from "react-native-paper";
import { formatIsoDate } from "../formats/dates";
import { ReasonDialog } from "../leave/ReasonDialog";
import { useExpenseOutbox } from "../outbox/ExpenseOutboxProvider";
import type { OutboxItem } from "../outbox/types";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { categoryInfo } from "./categories";
import { BATCH_WHEN_CONNECTED_TEXT, formatRupees, OUTBOX_WORDS } from "./expenseMessages";

/** The state of an item in words, with the batch note for a Training Stay that still needs one. */
export function itemStateText(item: OutboxItem, sending: boolean): string {
  if (sending) return OUTBOX_WORDS.SENDING;
  return OUTBOX_WORDS[item.state];
}

function ItemCard({ item, onOpen }: { item: OutboxItem; onOpen: (item: OutboxItem) => void }) {
  const theme = useTheme();
  const outbox = useExpenseOutbox();
  const [deleting, setDeleting] = useState(false);
  const sending = outbox.sendingId === item.itemId;
  const state = itemStateText(item, sending);
  const category = categoryInfo(item.fields.category).label;
  const refused = item.state === "REFUSED";
  const current = item.serverCurrent;
  const summary = `${category}, ${formatRupees(item.fields.amount)}, ${formatIsoDate(item.fields.expenseDate)}, ${state}`;

  return (
    <View style={[styles.card, { borderColor: refused ? theme.colors.error : theme.colors.outline, backgroundColor: theme.colors.surface }]}>
      <Pressable onPress={() => onOpen(item)} accessibilityRole="button" accessibilityLabel={`Waiting claim: ${summary}`} style={styles.open}>
        <View style={styles.line}>
          <Text variant="titleMedium">{category}</Text>
          <Text variant="titleMedium">{formatRupees(item.fields.amount)}</Text>
        </View>
        <Text variant="bodyMedium">{formatIsoDate(item.fields.expenseDate)}</Text>
        <Text variant="labelLarge" style={refused ? { color: theme.colors.error } : undefined}>
          {state}
        </Text>
        {item.fields.batchPending ? <Text variant="bodySmall">{BATCH_WHEN_CONNECTED_TEXT}</Text> : null}
        {item.refusal ? (
          <Text variant="bodyMedium" accessibilityRole="alert" style={{ color: theme.colors.error }}>
            {item.refusal.reason}
          </Text>
        ) : null}
        {current ? (
          <Text variant="bodySmall">
            {`Current claim on the server: ${formatRupees(current.amount)}, ${formatIsoDate(current.expenseDate)}${current.note ? `, ${current.note}` : ""}`}
          </Text>
        ) : null}
      </Pressable>

      <View style={styles.line}>
        {item.state === "NEEDS_CONFIRMATION" ? (
          <>
            <Button mode="contained" onPress={() => void outbox.confirmDuplicate(item.itemId)} contentStyle={styles.button} accessibilityLabel={`Confirm ${category} ${formatRupees(item.fields.amount)}`}>
              Confirm
            </Button>
            <Button mode="outlined" onPress={() => void outbox.remove(item.itemId)} contentStyle={styles.button} accessibilityLabel={`Keep ${category} ${formatRupees(item.fields.amount)} as Draft`}>
              Keep as Draft
            </Button>
          </>
        ) : null}
        {refused ? (
          <Button mode="outlined" onPress={() => void outbox.resend(item.itemId)} contentStyle={styles.button} accessibilityLabel={`Send again ${category} ${formatRupees(item.fields.amount)}`}>
            Send again
          </Button>
        ) : null}
        {item.state !== "NEEDS_CONFIRMATION" ? (
          <Button mode="outlined" onPress={() => setDeleting(true)} contentStyle={styles.button} accessibilityLabel={`Delete ${category} ${formatRupees(item.fields.amount)}`}>
            Delete
          </Button>
        ) : null}
      </View>

      {deleting ? (
        <ReasonDialog
          title="Delete this claim?"
          message="It and its photos will be removed from this phone. Nothing was sent to the server."
          confirmLabel="Delete"
          onConfirm={async () => {
            await outbox.remove(item.itemId);
            return null;
          }}
          onCancel={() => setDeleting(false)}
        />
      ) : null}
    </View>
  );
}

/**
 * The "Waiting to send" section at the top of the list (spec 036 FR-017, contracts/screens.md): only the signed-in user's
 * items, a count, a banner that says Sending or Waiting for a connection with Send now, and a card for each item. A claim
 * that could not be opened can only be deleted.
 */
export function WaitingSection({ onOpen }: { onOpen: (item: OutboxItem) => void }) {
  const outbox = useExpenseOutbox();
  const { items, unreadable, sendingId, waitingCount } = outbox;
  if (items.length === 0 && unreadable.length === 0) return null;
  const banner = sendingId ? "Sending..." : waitingCount > 0 ? "Waiting for a connection" : null;
  return (
    <View style={styles.section} accessibilityLabel="Waiting to send">
      <Text variant="titleMedium" accessibilityRole="header">
        {`Waiting to send (${items.length + unreadable.length})`}
      </Text>
      {banner ? (
        <View style={styles.line}>
          <Text variant="bodyMedium" accessibilityRole="alert">
            {banner}
          </Text>
          {!sendingId ? (
            <Button mode="outlined" onPress={() => void outbox.sendNow()} contentStyle={styles.button} accessibilityLabel="Send now">
              Send now
            </Button>
          ) : null}
        </View>
      ) : null}
      {items.map((item) => (
        <ItemCard key={item.itemId} item={item} onOpen={onOpen} />
      ))}
      {unreadable.map((id) => (
        <View key={id} style={styles.card}>
          <Text variant="bodyMedium">A saved claim could not be opened.</Text>
          <Button mode="outlined" onPress={() => void outbox.remove(id)} contentStyle={styles.button} accessibilityLabel="Delete the claim that could not be opened">
            Delete
          </Button>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  section: { gap: spacingUnit },
  card: { borderWidth: 1, borderRadius: spacingUnit, padding: spacingUnit * 1.5, gap: spacingUnit },
  open: { minHeight: minTouchTarget, gap: spacingUnit / 2 },
  line: { flexDirection: "row", flexWrap: "wrap", justifyContent: "space-between", alignItems: "center", gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
