import { useEffect, useState } from "react";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import { attachVisitPhoto, getVisit, type Visit } from "../api/marketingApi";
import { Problem } from "../attendance/MonthPane";
import { PhotoSlots } from "../media/PhotoSlots";
import type { PickedPhoto } from "../media/pickPhoto";
import { uploadPhotos, type FailedPhoto } from "../media/uploadPhotos";
import { useAction } from "../manager/useAction";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_VISIT_FILES } from "../tasks/taskMessages";
import { CancelVisitDialog } from "./CancelVisitDialog";
import { CompleteVisitForm } from "./CompleteVisitForm";
import { RescheduleDialog } from "./RescheduleDialog";
import { VisitDetail } from "./VisitDetail";

type Loaded =
  | { state: "ready"; id: string; visit: Visit }
  | { state: "gone" | "error"; id: string; message: string };

type Panel = "complete" | "photos" | "reschedule" | "cancel" | null;

interface Props {
  id: string;
  canCreate: boolean;
  canEdit: boolean;
  /** Called after the server changed the visit, so the month list can reload. */
  onChanged: () => void;
  onBack: () => void;
}

/**
 * One visit with the forms that act on it (spec 034). It reads the visit from the server when it opens, so the version it
 * sends back is current; a 409 or 404 from an action reloads the visit and keeps what the Manager typed.
 */
export function VisitDetailPane({ id, canCreate, canEdit, onChanged, onBack }: Props) {
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [panel, setPanel] = useState<Panel>(null);
  const [failed, setFailed] = useState<FailedPhoto[]>([]);

  useEffect(() => {
    let cancelled = false;
    getVisit(id)
      .then((visit) => {
        if (!cancelled) setLoaded({ state: "ready", id, visit });
      })
      .catch((error: unknown) => {
        if (cancelled) return;
        const message =
          error instanceof NoConnectionError
            ? "No connection. The visit could not be loaded."
            : error instanceof ApiError
              ? error.message
              : "The visit could not be loaded.";
        setLoaded({ state: error instanceof ApiError && error.status === 404 ? "gone" : "error", id, message });
      });
    return () => {
      cancelled = true;
    };
  }, [id, attempt]);

  const current = loaded && loaded.id === id ? loaded : null;
  const reload = () => setAttempt((n) => n + 1);

  const photoUpload = useAction(async (visit: Visit, photos: PickedPhoto[]) => {
    const { failed: problems } = await uploadPhotos(photos, (file) => attachVisitPhoto(visit.id, file));
    const fresh = await getVisit(visit.id);
    setLoaded({ state: "ready", id, visit: fresh });
    setFailed(problems);
    onChanged();
    return problems.length === 0;
  });
  const [extraPhotos, setExtraPhotos] = useState<PickedPhoto[]>([]);

  const back = (
    <Button onPress={onBack} contentStyle={styles.button} accessibilityLabel="Back to the visits">
      Back to the visits
    </Button>
  );

  if (!current) {
    return (
      <View style={styles.box}>
        <ActivityIndicator accessibilityLabel="Loading the visit" />
        {back}
      </View>
    );
  }
  if (current.state !== "ready") {
    return (
      <View style={styles.box}>
        <Problem
          title={current.state === "gone" ? "Visit not found" : "Something went wrong"}
          text={current.message}
          onRetry={current.state === "error" ? reload : undefined}
        />
        {back}
      </View>
    );
  }

  const { visit } = current;

  if (panel === "complete") {
    return (
      <CompleteVisitForm
        visit={visit}
        onStale={reload}
        onCancel={() => setPanel(null)}
        onDone={(done, problems) => {
          setLoaded({ state: "ready", id, visit: done });
          setFailed(problems);
          setPanel(null);
          onChanged();
        }}
      />
    );
  }

  if (panel === "reschedule") {
    return (
      <RescheduleDialog
        visit={visit}
        onStale={reload}
        onCancel={() => setPanel(null)}
        onDone={(moved) => {
          setLoaded({ state: "ready", id, visit: moved });
          setPanel(null);
          onChanged();
        }}
      />
    );
  }

  if (panel === "photos") {
    return (
      <View style={styles.box}>
        <Text variant="titleLarge" accessibilityRole="header">
          Add photos
        </Text>
        <PhotoSlots photos={extraPhotos} onChange={setExtraPhotos} limit={MAX_VISIT_FILES} alreadyAttached={visit.attachments.length} disabled={photoUpload.sending} />
        {photoUpload.error ? (
          <HelperText type="error" visible accessibilityRole="alert">
            {photoUpload.error}
          </HelperText>
        ) : null}
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
        <Button
          mode="contained"
          disabled={extraPhotos.length === 0 || photoUpload.sending}
          loading={photoUpload.sending}
          onPress={async () => {
            if (await photoUpload.run(visit, extraPhotos)) {
              setExtraPhotos([]);
              setPanel(null);
            }
          }}
          contentStyle={styles.button}
          accessibilityLabel="Upload photos"
        >
          Upload photos
        </Button>
        <Button onPress={() => setPanel(null)} disabled={photoUpload.sending} contentStyle={styles.button} accessibilityLabel="Cancel">
          Cancel
        </Button>
      </View>
    );
  }

  return (
    <View style={styles.box}>
      <VisitDetail
        visit={visit}
        canCreate={canCreate}
        canEdit={canEdit}
        actions={{
          onComplete: () => setPanel("complete"),
          onReschedule: () => setPanel("reschedule"),
          onCancel: () => setPanel("cancel"),
          onAddPhoto: () => setPanel("photos"),
        }}
      >
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
      </VisitDetail>
      {back}
      {panel === "cancel" ? (
        <CancelVisitDialog
          visit={visit}
          onStale={reload}
          onCancel={() => setPanel(null)}
          onDone={(cancelled) => {
            setLoaded({ state: "ready", id, visit: cancelled });
            setPanel(null);
            onChanged();
          }}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
