import * as ImagePicker from "expo-image-picker";
import { useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { formatImageType, formatSize } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

/** A picture chosen on the phone, held in memory until it is uploaded or removed (spec 033 FR-004). */
export interface PickedPhoto {
  uri: string;
  name: string;
  type: string;
  sizeBytes: number | null;
}

interface Props {
  value: PickedPhoto | null;
  onChange: (photo: PickedPhoto | null) => void;
  disabled?: boolean;
}

const CAMERA_REFUSED =
  "The camera is not allowed. Allow it in your phone settings, or choose a picture from the gallery. " +
  "You can also check in without a photo.";

const OPTIONS: ImagePicker.ImagePickerOptions = { mediaTypes: ["images"], quality: 0.7 };

function toPhoto(result: ImagePicker.ImagePickerResult): PickedPhoto | null {
  const asset = result.canceled ? undefined : result.assets?.[0];
  if (!asset) return null;
  const type = asset.mimeType ?? "image/jpeg";
  return {
    uri: asset.uri,
    name: asset.fileName ?? (type === "image/png" ? "check-in.png" : "check-in.jpg"),
    type,
    sizeBytes: asset.fileSize ?? null,
  };
}

/**
 * Take a photo with the camera or choose one from the gallery (the system picker, which needs no storage permission),
 * look at it, and remove it. The camera permission is asked only when "Take a photo" is chosen, and a refusal affects
 * nothing else. The picture is never written anywhere by the app.
 */
export function PhotoPicker({ value, onChange, disabled }: Props) {
  const [problem, setProblem] = useState<string | null>(null);

  const take = async () => {
    setProblem(null);
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) {
      setProblem(CAMERA_REFUSED);
      return;
    }
    const photo = toPhoto(await ImagePicker.launchCameraAsync(OPTIONS));
    if (photo) onChange(photo);
  };

  const choose = async () => {
    setProblem(null);
    const photo = toPhoto(await ImagePicker.launchImageLibraryAsync(OPTIONS));
    if (photo) onChange(photo);
  };

  return (
    <View style={styles.box}>
      <Text variant="titleSmall">Photo (optional)</Text>
      {value ? (
        <View style={styles.picked}>
          <Image source={{ uri: value.uri }} style={styles.thumb} accessibilityLabel="Photo for this check-in" />
          <Text variant="bodyMedium">
            {value.name}, {formatImageType(value.type)}
            {value.sizeBytes !== null ? `, ${formatSize(value.sizeBytes)}` : ""}
          </Text>
          <Button onPress={() => onChange(null)} disabled={disabled} accessibilityLabel="Remove photo" contentStyle={styles.button}>
            Remove photo
          </Button>
        </View>
      ) : (
        <View style={styles.row}>
          <Button mode="outlined" onPress={() => void take()} disabled={disabled} contentStyle={styles.button}>
            Take a photo
          </Button>
          <Button mode="outlined" onPress={() => void choose()} disabled={disabled} contentStyle={styles.button}>
            Choose from gallery
          </Button>
        </View>
      )}
      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  box: { gap: spacingUnit },
  row: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  picked: { gap: spacingUnit },
  thumb: { width: 160, height: 160, borderRadius: spacingUnit },
  button: { minHeight: minTouchTarget },
});
