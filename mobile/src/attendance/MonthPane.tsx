import { useCallback, useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Text } from "react-native-paper";
import {
  listStatusCodes,
  type DayHistoryEntry,
  type DayView,
  type EditableBy,
  type MarkRequest,
  type StatusCode,
  type TeacherMonthView,
} from "../api/attendanceApi";
import { ApiError, NoConnectionError } from "../api/errors";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { DayHistoryList } from "./DayHistoryList";
import { DayLegend } from "./DayLegend";
import { DaySheet } from "./DaySheet";
import { MonthGrid } from "./MonthGrid";
import { MonthPicker } from "./MonthPicker";
import { RollupStrip } from "./RollupStrip";
import type { Viewer } from "./dayPresentation";
import { currentMonth, type RangeKind } from "./monthRange";
import { isStaleRefusal, refusalText } from "./refusalMessages";
import { serverNow } from "./serverClock";
import { useMonthView } from "./useMonthView";

/** What a screen can do to a day. Each call throws when the server refuses or cannot be reached. */
export interface DayActions {
  /** The server's `editableBy` value that lets this viewer change a day: "SELF" or "SUPERVISOR". */
  editableBy: EditableBy;
  save: (date: string, body: MarkRequest) => Promise<unknown>;
  clear?: (date: string) => Promise<unknown>;
  history?: (date: string) => Promise<DayHistoryEntry[]>;
}

interface Props {
  kind: RangeKind;
  viewer: Viewer;
  loader: (month: string) => Promise<TeacherMonthView>;
  initialMonth?: string;
  onMonthChange?: (month: string) => void;
  /** Omit for a read-only view: no day is ever offered for change. */
  actions?: DayActions;
  /** Shown alone for a 404, in place of the server's text (a Manager must not learn why a Teacher is hidden). */
  notFoundText?: string;
}

/** Plain text for a failed save or clear; the server's refusal wording comes from `refusalText`. */
export function failureText(error: unknown): string {
  if (error instanceof NoConnectionError) return "No connection. Your change was not saved.";
  if (error instanceof ApiError && error.status === 409) return refusalText(error.message);
  if (error instanceof ApiError && error.status === 403) return "You are not allowed to change this day.";
  if (error instanceof ApiError && (error.status === 400 || error.status === 404)) return refusalText(error.message);
  return "Something went wrong. Your change was not saved. Please try again.";
}

/**
 * One month of a person's attendance: month picker, totals, the day grid, a legend and the day
 * sheet. Used by My Attendance, Attendance History and a Manager's view of one Teacher. Every
 * figure and every permission comes from the server's response; nothing is worked out here.
 */
export function MonthPane({ kind, viewer, loader, initialMonth, onMonthChange, actions, notFoundText }: Props) {
  const [month, setMonth] = useState(() => initialMonth ?? currentMonth(serverNow()));
  const { status, view, message, reload } = useMonthView(loader, month);
  const [selected, setSelected] = useState<string | null>(null);
  const [statuses, setStatuses] = useState<StatusCode[] | null>(null);
  const [statusesFailed, setStatusesFailed] = useState(false);
  const [history, setHistory] = useState<{ date: string; entries: DayHistoryEntry[] | "failed" } | null>(null);

  const day: DayView | undefined = view?.days.find((d) => d.date === selected);
  const editable = Boolean(actions && day && day.editableBy === actions.editableBy);

  const fetchStatuses = useCallback(() => {
    listStatusCodes()
      .then((all) => {
        // Holiday-type codes are set by the calendar, not chosen by a person.
        setStatuses(all.filter((s) => s.active && s.category !== "NON_WORKING"));
      })
      .catch(() => setStatusesFailed(true));
  }, []);

  useEffect(() => {
    if (editable && statuses === null && !statusesFailed) fetchStatuses();
  }, [editable, statuses, statusesFailed, fetchStatuses]);

  const retryStatuses = () => {
    setStatusesFailed(false);
    fetchStatuses();
  };

  const changeMonth = (next: string) => {
    setSelected(null);
    setHistory(null);
    setMonth(next);
    onMonthChange?.(next);
  };

  const close = () => {
    setSelected(null);
    setHistory(null);
  };

  const save = async (body: MarkRequest): Promise<string | null> => {
    if (!actions || !day) return null;
    try {
      await actions.save(day.date, body);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409 && isStaleRefusal(error.message)) {
        reload({ quiet: true });
      }
      return failureText(error);
    }
    close();
    reload({ quiet: true });
    return null;
  };

  const clear = async (): Promise<string | null> => {
    if (!actions?.clear || !day) return null;
    try {
      await actions.clear(day.date);
    } catch (error) {
      return failureText(error);
    }
    close();
    reload({ quiet: true });
    return null;
  };

  const showHistory = async () => {
    if (!actions?.history || !day) return;
    const date = day.date;
    try {
      setHistory({ date, entries: await actions.history(date) });
    } catch {
      setHistory({ date, entries: "failed" });
    }
  };

  return (
    <View style={styles.pane}>
      <MonthPicker value={month} kind={kind} onChange={changeMonth} />

      {status === "loading" ? (
        <ActivityIndicator size="large" accessibilityLabel="Loading attendance" />
      ) : null}

      {status === "noConnection" ? (
        <Problem
          title="No connection"
          text="Check your internet connection and try again."
          onRetry={() => reload()}
        />
      ) : null}
      {status === "error" ? (
        <Problem
          title="Something went wrong"
          text={message ?? "We couldn't load this month."}
          onRetry={() => reload()}
        />
      ) : null}
      {status === "notFound" ? <Problem title={notFoundText ?? "Not found"} text={notFoundText ? "" : (message ?? "")} /> : null}

      {status === "ready" && view ? (
        <>
          <RollupStrip rollup={view.rollup} locked={view.locked} />
          <MonthGrid days={view.days} viewer={viewer} onSelect={(d) => setSelected(d.date)} />
          <DayLegend />
        </>
      ) : null}

      {day ? (
        <DaySheet
          key={day.date}
          day={day}
          viewer={viewer}
          editable={editable}
          statuses={statuses}
          statusesFailed={statusesFailed}
          onRetryStatuses={retryStatuses}
          onSave={save}
          onClear={editable && actions?.clear ? clear : undefined}
          onHistory={actions?.history ? () => void showHistory() : undefined}
          history={
            history && history.date === day.date ? (
              history.entries === "failed" ? (
                <Text variant="bodyMedium">We couldn't load the history.</Text>
              ) : (
                <DayHistoryList entries={history.entries} />
              )
            ) : null
          }
          onClose={close}
        />
      ) : null}
    </View>
  );
}

function Problem({ title, text, onRetry }: { title: string; text: string; onRetry?: () => void }) {
  return (
    <View style={styles.problem} accessibilityRole="alert">
      <Text variant="titleMedium">{title}</Text>
      {text ? <Text variant="bodyMedium">{text}</Text> : null}
      {onRetry ? (
        <Button mode="contained" onPress={onRetry} contentStyle={styles.buttonContent} accessibilityLabel="Retry">
          Retry
        </Button>
      ) : null}
    </View>
  );
}

export { Problem };

const styles = StyleSheet.create({
  pane: { gap: spacingUnit * 2 },
  problem: { gap: spacingUnit },
  buttonContent: { minHeight: minTouchTarget },
});
