import * as Crypto from "expo-crypto";
import { useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import { deleteClaim, getClaim, withdrawClaim, type ClaimDetail as Claim, type ClaimRow } from "../api/expensesApi";
import { Problem } from "../attendance/MonthPane";
import { formatBusinessDateTime } from "../formats/manager";
import { formatIsoDate } from "../formats/dates";
import { kindText, managerRefusalText } from "../manager/managerMessages";
import { useExpenseOutbox } from "../outbox/ExpenseOutboxProvider";
import type { OutboxItem } from "../outbox/types";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { AddBillPanel } from "./AddBillPanel";
import { BillThumb } from "./BillThumb";
import { DuplicateDialog } from "./DuplicateDialog";
import { formatRupees, MAX_BILLS, MORE_DETAILS_TEXT, NEEDS_CONNECTION_TEXT } from "./expenseMessages";
import { WithdrawDialog } from "./WithdrawDialog";

type Loaded = { state: "ready"; id: string; claim: Claim } | { state: "offline" | "gone" | "error"; id: string; message: string };

interface Props {
  /** The row the claimant tapped: shown alone when the details cannot be loaded. */
  row: ClaimRow;
  /** Called after the server changed the claim, so the list can load again. */
  onChanged: (notice?: string) => void;
  onBack: () => void;
  /** Opens the form on the claim: Edit, or Correct and resubmit for a Rejected one. */
  onEdit: (claim: Claim) => void;
}

/** The facts the app has for a row, used while or instead of the full claim. */
function Facts({ claim }: { claim: ClaimRow & Partial<Claim> }) {
  return (
    <View style={styles.box}>
      <Text variant="headlineSmall" accessibilityRole="header">
        {`${claim.categoryLabel}, ${formatRupees(claim.amount)}`}
      </Text>
      <Text variant="bodyLarge">{`${claim.statusLabel}${claim.status === "SUBMITTED" && claim.ageDays !== null ? `, ${claim.ageDays} ${claim.ageDays === 1 ? "day" : "days"}` : ""}`}</Text>
      <Text variant="bodyMedium">{`Date: ${formatIsoDate(claim.expenseDate)}`}</Text>
      {claim.areaName ? <Text variant="bodyMedium">{`Area: ${claim.areaName}`}</Text> : null}
      <Text variant="bodyMedium">{`Paid by: ${claim.paidBy === "COMPANY" ? "Company" : "Myself"}`}</Text>
      {claim.reason ? <Text variant="bodyMedium">{`Reason: ${claim.reason}`}</Text> : null}
      {claim.note ? <Text variant="bodyMedium">{`Note: ${claim.note}`}</Text> : null}
      {claim.link?.label ?? claim.linkLabel ? <Text variant="bodyMedium">{`Linked work: ${claim.link?.label ?? claim.linkLabel}`}</Text> : null}
      {claim.noBillReason ? <Text variant="bodyMedium">{`No bill: ${claim.noBillReason}`}</Text> : null}
    </View>
  );
}

/** A claim built from the claim's own values, to send the claim through the outbox (Submit from the detail). */
function itemFor(claim: Claim, ownerUserId: string): OutboxItem {
  const now = new Date().toISOString();
  return {
    itemId: Crypto.randomUUID(),
    ownerUserId,
    clientRequestId: Crypto.randomUUID(),
    intent: "SUBMIT",
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
      link: claim.link ? { kind: claim.link.kind, ref: claim.link.ref, label: claim.link.label, date: claim.link.date } : null,
    },
    bills: [],
    serverClaimId: claim.id,
    serverVersion: claim.version,
    baseSnapshot: null,
    confirmDuplicate: false,
    state: "WAITING",
    stage: "SUBMIT",
    refusal: null,
    attempts: 0,
    nextTryAt: null,
  };
}

/**
 * One claim (spec 036 FR-014 to FR-016): the facts, the decision and payment for a decided claim, the bills, the history, and
 * a button only for each action the server lists in `allowedActions`. Nothing is shown as done before the server confirmed it;
 * Withdraw and Delete need a connection. No review action is ever shown.
 */
export function ClaimDetail({ row, onChanged, onBack, onEdit }: Props) {
  const outbox = useExpenseOutbox();
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [confirming, setConfirming] = useState<"withdraw" | "delete" | null>(null);
  const [adding, setAdding] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState<string | null>(null);
  const [duplicate, setDuplicate] = useState<{ itemId: string; matches: string[] } | null>(null);

  useEffect(() => {
    let cancelled = false;
    getClaim(row.id)
      .then((claim) => {
        if (!cancelled) setLoaded({ state: "ready", id: row.id, claim });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof NoConnectionError) setLoaded({ state: "offline", id: row.id, message: MORE_DETAILS_TEXT });
        else if (error instanceof ApiError && error.status === 404) setLoaded({ state: "gone", id: row.id, message: error.message });
        else setLoaded({ state: "error", id: row.id, message: managerRefusalText(error) });
      });
    return () => {
      cancelled = true;
    };
  }, [row.id, attempt]);

  const back = (
    <Button onPress={onBack} contentStyle={styles.button} accessibilityLabel="Back to the expenses">
      Back to the expenses
    </Button>
  );
  const current = loaded && loaded.id === row.id ? loaded : null;

  if (!current) {
    return (
      <View style={styles.box}>
        <ActivityIndicator accessibilityLabel="Loading the claim" />
        {back}
      </View>
    );
  }
  if (current.state === "offline") {
    return (
      <View style={styles.box}>
        <Facts claim={row} />
        <Text variant="bodyMedium">{current.message}</Text>
        <Button onPress={() => setAttempt((n) => n + 1)} contentStyle={styles.button} accessibilityLabel="Try again">
          Try again
        </Button>
        {back}
      </View>
    );
  }
  if (current.state !== "ready") {
    return (
      <View style={styles.box}>
        <Problem
          title={current.state === "gone" ? "Claim not found" : "Something went wrong"}
          text={current.message}
          onRetry={current.state === "error" ? () => setAttempt((n) => n + 1) : undefined}
        />
        {back}
      </View>
    );
  }

  const claim = current.claim;
  const replace = (next: Claim, notice?: string) => {
    setLoaded({ state: "ready", id: row.id, claim: next });
    setDone(notice ?? null);
    onChanged(notice);
  };
  const can = (action: string) => claim.allowedActions.includes(action);
  const liveBills = claim.bills.filter((b) => !b.removed).length;

  const withdraw = async (): Promise<string | null> => {
    try {
      replace(await withdrawClaim(claim.id, claim.version), "Withdrawn. It is a Draft again.");
      setConfirming(null);
      return null;
    } catch (error) {
      return error instanceof NoConnectionError ? NEEDS_CONNECTION_TEXT : managerRefusalText(error);
    }
  };
  const remove = async (): Promise<string | null> => {
    try {
      await deleteClaim(claim.id);
      setConfirming(null);
      onChanged("Deleted.");
      onBack();
      return null;
    } catch (error) {
      return error instanceof NoConnectionError ? NEEDS_CONNECTION_TEXT : managerRefusalText(error);
    }
  };

  /** Submit goes through the outbox like every send, so it also works with a weak connection. */
  const submit = async () => {
    if (busy) return;
    setBusy(true);
    setProblem(null);
    const item = itemFor(claim, outbox.userId);
    try {
      const outcome = await outbox.saveAndSend(item);
      if (outcome.kind === "sent") {
        setAttempt((n) => n + 1);
        setDone("Submitted.");
        onChanged("Submitted.");
      } else if (outcome.kind === "refused") {
        setProblem(outcome.item.refusal?.reason ?? "The server refused this claim.");
        await outbox.remove(item.itemId);
      } else if (outcome.kind === "confirm") {
        setDuplicate({ itemId: item.itemId, matches: outcome.item.refusal?.matches ?? [] });
      } else {
        onChanged("Saved. It will be sent when you are back online.");
        onBack();
      }
    } catch {
      setProblem("Not saved. Free some space and try again.");
    } finally {
      setBusy(false);
    }
  };

  return (
    <View style={styles.box}>
      {done ? (
        <HelperText type="info" visible accessibilityRole="alert">
          {done}
        </HelperText>
      ) : null}
      <Facts claim={claim} />

      {claim.decision && claim.decision.at ? (
        <View style={styles.box} accessibilityLabel="Decision">
          <Text variant="titleSmall" accessibilityRole="header">
            Decision
          </Text>
          <Text variant="bodyMedium">
            {`${claim.statusLabel}${claim.decision.byName ? ` by ${claim.decision.byName}` : ""} on ${formatBusinessDateTime(claim.decision.at)}`}
          </Text>
          {claim.decision.reason ? <Text variant="bodyMedium">{`Reason: ${claim.decision.reason}`}</Text> : null}
        </View>
      ) : null}

      {claim.payment ? (
        <View style={styles.box} accessibilityLabel="Payment">
          <Text variant="titleSmall" accessibilityRole="header">
            Payment
          </Text>
          {claim.payment.how ? <Text variant="bodyMedium">{`How: ${claim.payment.how}`}</Text> : null}
          {claim.payment.paidOn ? <Text variant="bodyMedium">{`Paid on: ${formatIsoDate(claim.payment.paidOn)}`}</Text> : null}
          {claim.payment.mode ? <Text variant="bodyMedium">{`Mode: ${claim.payment.mode}`}</Text> : null}
          {claim.payment.reference ? <Text variant="bodyMedium">{`Reference: ${claim.payment.reference}`}</Text> : null}
        </View>
      ) : null}

      <View style={styles.box}>
        <Text variant="titleSmall" accessibilityRole="header">
          Bills
        </Text>
        <Text variant="bodySmall">{`${liveBills} of ${MAX_BILLS}`}</Text>
        {claim.bills.length === 0 ? <Text variant="bodyMedium">{claim.noBillReason ? "No bill." : "No bills yet."}</Text> : null}
        {claim.bills.map((bill, index) => (
          <BillThumb key={bill.id} claimId={claim.id} bill={bill} index={index} />
        ))}
      </View>

      <View style={styles.box}>
        <Text variant="titleSmall" accessibilityRole="header">
          History
        </Text>
        {claim.history.map((line, index) => (
          <Text key={`${line.at}-${index}`} variant="bodySmall">
            {`${formatBusinessDateTime(line.at)}, ${line.actorName ?? "System"}: ${kindText(line.event)}${line.reason ? `, ${line.reason}` : ""}`}
          </Text>
        ))}
      </View>

      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}

      {adding ? (
        <AddBillPanel
          claim={claim}
          onCancel={() => setAdding(false)}
          onDone={(notice) => {
            setAdding(false);
            setAttempt((n) => n + 1);
            setDone(notice);
            onChanged(notice);
          }}
        />
      ) : (
        <View style={styles.row}>
          {can("EDIT") ? (
            <Button mode="contained" onPress={() => onEdit(claim)} contentStyle={styles.button} accessibilityLabel={claim.status === "REJECTED" ? "Correct and resubmit" : "Edit"}>
              {claim.status === "REJECTED" ? "Correct and resubmit" : "Edit"}
            </Button>
          ) : null}
          {can("SUBMIT") ? (
            <Button mode="contained" onPress={() => void submit()} disabled={busy} loading={busy} contentStyle={styles.button} accessibilityLabel="Submit claim">
              Submit
            </Button>
          ) : null}
          {can("ADD_BILL") ? (
            <Button mode="outlined" onPress={() => setAdding(true)} contentStyle={styles.button} accessibilityLabel="Add bill">
              Add bill
            </Button>
          ) : null}
          {can("WITHDRAW") ? (
            <Button mode="outlined" onPress={() => setConfirming("withdraw")} contentStyle={styles.button} accessibilityLabel="Withdraw claim">
              Withdraw
            </Button>
          ) : null}
          {can("DELETE") ? (
            <Button mode="outlined" onPress={() => setConfirming("delete")} contentStyle={styles.button} accessibilityLabel="Delete claim">
              Delete
            </Button>
          ) : null}
        </View>
      )}
      {back}

      {confirming ? <WithdrawDialog kind={confirming} onConfirm={confirming === "withdraw" ? withdraw : remove} onCancel={() => setConfirming(null)} /> : null}
      {duplicate ? (
        <DuplicateDialog
          matches={duplicate.matches}
          confirmLabel="Submit anyway"
          dismissLabel="Cancel"
          onConfirm={() => {
            const id = duplicate.itemId;
            setDuplicate(null);
            void outbox.confirmDuplicate(id).then((outcome) => {
              if (outcome.kind === "sent") {
                setAttempt((n) => n + 1);
                setDone("Submitted.");
                onChanged("Submitted.");
              } else if (outcome.kind === "refused") {
                setProblem(outcome.item.refusal?.reason ?? "The server refused this claim.");
                void outbox.remove(id);
              }
            });
          }}
          onDismiss={() => {
            void outbox.remove(duplicate.itemId);
            setDuplicate(null);
          }}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  row: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
