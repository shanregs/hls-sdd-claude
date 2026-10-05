import { useState, type ReactNode } from "react";
import { ScrollView, StyleSheet, View } from "react-native";
import { Button, HelperText, Modal, Portal, RadioButton, Text, TextInput, useTheme } from "react-native-paper";
import type { DayState, DayView, DayValue, MarkRequest, StatusCode } from "../api/attendanceApi";
import { formatDateTime, formatLongDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import type { Viewer } from "./dayPresentation";

const STATE_TEXT: Record<DayState, string> = {
  MARKED: "Marked",
  UNMARKED: "Not marked",
  NOT_PLACED: "Not placed in a school",
  WEEKLY_OFF: "Weekly off",
  NON_WORKING: "Holiday",
  FUTURE: "Future day",
};

export interface DaySheetProps {
  day: DayView;
  viewer: Viewer;
  /** True only when the server says the viewer may change this day (`editableBy`). */
  editable: boolean;
  /** The statuses to choose from (the server's active list); null while it loads. */
  statuses: StatusCode[] | null;
  /** Shown when the status list could not be loaded. */
  statusesFailed?: boolean;
  onRetryStatuses?: () => void;
  /** Saves the day; resolves with the refusal or error text to show, or null once the server accepted it. */
  onSave: (body: MarkRequest) => Promise<string | null>;
  /** Clears the day (Manager only); resolves like onSave. Offered only for an editable, marked day. */
  onClear?: () => Promise<string | null>;
  onHistory?: () => void;
  history?: ReactNode;
  onClose: () => void;
}

/**
 * A day's details and, when the server allows it, the form to mark it. A draft that fails to save
 * stays on screen with the reason; nothing is shown as saved until the server accepted it (FR-004).
 */
export function DaySheet({
  day,
  viewer,
  editable,
  statuses,
  statusesFailed,
  onRetryStatuses,
  onSave,
  onClear,
  onHistory,
  history,
  onClose,
}: DaySheetProps) {
  const theme = useTheme();
  const mark = day.mark;
  const [chosen, setChosen] = useState<string | null>(null);
  const [dayValue, setDayValue] = useState<DayValue | null>(null);
  const [note, setNote] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [problem, setProblem] = useState<string | null>(null);

  const defaultCode =
    mark && statuses?.some((s) => s.shortCode === mark.code) ? mark.code : (statuses?.[0]?.shortCode ?? null);
  const statusCode = chosen ?? defaultCode;
  const value: DayValue = dayValue ?? (mark?.dayValue === 0.5 ? 0.5 : 1);
  const noteText = note ?? mark?.note ?? "";

  const run = async (action: () => Promise<string | null>) => {
    setBusy(true);
    setProblem(null);
    try {
      const result = await action();
      if (result !== null) setProblem(result);
    } finally {
      setBusy(false);
    }
  };

  const save = () => {
    if (!statusCode) return;
    const body: MarkRequest = { statusCode, dayValue: value };
    const trimmed = noteText.trim();
    if (trimmed !== "") body.note = trimmed;
    if (mark) body.version = mark.version;
    void run(() => onSave(body));
  };

  const setByLine = mark
    ? viewer === "self" && mark.setByKind === "SELF"
      ? `Set by you on ${formatDateTime(mark.setAt)}`
      : `Set by ${mark.setByName} on ${formatDateTime(mark.setAt)}`
    : null;

  return (
    <Portal>
      <Modal
        visible
        onDismiss={onClose}
        style={styles.wrapper}
        contentContainerStyle={[styles.sheet, { backgroundColor: theme.colors.surface }]}
      >
        <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
          <Text variant="titleLarge" accessibilityRole="header">
            {formatLongDate(day.date)}
          </Text>
          <Text variant="bodyLarge">{STATE_TEXT[day.state]}</Text>
          {mark ? (
            <View style={styles.details}>
              <Text variant="bodyLarge">
                {mark.codeName}, {mark.dayValue === 0.5 ? "half day" : "whole day"}
              </Text>
              {mark.schoolName ? <Text variant="bodyMedium">School: {mark.schoolName}</Text> : null}
              {mark.note ? <Text variant="bodyMedium">Note: {mark.note}</Text> : null}
              <Text variant="bodyMedium">{setByLine}</Text>
            </View>
          ) : null}
          {!editable && viewer === "self" && mark?.setByKind === "SUPERVISOR" ? (
            <Text variant="bodyMedium">Your Manager set this day. Ask your Manager to correct it.</Text>
          ) : null}

          {problem ? (
            <HelperText type="error" visible accessibilityRole="alert" style={styles.problem}>
              {problem}
            </HelperText>
          ) : null}

          {editable ? (
            <View style={styles.form}>
              {statuses === null && !statusesFailed ? <Text variant="bodyMedium">Loading statuses…</Text> : null}
              {statusesFailed ? (
                <View>
                  <Text variant="bodyMedium">We couldn't load the statuses.</Text>
                  {onRetryStatuses ? (
                    <Button onPress={onRetryStatuses} accessibilityLabel="Retry statuses">
                      Retry
                    </Button>
                  ) : null}
                </View>
              ) : null}
              {statuses ? (
                <RadioButton.Group onValueChange={setChosen} value={statusCode ?? ""}>
                  {statuses.map((status) => (
                    <RadioButton.Item
                      key={status.shortCode}
                      label={status.name}
                      value={status.shortCode}
                      accessibilityLabel={status.name}
                      style={styles.radio}
                    />
                  ))}
                </RadioButton.Group>
              ) : null}
              <RadioButton.Group onValueChange={(v) => setDayValue(v === "0.5" ? 0.5 : 1)} value={String(value)}>
                <RadioButton.Item label="Whole day" value="1" accessibilityLabel="Whole day" style={styles.radio} />
                <RadioButton.Item label="Half day" value="0.5" accessibilityLabel="Half day" style={styles.radio} />
              </RadioButton.Group>
              <TextInput
                label="Note (optional)"
                value={noteText}
                onChangeText={setNote}
                multiline
                accessibilityLabel="Note"
              />
              <Button
                mode="contained"
                onPress={save}
                disabled={busy || !statusCode}
                loading={busy}
                contentStyle={styles.buttonContent}
                accessibilityLabel="Save"
              >
                Save
              </Button>
              {onClear && mark ? (
                <Button
                  mode="outlined"
                  onPress={() => void run(onClear)}
                  disabled={busy}
                  contentStyle={styles.buttonContent}
                  accessibilityLabel="Clear mark"
                >
                  Clear mark
                </Button>
              ) : null}
            </View>
          ) : null}

          {onHistory ? (
            <Button onPress={onHistory} contentStyle={styles.buttonContent} accessibilityLabel="History">
              History
            </Button>
          ) : null}
          {history}
          <Button onPress={onClose} contentStyle={styles.buttonContent} accessibilityLabel="Close">
            Close
          </Button>
        </ScrollView>
      </Modal>
    </Portal>
  );
}

const styles = StyleSheet.create({
  wrapper: { justifyContent: "flex-end" },
  sheet: { maxHeight: "88%", borderTopLeftRadius: 16, borderTopRightRadius: 16 },
  content: { padding: spacingUnit * 3, gap: spacingUnit * 1.5 },
  details: { gap: spacingUnit / 2 },
  form: { gap: spacingUnit },
  radio: { minHeight: minTouchTarget },
  problem: { fontSize: 14 },
  buttonContent: { minHeight: minTouchTarget },
});
