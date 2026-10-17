import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, Text, TextInput } from "react-native-paper";
import { ApiError } from "../api/errors";
import {
  acknowledgeTask,
  completeTask,
  giveOverdueReason,
  startTask,
  type TaskDetail,
} from "../api/tasksApi";
import { DateChooser } from "../leave/DateChooser";
import { useAction } from "../manager/useAction";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MIN_COMPLETION_NOTE, MIN_OVERDUE_REASON, moreNeededText } from "./taskMessages";

/** The longest note or reason the app lets the Manager type; the server's own limit decides. */
const MAX_TEXT = 1000;

interface Props {
  detail: TaskDetail;
  /** The server's answer to a step: the whole task, replacing what is on screen. */
  onDetail: (detail: TaskDetail) => void;
  /** The server said the task changed or is gone: the caller reads it again. */
  onStale: () => void;
}

type Form = "reason" | "complete" | null;

/**
 * The steps of a task (spec 034 FR-009): Acknowledge, Start, Give a reason and Complete, each shown exactly when the server's
 * `actions` flag for this task is true, never from the task's status or dates. Verify, send back, edit and cancel are never shown.
 * A step is shown as done only after the server answers; a refusal shows its reason and keeps what the Manager typed.
 */
export function TaskSteps({ detail, onDetail, onStale }: Props) {
  const { actions } = detail;
  const [form, setForm] = useState<Form>(null);
  const [reason, setReason] = useState("");
  const [followUpOn, setFollowUpOn] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const id = detail.task.id;

  const guarded = async (call: () => Promise<TaskDetail>): Promise<boolean> => {
    try {
      onDetail(await call());
      return true;
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) onStale();
      throw error;
    }
  };

  const acknowledge = useAction(() => guarded(() => acknowledgeTask(id)));
  const start = useAction(() => guarded(() => startTask(id)));
  const sendReason = useAction(() =>
    guarded(() => giveOverdueReason(id, { reason: reason.trim(), followUpOn: followUpOn ?? undefined })),
  );
  const complete = useAction(() => guarded(() => completeTask(id, { note: note.trim() })));

  const reasonNeed = moreNeededText(reason.trim().length, MIN_OVERDUE_REASON);
  const noteNeed = moreNeededText(note.trim().length, MIN_COMPLETION_NOTE);

  if (form === "reason") {
    return (
      <View style={styles.box}>
        <Text variant="titleMedium" accessibilityRole="header">
          Why is this task overdue?
        </Text>
        <TextInput
          label="Reason"
          value={reason}
          onChangeText={(t) => setReason(t.slice(0, MAX_TEXT))}
          multiline
          accessibilityLabel="Reason"
        />
        {reasonNeed ? (
          <HelperText type="info" visible accessibilityLabel={reasonNeed}>
            {reasonNeed}
          </HelperText>
        ) : null}
        <DateChooser label="Follow-up date" value={followUpOn} onChange={setFollowUpOn} />
        {followUpOn ? (
          <Button onPress={() => setFollowUpOn(null)} contentStyle={styles.button} accessibilityLabel="Remove the follow-up date">
            Remove the follow-up date
          </Button>
        ) : null}
        {sendReason.error ? (
          <HelperText type="error" visible accessibilityRole="alert">
            {sendReason.error}
          </HelperText>
        ) : null}
        <Button
          mode="contained"
          disabled={reasonNeed !== null || sendReason.sending}
          loading={sendReason.sending}
          onPress={async () => {
            if (await sendReason.run()) {
              setForm(null);
              setReason("");
              setFollowUpOn(null);
            }
          }}
          contentStyle={styles.button}
          accessibilityLabel="Send"
        >
          Send
        </Button>
        <Button onPress={() => setForm(null)} disabled={sendReason.sending} contentStyle={styles.button} accessibilityLabel="Cancel">
          Cancel
        </Button>
      </View>
    );
  }

  if (form === "complete") {
    return (
      <View style={styles.box}>
        <Text variant="titleMedium" accessibilityRole="header">
          Complete this task
        </Text>
        <TextInput
          label="Note"
          value={note}
          onChangeText={(t) => setNote(t.slice(0, MAX_TEXT))}
          multiline
          accessibilityLabel="Note"
        />
        {noteNeed ? (
          <HelperText type="info" visible accessibilityLabel={noteNeed}>
            {noteNeed}
          </HelperText>
        ) : null}
        {complete.error ? (
          <HelperText type="error" visible accessibilityRole="alert">
            {complete.error}
          </HelperText>
        ) : null}
        <Button
          mode="contained"
          disabled={noteNeed !== null || complete.sending}
          loading={complete.sending}
          onPress={async () => {
            if (await complete.run()) {
              setForm(null);
              setNote("");
            }
          }}
          contentStyle={styles.button}
          accessibilityLabel="Send"
        >
          Send
        </Button>
        <Button onPress={() => setForm(null)} disabled={complete.sending} contentStyle={styles.button} accessibilityLabel="Cancel">
          Cancel
        </Button>
      </View>
    );
  }

  const errors = [acknowledge.error, start.error].filter((e): e is string => Boolean(e));
  return (
    <View style={styles.box}>
      {errors.map((text) => (
        <HelperText key={text} type="error" visible accessibilityRole="alert">
          {text}
        </HelperText>
      ))}
      {actions.acknowledge ? (
        <Button mode="contained" onPress={() => void acknowledge.run()} loading={acknowledge.sending} disabled={acknowledge.sending} contentStyle={styles.button}>
          Acknowledge
        </Button>
      ) : null}
      {actions.start ? (
        <Button mode="contained" onPress={() => void start.run()} loading={start.sending} disabled={start.sending} contentStyle={styles.button}>
          Start
        </Button>
      ) : null}
      {actions.overdueReason ? (
        <Button mode="outlined" onPress={() => setForm("reason")} contentStyle={styles.button}>
          Give a reason
        </Button>
      ) : null}
      {actions.complete ? (
        <Button mode="contained" onPress={() => setForm("complete")} contentStyle={styles.button}>
          Complete
        </Button>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
