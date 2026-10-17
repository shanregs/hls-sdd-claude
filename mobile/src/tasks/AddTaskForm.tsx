import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import { createTask, listTaskTypes, type TaskDetail, type TaskTypeOption } from "../api/tasksApi";
import { DateChooser } from "../leave/DateChooser";
import { useAction } from "../manager/useAction";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_TITLE, MIN_TITLE } from "./taskMessages";

const MAX_DESCRIPTION = 1000;

interface Props {
  /** The caller's own user id: the task is always assigned to them (spec 034 FR-012). */
  myUserId: string;
  /** Called once the server has created the task, with the server's task. */
  onDone: (detail: TaskDetail) => void;
  onCancel: () => void;
}

/**
 * Adds a task for the Manager themselves (spec 034 US7): a type from the server's list, a title of 3 to 120 characters, an optional
 * description and place, and a due date. The task is assigned to the caller and to no one else; every other rule is the server's.
 */
export function AddTaskForm({ myUserId, onDone, onCancel }: Props) {
  const [types, setTypes] = useState<TaskTypeOption[] | null>(null);
  const [typesFailed, setTypesFailed] = useState(false);
  const [type, setType] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [place, setPlace] = useState("");
  const [dueOn, setDueOn] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    listTaskTypes()
      .then((list) => {
        if (!cancelled) setTypes(list);
      })
      .catch(() => {
        if (!cancelled) setTypesFailed(true);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const send = useAction(async () => {
    const created = await createTask({
      type: type ?? "",
      title: title.trim(),
      description: description.trim() !== "" ? description.trim() : undefined,
      assigneeUserId: myUserId,
      dueOn: dueOn ?? "",
      place: place.trim() !== "" ? place.trim() : undefined,
    });
    onDone(created);
    return true;
  });

  const ready = type !== null && title.trim().length >= MIN_TITLE && dueOn !== null;

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        Add a task for me
      </Text>
      <Text variant="titleSmall">Type</Text>
      {typesFailed ? (
        <HelperText type="error" visible accessibilityRole="alert">
          The task types could not be loaded. Close this and try again.
        </HelperText>
      ) : null}
      {types === null && !typesFailed ? <Text variant="bodyMedium">Loading the types…</Text> : null}
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
      <HelperText type="info" visible accessibilityLabel={`${MAX_TITLE - title.length} characters left in the title`}>
        {MAX_TITLE - title.length} characters left. At least {MIN_TITLE} are needed.
      </HelperText>
      <TextInput
        label="Description"
        value={description}
        onChangeText={(t) => setDescription(t.slice(0, MAX_DESCRIPTION))}
        multiline
        maxLength={MAX_DESCRIPTION}
        accessibilityLabel="Description"
      />
      <TextInput label="Place" value={place} onChangeText={setPlace} accessibilityLabel="Place" />
      <DateChooser label="Due date" value={dueOn} onChange={setDueOn} />

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
        accessibilityLabel="Send"
      >
        Send
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
