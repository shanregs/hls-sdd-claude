import { useEffect, useState } from "react";
import { ActivityIndicator, Pressable, StyleSheet, View } from "react-native";
import { Button, Chip, Searchbar, Text, useTheme } from "react-native-paper";
import { getTeacherGrid, type TeacherGridRow } from "../api/attendanceApi";
import { NoConnectionError } from "../api/errors";
import { MonthPicker } from "../attendance/MonthPicker";
import { Problem } from "../attendance/MonthPane";
import { currentMonth } from "../attendance/monthRange";
import { serverNow } from "../attendance/serverClock";
import { useInnerBack } from "../navigation/InnerBack";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";
import { TeacherMonthScreen } from "./TeacherMonthScreen";

const PAGE_SIZE = 25;
const SEARCH_DELAY_MS = 300;

type ListState = "loading" | "ready" | "error" | "noConnection";

interface Result {
  key: string;
  state: Exclude<ListState, "loading">;
  rows: TeacherGridRow[];
  total: number;
  page: number;
}

/**
 * The Manager's assigned Teachers for a month, with the server's rollup per Teacher. Only the
 * Teachers the server returns are ever shown or opened (spec FR-014).
 */
export function TeacherAttendanceScreen() {
  const theme = useTheme();
  const [month, setMonth] = useState(() => currentMonth(serverNow()));
  const [query, setQuery] = useState("");
  const [search, setSearch] = useState("");
  const [result, setResult] = useState<Result | null>(null);
  const [tick, setTick] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [selected, setSelected] = useState<{ id: string; name: string } | null>(null);

  useInnerBack(selected !== null, () => setSelected(null));

  useEffect(() => {
    const timer = setTimeout(() => setSearch(query.trim()), SEARCH_DELAY_MS);
    return () => clearTimeout(timer);
  }, [query]);

  // A result belongs to the month, search and attempt it was loaded for; any other is "loading".
  const key = `${month}|${search}|${tick}`;
  const current = result?.key === key ? result : null;
  const state: ListState = current?.state ?? "loading";
  const rows = current?.rows ?? [];
  const total = current?.total ?? 0;

  useEffect(() => {
    let cancelled = false;
    getTeacherGrid({ month, query: search, page: 0, size: PAGE_SIZE })
      .then((loaded) => {
        if (cancelled) return;
        setResult({ key, state: "ready", rows: loaded.content, total: loaded.totalElements, page: 0 });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        setResult({
          key,
          state: error instanceof NoConnectionError ? "noConnection" : "error",
          rows: [],
          total: 0,
          page: 0,
        });
      });
    return () => {
      cancelled = true;
    };
  }, [month, search, key]);

  const loadMore = async () => {
    if (!current) return;
    setLoadingMore(true);
    try {
      const loaded = await getTeacherGrid({ month, query: search, page: current.page + 1, size: PAGE_SIZE });
      setResult((r) =>
        r?.key === key
          ? { ...r, rows: [...r.rows, ...loaded.content], total: loaded.totalElements, page: r.page + 1 }
          : r,
      );
    } catch (error) {
      setResult((r) =>
        r?.key === key ? { ...r, state: error instanceof NoConnectionError ? "noConnection" : "error" } : r,
      );
    } finally {
      setLoadingMore(false);
    }
  };

  if (selected) {
    return (
      <TeacherMonthScreen
        teacherId={selected.id}
        name={selected.name}
        initialMonth={month}
        onMonthChange={setMonth}
        onBack={() => setSelected(null)}
      />
    );
  }

  return (
    <Screen>
      <MonthPicker value={month} kind="current" onChange={setMonth} />
      <Searchbar
        placeholder="Search Teachers"
        value={query}
        onChangeText={setQuery}
        accessibilityLabel="Search Teachers"
      />

      {state === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading Teachers" /> : null}
      {state === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => setTick((n) => n + 1)} />
      ) : null}
      {state === "error" ? (
        <Problem title="Something went wrong" text="We couldn't load your Teachers." onRetry={() => setTick((n) => n + 1)} />
      ) : null}

      {state === "ready" && rows.length === 0 ? <Text variant="bodyLarge">No Teachers found</Text> : null}

      {state === "ready"
        ? rows.map((row) => (
            <Pressable
              key={row.teacherId}
              onPress={() => setSelected({ id: row.teacherId, name: row.name })}
              accessibilityRole="button"
              accessibilityLabel={`${row.name}, ${row.school?.name ?? "no school"}. Worked ${row.rollup.daysWorked}, leave ${row.rollup.daysLeave}, unmarked ${row.rollup.unmarked}${row.locked ? ", locked" : ""}`}
              style={[styles.row, { backgroundColor: theme.colors.surface, borderColor: theme.colors.outline }]}
            >
              <View style={styles.rowHead}>
                <Text variant="titleMedium">{row.name}</Text>
                {row.locked ? (
                  <Chip icon="lock" compact>
                    Locked
                  </Chip>
                ) : null}
              </View>
              <Text variant="bodyMedium">{row.school?.name ?? "No school"}</Text>
              <Text variant="bodySmall">
                Worked {row.rollup.daysWorked} · Leave {row.rollup.daysLeave} · Unmarked {row.rollup.unmarked}
              </Text>
            </Pressable>
          ))
        : null}

      {state === "ready" && rows.length < total ? (
        <Button
          mode="outlined"
          onPress={() => void loadMore()}
          loading={loadingMore}
          disabled={loadingMore}
          contentStyle={styles.button}
          accessibilityLabel="Load more"
        >
          Load more
        </Button>
      ) : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  row: {
    minHeight: minTouchTarget,
    padding: spacingUnit * 1.5,
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 8,
    gap: spacingUnit / 2,
  },
  rowHead: { flexDirection: "row", justifyContent: "space-between", alignItems: "center" },
  button: { minHeight: minTouchTarget },
});
