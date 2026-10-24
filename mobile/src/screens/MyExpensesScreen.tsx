import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, ScrollView, StyleSheet, View } from "react-native";
import { Button, Chip, HelperText, Text } from "react-native-paper";
import { EXPENSES_ROUTE } from "../access/screenRegistry";
import { useMenuActions } from "../access/useMenuActions";
import type { ClaimRow, ClaimStatus } from "../api/expensesApi";
import { Problem } from "../attendance/MonthPane";
import { MonthPicker } from "../attendance/MonthPicker";
import { currentMonth } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { ClaimCard } from "../expenses/ClaimCard";
import { ClaimDetail } from "../expenses/ClaimDetail";
import { ClaimForm, type FormResult, type FormSource } from "../expenses/ClaimForm";
import { formatRupees, STATUS_FILTERS } from "../expenses/expenseMessages";
import { useMyExpenses } from "../expenses/useMyExpenses";
import { WaitingSection } from "../expenses/WaitingSection";
import { formatBusinessDateTime } from "../formats/manager";
import { useInnerBack } from "../navigation/InnerBack";
import { useExpenseOutbox } from "../outbox/ExpenseOutboxProvider";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/**
 * My Expenses (spec 036): the claimant's own claims with what is owed, a status filter and a month, the claims waiting to be
 * sent, a form to add one, and (inside the screen, Back closes them first) the form and the claim detail. Every status,
 * amount and button comes from the server.
 */
export function MyExpensesScreen() {
  const { userId, items } = useExpenseOutbox();
  const [status, setStatus] = useState<ClaimStatus | "ALL">("ALL");
  const [month, setMonth] = useState<string | null>(null);
  const [choosingMonth, setChoosingMonth] = useState(false);
  const list = useMyExpenses(userId, status, month);
  const { canCreate } = useMenuActions(EXPENSES_ROUTE);
  const [form, setForm] = useState<FormSource | null>(null);
  const [open, setOpen] = useState<ClaimRow | null>(null);
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
    // A form opened from a claim's detail returns to the list; the detail reads the claim again when it is opened.
    setOpen(null);
    list.reload({ quiet: true });
  };
  const closeDetail = () => {
    setOpen(null);
    list.reload({ quiet: true });
  };
  useInnerBack(form !== null || open !== null, () => (form ? setForm(null) : closeDetail()));

  if (form) {
    return (
      <Screen>
        <ClaimForm source={form} onClose={closeForm} />
      </Screen>
    );
  }

  if (open) {
    return (
      <Screen>
        <ClaimDetail
          row={open}
          onChanged={(text) => {
            if (text) setNotice(text);
            list.reload({ quiet: true });
          }}
          onBack={closeDetail}
          onEdit={(claim) => setForm({ kind: "claim", claim })}
        />
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
      {list.outOfDate ? (
        <HelperText type="info" visible accessibilityRole="alert">
          {`May be out of date, loaded ${formatBusinessDateTime(list.outOfDate)}`}
        </HelperText>
      ) : null}

      <WaitingSection
        onOpen={(item) => {
          setNotice(null);
          setForm({ kind: "item", item });
        }}
      />

      {canCreate ? (
        <Button
          mode="contained"
          onPress={() => {
            setNotice(null);
            setForm({ kind: "new" });
          }}
          contentStyle={styles.button}
          accessibilityLabel="Add expense"
        >
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

      <View style={styles.month}>
        <Button mode="outlined" onPress={() => setChoosingMonth((v) => !v)} contentStyle={styles.button} accessibilityLabel={month ? "Filter by month, on" : "Filter by month, off"}>
          {month ? "Month filter on" : "All months"}
        </Button>
        {month ? (
          <Button
            onPress={() => {
              setMonth(null);
              setChoosingMonth(false);
            }}
            contentStyle={styles.button}
            accessibilityLabel="Show all months"
          >
            Show all months
          </Button>
        ) : null}
      </View>
      {choosingMonth ? <MonthPicker value={month ?? currentMonth(serverNow())} kind="history" onChange={setMonth} /> : null}

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
            <ClaimCard
              key={row.id}
              row={row}
              onPress={() => {
                setNotice(null);
                setOpen(row);
              }}
            />
          ))}
          {list.moreProblem ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {list.moreProblem}
            </HelperText>
          ) : null}
          {list.rows.length < list.total ? (
            <Button onPress={list.loadMore} loading={list.loadingMore} disabled={list.loadingMore} contentStyle={styles.button} accessibilityLabel="Load more">
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
  month: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  list: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
