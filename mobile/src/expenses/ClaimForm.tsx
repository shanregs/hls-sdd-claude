import * as Crypto from "expo-crypto";
import { useMemo, useRef, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import type { Area, CategoryKey, ClaimDetail, PaidBy } from "../api/expensesApi";
import { businessToday } from "../attendance/serverClock";
import { DateChooser } from "../leave/DateChooser";
import { useAction } from "../manager/useAction";
import { useExpenseOutbox, type SendOutcome } from "../outbox/ExpenseOutboxProvider";
import { KeyUnreadableError } from "../outbox/cipher";
import { StorageFullError } from "../outbox/outboxStore";
import type { OutboxBill, OutboxFields, OutboxItem } from "../outbox/types";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { AreaField, useAreas } from "./AreaField";
import { BillSlots, type FormBill } from "./BillSlots";
import { CATEGORIES, categoryInfo } from "./categories";
import { DuplicateDialog } from "./DuplicateDialog";
import { NOT_SAVED_TEXT, SAVED_OFFLINE_TEXT } from "./expenseMessages";
import { LinkField, useLinkChoices, type LinkValue } from "./LinkField";

/** What the form is opened with. */
export type FormSource =
  | { kind: "new" }
  /** A claim on the server: a Draft to edit, or a Rejected claim to correct and resubmit. */
  | { kind: "claim"; claim: ClaimDetail }
  /** A waiting or refused item saved on the phone. */
  | { kind: "item"; item: OutboxItem };

export interface FormResult {
  /** A line for the screen to show once the form is closed. */
  notice?: string;
  /** True when the claim reached the server (so the list should load again). */
  sent: boolean;
}

interface Props {
  source: FormSource;
  onClose: (result?: FormResult) => void;
}

const AMOUNT_PATTERN = /^\d+(\.\d{1,2})?$/;
const cleanAmount = (text: string) => text.replace(/[₹,\s]/g, "");
const dayNumber = (iso: string) => Math.floor(Date.parse(`${iso}T00:00:00Z`) / 86_400_000);

function initial(source: FormSource, today: string) {
  if (source.kind === "claim") {
    const c = source.claim;
    return {
      fields: {
        expenseDate: c.expenseDate,
        amount: c.amount,
        category: c.category,
        zoneId: c.zoneId,
        areaName: c.areaName,
        note: c.note ?? "",
        paidBy: c.paidBy,
        reason: c.reason ?? "",
        noBillReason: c.noBillReason ?? "",
        link: c.link ? { kind: c.link.kind, ref: c.link.ref, label: c.link.label, date: c.link.date } : null,
      } satisfies OutboxFields,
      bills: [] as FormBill[],
      serverBills: c.bills,
    };
  }
  if (source.kind === "item") {
    return {
      fields: source.item.fields,
      bills: source.item.bills.map<FormBill>((b) => ({ ...b })),
      serverBills: [],
    };
  }
  return {
    fields: {
      expenseDate: today,
      amount: "",
      category: "TRAVEL_FUEL" as CategoryKey,
      zoneId: null,
      areaName: "Head office",
      note: "",
      paidBy: "CLAIMANT" as PaidBy,
      reason: "",
      noBillReason: "",
      link: null,
    } satisfies OutboxFields,
    bills: [] as FormBill[],
    serverBills: [],
  };
}

function titleOf(source: FormSource): string {
  if (source.kind === "new") return "Add expense";
  if (source.kind === "item") return "Edit waiting claim";
  return source.claim.status === "REJECTED" ? "Correct and resubmit" : "Edit expense";
}

/**
 * The claim form (spec 036 FR-001 to FR-012): new, edit a Draft, correct a Rejected claim, or edit a waiting item. Save and
 * Submit write the claim to the outbox first and then wait for that item's result when online; the form closes only after the
 * server confirmed the last step, or when the claim is safely waiting on the phone. Every rule is the server's; the hints
 * here (the 60-day range, a missing reason) never block a send the server would accept.
 */
export function ClaimForm({ source, onClose }: Props) {
  const outbox = useExpenseOutbox();
  const today = useMemo(() => businessToday(), []);
  const start = useMemo(() => initial(source, today), [source, today]);
  const [fields, setFields] = useState<OutboxFields>(start.fields);
  const [bills, setBills] = useState<FormBill[]>(start.bills);
  // A new claim starts with no category chosen, so a wrong one is never submitted by habit.
  const [categoryChosen, setCategoryChosen] = useState(source.kind !== "new");
  const [problem, setProblem] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState<string[] | null>(null);
  const savedItem = useRef<OutboxItem | null>(source.kind === "item" ? source.item : null);
  const storedBills = useRef(new Set<string>(source.kind === "item" ? source.item.bills.map((b) => b.localId) : []));
  const sentIntent = useRef<"SAVE_DRAFT" | "SUBMIT">("SUBMIT");

  const areas = useAreas(outbox.userId);
  const choices = useLinkChoices(outbox.userId, categoryChosen ? fields.category : "OTHER", fields.expenseDate);
  const info = categoryInfo(fields.category);
  const set = <K extends keyof OutboxFields>(key: K, value: OutboxFields[K]) => setFields((f) => ({ ...f, [key]: value }));

  // Changing the category or the date reloads the choices; a choice that is no longer offered is cleared.
  const linkStillValid = fields.link === null || choices.status !== "ready" || choices.options.some((o) => o.ref === fields.link?.ref);
  const link: LinkValue | null = info.linkOptional || info.batchRequired ? (linkStillValid ? fields.link : null) : null;

  const amount = cleanAmount(fields.amount);
  const amountOk = AMOUNT_PATTERN.test(amount) && Number(amount) > 0;
  const age = dayNumber(today) - dayNumber(fields.expenseDate);
  const dateHint = age > 60 ? "This is more than 60 days ago. It will probably be refused." : age < 0 ? "This date is in the future. It will be refused." : null;
  const billCount = bills.length + start.serverBills.filter((b) => !b.removed).length;
  const batchOk = !info.batchRequired || link !== null || (choices.status === "ready" && choices.unavailable);
  const baseOk = amountOk && fields.expenseDate !== "" && categoryChosen;
  const canSave = baseOk;
  const canSubmit =
    baseOk &&
    (!info.reasonRequired || fields.reason.trim() !== "") &&
    batchOk &&
    (billCount > 0 || fields.noBillReason.trim() !== "");

  const buildItem = (intent: "SAVE_DRAFT" | "SUBMIT"): OutboxItem => {
    const previous = savedItem.current;
    const claim = source.kind === "claim" ? source.claim : null;
    const serverClaimId = previous?.serverClaimId ?? claim?.id ?? null;
    const serverCurrent = previous?.serverCurrent;
    const outboxBills: OutboxBill[] = bills.map(({ sourceUri: _unused, ...bill }) => {
      void _unused;
      return bill;
    });
    const now = new Date().toISOString();
    return {
      itemId: previous?.itemId ?? Crypto.randomUUID(),
      ownerUserId: outbox.userId,
      clientRequestId: previous?.clientRequestId ?? Crypto.randomUUID(),
      intent,
      createdAt: previous?.createdAt ?? now,
      updatedAt: now,
      fields: {
        ...fields,
        amount: Number(amount).toFixed(2),
        link,
        reason: info.reasonRequired ? fields.reason : "",
        batchPending: info.batchRequired && link === null,
      },
      bills: outboxBills,
      serverClaimId,
      // Re-applying a change after the server's claim moved on starts from the server's current version.
      serverVersion: serverCurrent?.version ?? previous?.serverVersion ?? claim?.version ?? null,
      baseSnapshot:
        previous?.baseSnapshot ??
        (claim ? { expenseDate: claim.expenseDate, amount: claim.amount, categoryLabel: claim.categoryLabel, note: claim.note } : null),
      confirmDuplicate: false,
      state: "WAITING",
      stage: serverClaimId ? "UPDATE" : "CREATE",
      refusal: null,
      attempts: 0,
      nextTryAt: null,
      serverCurrent: null,
    };
  };

  const finish = (outcome: SendOutcome) => {
    if (outcome.kind === "sent") {
      onClose({ sent: true, notice: sentIntent.current === "SUBMIT" ? "Submitted." : "Saved as a draft." });
      return;
    }
    savedItem.current = outcome.item;
    if (outcome.kind === "waiting") {
      onClose({ sent: false, notice: SAVED_OFFLINE_TEXT });
    } else if (outcome.kind === "refused") {
      setProblem(outcome.item.refusal?.reason ?? "The server refused this claim.");
    } else {
      setDuplicate(outcome.item.refusal?.matches ?? []);
    }
  };

  const send = useAction(async (intent: "SAVE_DRAFT" | "SUBMIT") => {
    setProblem(null);
    sentIntent.current = intent;
    const item = buildItem(intent);
    const sources: Record<string, string> = {};
    for (const bill of bills) {
      if (bill.sourceUri && !storedBills.current.has(bill.localId)) sources[bill.localId] = bill.sourceUri;
    }
    let outcome: SendOutcome;
    try {
      outcome = await outbox.saveAndSend(item, sources, savedItem.current !== null);
    } catch (error) {
      if (error instanceof StorageFullError) setProblem(NOT_SAVED_TEXT);
      else if (error instanceof KeyUnreadableError) setProblem("The saved claims on this phone cannot be opened, so nothing can be saved now.");
      else setProblem(NOT_SAVED_TEXT);
      return false;
    }
    for (const bill of bills) storedBills.current.add(bill.localId);
    savedItem.current = item;
    finish(outcome);
    return true;
  });

  const confirm = useAction(async () => {
    const item = savedItem.current;
    if (!item) return false;
    const outcome = await outbox.confirmDuplicate(item.itemId);
    setDuplicate(null);
    finish(outcome);
    return true;
  });

  const busy = send.sending || confirm.sending;
  const pickArea = (area: Area) => setFields((f) => ({ ...f, zoneId: area.id, areaName: area.name }));

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        {titleOf(source)}
      </Text>

      {source.kind === "item" && source.item.state === "REFUSED" && source.item.refusal ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {`Not sent: ${source.item.refusal.reason}`}
        </HelperText>
      ) : null}
      {source.kind === "claim" && source.claim.decision?.reason ? (
        <HelperText type="info" visible>
          {`Rejected: ${source.claim.decision.reason}`}
        </HelperText>
      ) : null}

      <TextInput
        mode="outlined"
        label="Amount"
        accessibilityLabel="Amount"
        value={fields.amount}
        onChangeText={(text) => set("amount", text)}
        keyboardType="decimal-pad"
        left={<TextInput.Affix text="₹" />}
        error={fields.amount !== "" && !amountOk}
        disabled={busy}
      />
      {fields.amount !== "" && !amountOk ? (
        <HelperText type="error" visible>
          Enter the amount in rupees, with at most two decimals.
        </HelperText>
      ) : null}

      <DateChooser label="Date" value={fields.expenseDate} onChange={(date) => set("expenseDate", date)} />
      {dateHint ? <HelperText type="info" visible>{dateHint}</HelperText> : null}

      <Text variant="titleSmall" accessibilityRole="header">
        Category
      </Text>
      <RadioButton.Group
        value={categoryChosen ? fields.category : ""}
        onValueChange={(value) => {
          setCategoryChosen(true);
          setFields((f) => ({ ...f, category: value as CategoryKey, link: null }));
        }}
      >
        {CATEGORIES.map((category) => (
          <RadioButton.Item key={category.key} label={category.label} value={category.key} accessibilityLabel={category.label} disabled={busy} style={styles.item} />
        ))}
      </RadioButton.Group>

      {info.reasonRequired ? (
        <TextInput
          mode="outlined"
          label="Reason"
          accessibilityLabel="Reason"
          value={fields.reason}
          onChangeText={(text) => set("reason", text)}
          maxLength={500}
          multiline
          disabled={busy}
        />
      ) : null}

      <LinkField category={categoryChosen ? fields.category : "OTHER"} choices={choices} value={link} onChange={(value) => set("link", value)} disabled={busy} />
      <AreaField areas={areas} value={fields.zoneId} onChange={pickArea} disabled={busy} />

      <TextInput
        mode="outlined"
        label="Note"
        accessibilityLabel="Note"
        value={fields.note}
        onChangeText={(text) => set("note", text)}
        maxLength={1000}
        multiline
        disabled={busy}
      />

      <Text variant="titleSmall" accessibilityRole="header">
        Paid by
      </Text>
      <RadioButton.Group value={fields.paidBy} onValueChange={(value) => set("paidBy", value as PaidBy)}>
        <RadioButton.Item label="Myself" value="CLAIMANT" accessibilityLabel="Paid by myself" disabled={busy} style={styles.item} />
        <RadioButton.Item label="Company" value="COMPANY" accessibilityLabel="Paid by company" disabled={busy} style={styles.item} />
      </RadioButton.Group>

      <BillSlots
        bills={bills}
        serverBills={start.serverBills}
        noBillReason={fields.noBillReason}
        onNoBillReason={(text) => set("noBillReason", text)}
        onChange={setBills}
        disabled={busy}
      />

      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}

      <View style={styles.row}>
        <Button mode="outlined" onPress={() => void send.run("SAVE_DRAFT")} disabled={!canSave || busy} contentStyle={styles.button} accessibilityLabel="Save as draft">
          Save as draft
        </Button>
        <Button mode="contained" onPress={() => void send.run("SUBMIT")} disabled={!canSubmit || busy} loading={send.sending} contentStyle={styles.button} accessibilityLabel="Submit">
          Submit
        </Button>
      </View>
      <Button onPress={() => onClose()} disabled={busy} contentStyle={styles.button} accessibilityLabel="Cancel">
        Cancel
      </Button>

      {duplicate ? (
        <DuplicateDialog
          matches={duplicate}
          confirmLabel="Submit anyway"
          dismissLabel="Cancel"
          busy={confirm.sending}
          onConfirm={() => void confirm.run()}
          onDismiss={() => setDuplicate(null)}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  row: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  item: { minHeight: minTouchTarget },
  button: { minHeight: minTouchTarget },
});
