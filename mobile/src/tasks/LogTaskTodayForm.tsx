import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import { listTaskTypes, type TaskTypeOption } from "../api/tasksApi";
import { useAction } from "../manager/useAction";
import { useSameDayLog, type LogResult } from "../sameDay/useSameDayLog";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_TITLE, MIN_TITLE } from "./taskMessages";

const MIN_NOTE = 10;
const MAX_NOTE = 2000;

interface Props {
  myUserId: string;
  /** Called once the task is on the server (SENT) or kept on the phone (QUEUED). */
  onDone: (result: LogResult) => void;
  onCancel: () => void;
}

/**
 * Logs a task for today for the Manager themselves (spec 040 US3): saved as In progress, or Completed and accepted in one
 * step. It is for the signed-in Manager and for today; with no connection it waits on the phone and is sent later.
 */
export function LogTaskTodayForm({ myUserId, onDone, onCancel }: Props) {
  const log = useSameDayLog(myUserId);
  const [types, setTypes] = useState<TaskTypeOption[] | null>(null);
  const [type, setType] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [place, setPlace] = useState("");
  const [status, setStatus] = useState<"COMPLETED" | "IN_PROGRESS">("COMPLETED");
  const [note, setNote] = useState("");

  useEffect(() => {
    let cancelled = false;
    listTaskTypes()
      .then((list) => {
        if (!cancelled) setTypes(list);
      })
      .catch(() => {
        if (!cancelled) setTypes([]);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const send = useAction(async () => {
    const result = await log.logTask(
      {
        type: type ?? "",
        title: title.trim(),
        description: description.trim() !== "" ? description.trim() : undefined,
        place: place.trim() !== "" ? place.trim() : undefined,
        status,
        completionNote: status === "COMPLETED" ? note.trim() : undefined,
      },
      title.trim(),
    );
    onDone(result);
    return true;
  });

  const noteOk = status === "IN_PROGRESS" || note.trim().length >= MIN_NOTE;
  const ready = type !== null && title.trim().length >= MIN_TITLE && noteOk;

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        Log a task for today
      </Text>
      <Text variant="bodyMedium">It is for you and for today. It does not wait for anyone to approve it.</Text>
      <Text variant="titleSmall">Type</Text>
      {types === null ? <Text variant="bodyMedium">Loading the types…</Text> : null}
      <RadioButton.Group onValueChange={setType} value={type ?? ""}>
        {(types ?? []).map((option) => (
          <RadioButton.Item key={option.value} label={option.label} value={option.value} accessibilityLabel={option.label} style={styles.button} />
        ))}
      </RadioButton.Group>
      <TextInput
        label="Title (required)"
        value={title}
        onChangeText={(t) => setTitle(t.slice(0, MAX_TITLE))}
        maxLength={MAX_TITLE}
        accessibilityLabel="Title"
      />
      <TextInput label="Description" value={description} onChangeText={setDescription} multiline accessibilityLabel="Description" />
      <TextInput label="Place" value={place} onChangeText={setPlace} accessibilityLabel="Place" />
      <Text variant="titleSmall">Save as</Text>
      <RadioButton.Group onValueChange={(v) => setStatus(v as "COMPLETED" | "IN_PROGRESS")} value={status}>
        <RadioButton.Item label="Completed" value="COMPLETED" accessibilityLabel="Completed" style={styles.button} />
        <RadioButton.Item label="In progress" value="IN_PROGRESS" accessibilityLabel="In progress" style={styles.button} />
      </RadioButton.Group>
      {status === "COMPLETED" ? (
        <>
          <TextInput
            label="What was done (required)"
            value={note}
            onChangeText={(t) => setNote(t.slice(0, MAX_NOTE))}
            multiline
            maxLength={MAX_NOTE}
            accessibilityLabel="What was done"
          />
          <HelperText type="info" visible>
            At least {MIN_NOTE} characters.
          </HelperText>
        </>
      ) : null}
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}
      <Button
        mode="contained"
        onPress={() => void send.run()}
        disabled={!ready || send.sending}
        loading={send.sending}
        contentStyle={styles.button}
        accessibilityLabel="Log task"
      >
        Log task
      </Button>
      <Button onPress={onCancel} disabled={send.sending} contentStyle={styles.button} accessibilityLabel="Cancel">
        Cancel
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
