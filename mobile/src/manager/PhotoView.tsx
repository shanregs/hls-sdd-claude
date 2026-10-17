import { useEffect, useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { NoConnectionError } from "../api/errors";
import { attachPhoto, openPhoto, type PhotoFile } from "../api/managerAttendanceApi";
import { formatImageType, formatSize } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { NO_CONNECTION_TEXT } from "./managerMessages";
import { PhotoPicker, type PickedPhoto } from "./PhotoPicker";
import { useAction } from "./useAction";

interface Props {
  /** The date of the check-in the photo belongs to. */
  date: string;
  /** From the server's day: whether a photo is attached. */
  hasPhoto: boolean;
  /** Whether the menu grants Create, so a photo can be added while the day has none. */
  canAdd: boolean;
  /** Called after the server confirmed an added photo, so the day can be reloaded. */
  onChanged: () => void;
}

type Loaded =
  | { state: "loading" }
  | { state: "ready"; photo: PhotoFile }
  | { state: "error"; message: string };

/**
 * The Manager's own photo for a day, with the details the server returns with it (file name, type and size; spec 033
 * FR-004), or an Add photo action while the day has none. The thumbnail is held in memory only.
 */
export function PhotoView({ date, hasPhoto, canAdd, onChanged }: Props) {
  const [attempt, setAttempt] = useState(0);
  // A result belongs to the date and attempt it was loaded for; another date or a retry shows loading again.
  const key = `${date}|${attempt}`;
  const [result, setResult] = useState<{ key: string; value: Exclude<Loaded, { state: "loading" }> } | null>(null);
  const loaded: Loaded = result?.key === key ? result.value : { state: "loading" };
  const [adding, setAdding] = useState(false);
  const [picked, setPicked] = useState<PickedPhoto | null>(null);
  const upload = useAction(async (photo: PickedPhoto) => {
    await attachPhoto(date, { uri: photo.uri, name: photo.name, type: photo.type });
    return true;
  });

  useEffect(() => {
    if (!hasPhoto) return;
    let cancelled = false;
    openPhoto(date)
      .then((photo) => {
        if (!cancelled) setResult({ key, value: { state: "ready", photo } });
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setResult({
            key,
            value: {
              state: "error",
              message: error instanceof NoConnectionError ? NO_CONNECTION_TEXT : "The photo could not be loaded.",
            },
          });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [date, hasPhoto, key]);

  if (hasPhoto) {
    if (loaded.state === "loading") return <Text variant="bodyMedium">Loading the photo…</Text>;
    if (loaded.state === "error") {
      return (
        <View style={styles.box}>
          <HelperText type="error" visible accessibilityRole="alert">
            {loaded.message}
          </HelperText>
          <Button onPress={() => setAttempt((n) => n + 1)} contentStyle={styles.button}>
            Try again
          </Button>
        </View>
      );
    }
    const { photo } = loaded;
    return (
      <View style={styles.box}>
        <Image source={{ uri: photo.dataUri }} style={styles.thumb} accessibilityLabel="Your photo for this check-in" />
        <Text variant="bodyMedium">
          {photo.fileName ?? "Photo"}, {formatImageType(photo.contentType)}, {formatSize(photo.sizeBytes)}
        </Text>
      </View>
    );
  }

  if (!canAdd) return null;
  if (!adding) {
    return (
      <Button mode="outlined" onPress={() => setAdding(true)} contentStyle={styles.button}>
        Add photo
      </Button>
    );
  }
  return (
    <View style={styles.box}>
      <PhotoPicker value={picked} onChange={setPicked} disabled={upload.sending} />
      {picked ? (
        <Button
          mode="contained"
          loading={upload.sending}
          disabled={upload.sending}
          onPress={async () => {
            if (await upload.run(picked)) {
              setAdding(false);
              setPicked(null);
              onChanged();
            }
          }}
          contentStyle={styles.button}
        >
          Upload photo
        </Button>
      ) : null}
      {upload.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {upload.error}
        </HelperText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  thumb: { width: 160, height: 160, borderRadius: spacingUnit },
  button: { minHeight: minTouchTarget },
});
