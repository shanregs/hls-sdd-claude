import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, Checkbox, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import { getCalendar, type AttendanceCalendar } from "../api/attendanceApi";
import { ApiError, NoConnectionError } from "../api/errors";
import {
  listLeaveTypes,
  previewLeave,
  submitLeave,
  type LeavePreview,
  type LeaveType,
} from "../api/leaveApi";
import { Problem } from "../attendance/MonthPane";
import { DateChooser } from "../leave/DateChooser";
import { PreviewPanel } from "../leave/PreviewPanel";
import { appProblems, canCheck, canSubmit, editDraft, emptyDraft, toBody, type LeaveDraft } from "../leave/draft";
import { leaveRefusalText } from "../leave/leaveMessages";
import { MAX_LEAVE_TEXT } from "../leave/ReasonDialog";
import type { OpenRoute } from "../navigation/AppShell";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

type TypesState =
  | { state: "loading" }
  | { state: "ready"; types: LeaveType[] }
  | { state: "error" }
  | { state: "noConnection" }
  | { state: "notFound"; message: string };

const NO_CONNECTION = "No connection. Your request was not submitted.";

function failureText(error: unknown): string {
  if (error instanceof NoConnectionError) return NO_CONNECTION;
  if (error instanceof ApiError && (error.status === 400 || error.status === 409 || error.status === 404)) {
    return leaveRefusalText(error.message);
  }
  return "Something went wrong. Your request was not submitted. Please try again.";
}

/**
 * The Teacher composes a leave request, checks the server's preview of it, and submits. Nothing is a
 * request until the server confirms it; then My Leave History opens with the new request on top.
 */
export function ApplyLeaveScreen({ openRoute }: { openRoute: OpenRoute }) {
  const [types, setTypes] = useState<TypesState>({ state: "loading" });
  const [attempt, setAttempt] = useState(0);
  const [draft, setDraft] = useState<LeaveDraft>(emptyDraft);
  const [calendar, setCalendar] = useState<AttendanceCalendar | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [checking, setChecking] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [showErrors, setShowErrors] = useState(false);
  const sending = useRef(false);
  const calendarRequested = useRef(false);
  // Only the latest preview request may set the preview, so an answer for an older draft is ignored.
  const previewSeq = useRef(0);

  useEffect(() => {
    let cancelled = false;
    listLeaveTypes()
      .then((loaded) => {
        if (!cancelled) setTypes({ state: "ready", types: loaded });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        if (error instanceof NoConnectionError) setTypes({ state: "noConnection" });
        else if (error instanceof ApiError && error.status === 404) setTypes({ state: "notFound", message: error.message });
        else setTypes({ state: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, [attempt]);

  const change = (patch: Partial<Omit<LeaveDraft, "preview">>) => {
    previewSeq.current += 1;
    setProblem(null);
    setDraft((d) => editDraft(d, patch));
  };

  if (types.state === "loading") {
    return (
      <Screen>
        <ActivityIndicator size="large" accessibilityLabel="Loading leave types" />
      </Screen>
    );
  }
  if (types.state === "notFound") {
    return (
      <Screen>
        <Problem title="Apply Leave" text={types.message} />
      </Screen>
    );
  }
  if (types.state === "noConnection" || types.state === "error") {
    return (
      <Screen>
        <Problem
          title={types.state === "noConnection" ? "No connection" : "Something went wrong"}
          text={types.state === "noConnection" ? "Check your internet connection and try again." : "We couldn't load the leave types."}
          onRetry={() => {
            setTypes({ state: "loading" });
            setAttempt((n) => n + 1);
          }}
        />
      </Screen>
    );
  }

  const check = async () => {
    if (!canCheck(draft) || checking) return;
    const seq = ++previewSeq.current;
    setChecking(true);
    setProblem(null);
    try {
      const result: LeavePreview = await previewLeave(toBody(draft));
      if (seq !== previewSeq.current) return;
      setDraft((d) => ({ ...d, preview: result }));
      if (!calendarRequested.current) {
        calendarRequested.current = true;
        getCalendar()
          .then(setCalendar)
          .catch(() => undefined);
      }
    } catch (error) {
      if (seq === previewSeq.current) setProblem(failureText(error));
    } finally {
      setChecking(false);
    }
  };

  const submit = async () => {
    if (sending.current) return;
    const issues = appProblems(draft);
    if (issues.length > 0) {
      setShowErrors(true);
      return;
    }
    sending.current = true;
    setSubmitting(true);
    setProblem(null);
    try {
      await submitLeave(toBody(draft));
    } catch (error) {
      setProblem(failureText(error));
      sending.current = false;
      setSubmitting(false);
      return;
    }
    // Only a 2xx is a submitted request: clear the form and show the history with the new request on top.
    sending.current = false;
    setDraft(emptyDraft);
    setSubmitting(false);
    openRoute("/leave/history", { notice: "Request submitted" });
  };

  const issues = showErrors ? appProblems(draft) : [];
  const preview = draft.preview;

  return (
    <Screen>
      <Text variant="titleMedium" accessibilityRole="header">
        Leave type
      </Text>
      <RadioButton.Group onValueChange={(id) => change({ leaveTypeId: id })} value={draft.leaveTypeId ?? ""}>
        {types.types.map((type) => (
          <RadioButton.Item
            key={type.id}
            label={type.name}
            value={type.id}
            accessibilityLabel={type.name}
            style={styles.item}
          />
        ))}
      </RadioButton.Group>

      <DateChooser
        label="First date"
        value={draft.firstDate}
        onChange={(date) => change({ firstDate: date, lastDate: draft.lastDate && draft.lastDate < date ? date : draft.lastDate })}
      />
      <DateChooser label="Last date" value={draft.lastDate} onChange={(date) => change({ lastDate: date })} />
      {draft.firstDate && draft.lastDate && draft.lastDate < draft.firstDate ? (
        <HelperText type="error" visible accessibilityRole="alert">
          The last date cannot be before the first date.
        </HelperText>
      ) : null}

      <Checkbox.Item
        label="Half day on the first day"
        status={draft.halfDayStart ? "checked" : "unchecked"}
        onPress={() => change({ halfDayStart: !draft.halfDayStart })}
        accessibilityLabel="Half day on the first day"
        style={styles.item}
      />
      <Checkbox.Item
        label="Half day on the last day"
        status={draft.halfDayEnd ? "checked" : "unchecked"}
        onPress={() => change({ halfDayEnd: !draft.halfDayEnd })}
        accessibilityLabel="Half day on the last day"
        style={styles.item}
      />

      <TextInput
        label="Reason (required)"
        value={draft.reason}
        onChangeText={(reason) => change({ reason: reason.slice(0, MAX_LEAVE_TEXT) })}
        multiline
        maxLength={MAX_LEAVE_TEXT}
        accessibilityLabel="Reason"
      />
      <HelperText type="info" visible accessibilityLabel={`${MAX_LEAVE_TEXT - draft.reason.length} characters left`}>
        {MAX_LEAVE_TEXT - draft.reason.length} characters left
      </HelperText>

      {issues.map((text) => (
        <HelperText key={text} type="error" visible accessibilityRole="alert">
          {text}
        </HelperText>
      ))}

      <Button
        mode="outlined"
        onPress={() => void check()}
        disabled={!canCheck(draft) || checking}
        loading={checking}
        contentStyle={styles.button}
        accessibilityLabel="Check"
      >
        Check
      </Button>

      {preview && draft.firstDate && draft.lastDate ? (
        <PreviewPanel firstDate={draft.firstDate} lastDate={draft.lastDate} preview={preview} calendar={calendar} />
      ) : null}

      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}

      <View>
        <Button
          mode="contained"
          onPress={() => void submit()}
          disabled={!canSubmit(draft) || submitting}
          loading={submitting}
          contentStyle={styles.button}
          accessibilityLabel="Submit"
        >
          Submit
        </Button>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  button: { minHeight: minTouchTarget },
  item: { minHeight: minTouchTarget, paddingHorizontal: spacingUnit },
});
