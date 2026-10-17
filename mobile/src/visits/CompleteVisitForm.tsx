import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, Text, TextInput } from "react-native-paper";
import { ApiError } from "../api/errors";
import { attachVisitPhoto, completeVisit, getVisit, type Visit } from "../api/marketingApi";
import { formatIsoDate } from "../formats/dates";
import { DateChooser } from "../leave/DateChooser";
import { PhotoSlots } from "../media/PhotoSlots";
import type { PickedPhoto } from "../media/pickPhoto";
import { uploadPhotos, type FailedPhoto } from "../media/uploadPhotos";
import { useAction } from "../manager/useAction";
import { MAX_VISIT_FILES } from "../tasks/taskMessages";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { visitTarget } from "./VisitCard";

/** The longest outcome and notes the server accepts. */
export const MAX_VISIT_TEXT = 1000;

interface Props {
  visit: Visit;
  /** Called once the server has completed the visit (the photos have then been tried), with the server's visit. */
  onDone: (visit: Visit, failedPhotos: FailedPhoto[]) => void;
  /** The server said the visit changed or is gone: the caller reloads it. */
  onStale: () => void;
  onCancel: () => void;
}

/**
 * Completes a planned visit (spec 034 US2): the outcome (who was met, what was discussed and the next action) is required, notes
 * and a follow-up date are optional, and photos are optional and never required. The photos are uploaded after the server has
 * completed the visit, so a refused photo never undoes it. Every entry is kept until the server confirms.
 */
export function CompleteVisitForm({ visit, onDone, onStale, onCancel }: Props) {
  const [outcome, setOutcome] = useState("");
  const [notes, setNotes] = useState("");
  const [followUpOn, setFollowUpOn] = useState<string | null>(null);
  const [photos, setPhotos] = useState<PickedPhoto[]>([]);

  const send = useAction(async () => {
    let done: Visit;
    try {
      done = await completeVisit(visit.id, {
        outcome: outcome.trim(),
        notes: notes.trim() !== "" ? notes.trim() : undefined,
        followUpOn: followUpOn ?? undefined,
        version: visit.version,
      });
    } catch (error) {
      if (error instanceof ApiError && (error.status === 409 || error.status === 404)) onStale();
      throw error;
    }
    const { failed } = await uploadPhotos(photos, (file) => attachVisitPhoto(visit.id, file));
    // With photos attached the visit is read again so its attachments are the server's list; otherwise the answer is used.
    const fresh = photos.length > failed.length ? await getVisit(visit.id).catch(() => done) : done;
    onDone(fresh, failed);
    return true;
  });

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        Complete visit
      </Text>
      <Text variant="bodyMedium">
        {visitTarget(visit)}, {formatIsoDate(visit.date)}
      </Text>
      <TextInput
        label="Outcome (required)"
        value={outcome}
        onChangeText={(text) => setOutcome(text.slice(0, MAX_VISIT_TEXT))}
        multiline
        maxLength={MAX_VISIT_TEXT}
        accessibilityLabel="Outcome"
      />
      <HelperText type="info" visible accessibilityLabel={`${MAX_VISIT_TEXT - outcome.length} characters left in the outcome`}>
        Who was met, what was discussed and the next action. {MAX_VISIT_TEXT - outcome.length} characters left.
      </HelperText>
      {outcome.length > 0 && outcome.trim() === "" ? (
        <HelperText type="error" visible accessibilityRole="alert">
          An outcome is needed: who was met, what was discussed and the next action.
        </HelperText>
      ) : null}
      <TextInput
        label="Notes"
        value={notes}
        onChangeText={(text) => setNotes(text.slice(0, MAX_VISIT_TEXT))}
        multiline
        maxLength={MAX_VISIT_TEXT}
        accessibilityLabel="Notes"
      />
      <HelperText type="info" visible accessibilityLabel={`${MAX_VISIT_TEXT - notes.length} characters left in the notes`}>
        {MAX_VISIT_TEXT - notes.length} characters left.
      </HelperText>
      <DateChooser label="Follow-up date" value={followUpOn} onChange={setFollowUpOn} />
      {followUpOn ? (
        <Button onPress={() => setFollowUpOn(null)} contentStyle={styles.button} accessibilityLabel="Remove the follow-up date">
          Remove the follow-up date
        </Button>
      ) : null}
      <PhotoSlots
        photos={photos}
        onChange={setPhotos}
        limit={MAX_VISIT_FILES}
        alreadyAttached={visit.attachments.length}
        disabled={send.sending}
      />
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}
      <Button
        mode="contained"
        onPress={() => void send.run()}
        disabled={outcome.trim() === "" || send.sending}
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
