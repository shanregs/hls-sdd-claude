import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, ScrollView, StyleSheet, View } from "react-native";
import { Button, Chip, HelperText, Text } from "react-native-paper";
import { EXPENSES_ROUTE } from "../access/screenRegistry";
import { useMenuActions } from "../access/useMenuActions";
import type { ClaimStatus } from "../api/expensesApi";
import { Problem } from "../attendance/MonthPane";
import { ClaimCard } from "../expenses/ClaimCard";
import { ClaimForm, type FormResult, type FormSource } from "../expenses/ClaimForm";
import { formatRupees, STATUS_FILTERS } from "../expenses/expenseMessages";
import { useMyExpenses } from "../expenses/useMyExpenses";
import { WaitingSection } from "../expenses/WaitingSection";
import { useInnerBack } from "../navigation/InnerBack";
import { useExpenseOutbox } from "../outbox/ExpenseOutboxProvider";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/**
 * My Expenses (spec 036): the claimant's own claims with what is owed, a form to add one, and (inside the screen, Back closes
 * them first) the form and the claim detail. Every status, amount and button comes from the server.
 */
export function MyExpensesScreen() {
  const { userId, items } = useExpenseOutbox();
  const [status, setStatus] = useState<ClaimStatus | "ALL">("ALL");
  const [month] = useState<string | null>(null);
  const list = useMyExpenses(userId, status, month);
  const { canCreate } = useMenuActions(EXPENSES_ROUTE);
  const [form, setForm] = useState<FormSource | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  // When a waiting claim has been sent and is gone from the phone, the list loads again so it shows as Submitted.
  const itemCount = useRef(items.length);
  const { reload } = list;
  useEffect(() => {
    if (items.length < itemCount.current) reload({ quiet: true });
    itemCount.current = items.length;
  }, [items.length, reload]);

  const closeForm = (result?: FormResult) => {
    setForm(null);
    if (result?.notice) setNotice(result.notice);
    list.reload({ quiet: true });
  };
  useInnerBack(form !== null, () => setForm(null));

  if (form) {
    return (
      <Screen>
        <ClaimForm source={form} onClose={closeForm} />
      </Screen>
    );
  }

  return (
    <Screen onRefresh={() => list.reload({ quiet: true })}>
      {notice ? (
        <HelperText type="info" visible accessibilityRole="alert">
          {notice}
        </HelperText>
      ) : null}
      {list.owed !== null ? (
        <Text variant="titleMedium" accessibilityLabel={`Owed to you: ${formatRupees(list.owed)}`}>
          {`Owed to you: ${formatRupees(list.owed)}`}
        </Text>
      ) : null}
      <WaitingSection onOpen={(item) => { setNotice(null); setForm({ kind: "item", item }); }} />
      {canCreate ? (
        <Button mode="contained" onPress={() => { setNotice(null); setForm({ kind: "new" }); }} contentStyle={styles.button} accessibilityLabel="Add expense">
          Add expense
        </Button>
      ) : null}

      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips} accessibilityLabel="Choose a status">
        {STATUS_FILTERS.map((option) => (
          <Chip key={option.value} selected={option.value === status} showSelectedCheck onPress={() => setStatus(option.value)} accessibilityLabel={option.label}>
            {option.label}
          </Chip>
        ))}
      </ScrollView>

      {list.status === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading expenses" /> : null}
      {list.status === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => list.reload()} />
      ) : null}
      {list.status === "error" ? (
        <Problem title="Something went wrong" text={list.message ?? "Your expenses could not be loaded."} onRetry={() => list.reload()} />
      ) : null}

      {list.status === "ready" && list.rows.length === 0 ? (
        <Text variant="bodyMedium">{canCreate ? "No expenses yet. Choose Add expense to raise one." : "No expenses yet."}</Text>
      ) : null}
      {list.status === "ready" ? (
        <View style={styles.list}>
          {list.rows.map((row) => (
            <ClaimCard key={row.id} row={row} />
          ))}
          {list.moreProblem ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {list.moreProblem}
            </HelperText>
          ) : null}
          {list.rows.length < list.total ? (
            <Button onPress={list.loadMore} loading={list.loadingMore} disabled={list.loadingMore} contentStyle={styles.button}>
              Load more
            </Button>
          ) : null}
        </View>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { gap: spacingUnit, paddingVertical: spacingUnit / 2 },
  list: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
