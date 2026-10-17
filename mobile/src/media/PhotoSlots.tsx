import { useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import { formatFileType, formatSize } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { choosePhoto, takePhoto, type PickedPhoto } from "./pickPhoto";

interface Props {
  photos: PickedPhoto[];
  onChange: (photos: PickedPhoto[]) => void;
  /** The most photos the visit or task may have in all (the server's limit). */
  limit: number;
  /** How many the server already holds, counted against the limit. */
  alreadyAttached?: number;
  disabled?: boolean;
}

/**
 * Photos to send with a visit or a task (spec 034 FR-005, FR-010): taken in the app or chosen from the gallery, never
 * required. It shows how many of the limit are used and stops offering more at the limit. Pictures are held in memory only.
 */
export function PhotoSlots({ photos, onChange, limit, alreadyAttached = 0, disabled }: Props) {
  const [problem, setProblem] = useState<string | null>(null);
  const used = alreadyAttached + photos.length;
  const full = used >= limit;

  const add = async (pick: typeof takePhoto) => {
    setProblem(null);
    const result = await pick();
    if (result.problem) setProblem(result.problem);
    if (result.photo) onChange([...photos, result.photo]);
  };

  return (
    <View style={styles.box}>
      <Text variant="titleSmall" accessibilityLabel={`Photos, ${used} of ${limit}`}>
        Photos (optional): {used} of {limit}
      </Text>
      {photos.map((photo, index) => (
        <View key={`${photo.uri}-${index}`} style={styles.picked}>
          <Image source={{ uri: photo.uri }} style={styles.thumb} accessibilityLabel={`Photo ${photo.name}`} />
          <Text variant="bodyMedium">
            {photo.name}, {formatFileType(photo.type)}
            {photo.sizeBytes !== null ? `, ${formatSize(photo.sizeBytes)}` : ""}
          </Text>
          <Button
            onPress={() => onChange(photos.filter((_, i) => i !== index))}
            disabled={disabled}
            accessibilityLabel={`Remove ${photo.name}`}
            contentStyle={styles.button}
          >
            Remove
          </Button>
        </View>
      ))}
      {!full ? (
        <View style={styles.row}>
          <Button mode="outlined" onPress={() => void add(takePhoto)} disabled={disabled} contentStyle={styles.button}>
            Take a photo
          </Button>
          <Button mode="outlined" onPress={() => void add(choosePhoto)} disabled={disabled} contentStyle={styles.button}>
            Choose from gallery
          </Button>
        </View>
      ) : (
        <Text variant="bodyMedium">The limit of {limit} photos is reached.</Text>
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
  thumb: { width: 120, height: 120, borderRadius: spacingUnit },
  button: { minHeight: minTouchTarget },
});
