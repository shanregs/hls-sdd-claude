import { useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { formatImageType, formatSize } from "../formats/manager";
import { choosePhoto, takePhoto, type PickedPhoto } from "../media/pickPhoto";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

export type { PickedPhoto };

interface Props {
  value: PickedPhoto | null;
  onChange: (photo: PickedPhoto | null) => void;
  disabled?: boolean;
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
    const result = await takePhoto();
    if (result.problem) setProblem(result.problem);
    if (result.photo) onChange(result.photo);
  };

  const choose = async () => {
    setProblem(null);
    const result = await choosePhoto();
    if (result.photo) onChange(result.photo);
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
