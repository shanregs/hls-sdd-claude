import { useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { addTaskEvidence, getTask, openTaskFile, type TaskDetail } from "../api/tasksApi";
import { AttachmentList } from "../media/AttachmentList";
import { PhotoSlots } from "../media/PhotoSlots";
import type { PickedPhoto } from "../media/pickPhoto";
import { uploadPhotos, type FailedPhoto } from "../media/uploadPhotos";
import { useAction } from "../manager/useAction";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_EVIDENCE } from "./taskMessages";

interface Props {
  detail: TaskDetail;
  /** The server's latest task, after the photos were tried. */
  onDetail: (detail: TaskDetail) => void;
}

/**
 * A task's evidence (spec 034 FR-010): the files the server holds, "n of 5", and, while the server's `addEvidence` flag is true,
 * photos taken or chosen on the phone. A photo is never required; a refused one is named and never changes the task.
 */
export function EvidenceSection({ detail, onDetail }: Props) {
  const [photos, setPhotos] = useState<PickedPhoto[]>([]);
  const [failed, setFailed] = useState<FailedPhoto[]>([]);
  const id = detail.task.id;
  const count = detail.evidence.length;

  const upload = useAction(async () => {
    const result = await uploadPhotos(photos, (file) => addTaskEvidence(id, file));
    // The task is read again so the evidence and the flags are the server's, whatever happened to each photo.
    onDetail(await getTask(id));
    setFailed(result.failed);
    setPhotos([]);
    return true;
  });

  return (
    <View style={styles.box}>
      <Text variant="titleMedium" accessibilityRole="header" accessibilityLabel={`Evidence, ${count} of ${MAX_EVIDENCE}`}>
        Evidence ({count} of {MAX_EVIDENCE})
      </Text>
      <AttachmentList files={detail.evidence} open={openTaskFile} />
      {failed.length > 0 ? (
        <View style={styles.box}>
          <Text variant="titleSmall">These photos were not added:</Text>
          {failed.map((photo, index) => (
            <HelperText key={`${photo.name}-${index}`} type="error" visible accessibilityRole="alert">
              {photo.name}: {photo.reason}
            </HelperText>
          ))}
        </View>
      ) : null}
      {detail.actions.addEvidence ? (
        <View style={styles.box}>
          <PhotoSlots photos={photos} onChange={setPhotos} limit={MAX_EVIDENCE} alreadyAttached={count} disabled={upload.sending} />
          {upload.error ? (
            <HelperText type="error" visible accessibilityRole="alert">
              {upload.error}
            </HelperText>
          ) : null}
          {photos.length > 0 ? (
            <Button
              mode="contained"
              onPress={() => void upload.run()}
              loading={upload.sending}
              disabled={upload.sending}
              contentStyle={styles.button}
              accessibilityLabel="Upload photos"
            >
              Upload photos
            </Button>
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
