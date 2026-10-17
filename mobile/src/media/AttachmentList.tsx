import { useEffect, useState } from "react";
import { Image, StyleSheet, View } from "react-native";
import { Button, HelperText, Text } from "react-native-paper";
import type { FileView } from "../api/marketingApi";
import { formatFileType, formatSize } from "../formats/manager";
import { minTouchTarget, spacingUnit } from "../theme/tokens";

export interface AttachedFile {
  fileId: string;
  name: string;
  contentType: string;
  sizeBytes: number;
}

interface Props {
  files: AttachedFile[];
  /** Downloads one file's bytes (the visit or the task endpoint). */
  open: (fileId: string) => Promise<FileView>;
}

type Thumb = { key: string; state: "ready"; uri: string } | { key: string; state: "error" };

function Row({ file, open }: { file: AttachedFile; open: Props["open"] }) {
  const isImage = file.contentType.toLowerCase().startsWith("image/");
  const [attempt, setAttempt] = useState(0);
  const key = `${file.fileId}|${attempt}`;
  const [thumb, setThumb] = useState<Thumb | null>(null);

  useEffect(() => {
    if (!isImage) return;
    let cancelled = false;
    open(file.fileId)
      .then((view) => {
        if (!cancelled) setThumb({ key, state: "ready", uri: view.dataUri });
      })
      .catch(() => {
        if (!cancelled) setThumb({ key, state: "error" });
      });
    return () => {
      cancelled = true;
    };
  }, [file.fileId, isImage, key, open]);

  const current = thumb?.key === key ? thumb : null;
  return (
    <View style={styles.row}>
      <Text variant="bodyMedium">
        {file.name}, {formatFileType(file.contentType)}, {formatSize(file.sizeBytes)}
      </Text>
      {isImage && !current ? <Text variant="bodySmall">Loading the photo…</Text> : null}
      {current?.state === "ready" ? (
        <Image source={{ uri: current.uri }} style={styles.thumb} accessibilityLabel={`Photo ${file.name}`} />
      ) : null}
      {current?.state === "error" ? (
        <View>
          <HelperText type="error" visible accessibilityRole="alert">
            The photo could not be loaded.
          </HelperText>
          <Button onPress={() => setAttempt((n) => n + 1)} contentStyle={styles.button} accessibilityLabel={`Try again for ${file.name}`}>
            Try again
          </Button>
        </View>
      ) : null}
      {!isImage ? <Text variant="bodySmall">Open it on the web.</Text> : null}
    </View>
  );
}

/**
 * The files attached to a visit or a task: name, type and size as the server holds them, a thumbnail for an image (loaded
 * from the downloaded bytes and kept in memory only), and, for a document added on the web, a note that it opens there.
 */
export function AttachmentList({ files, open }: Props) {
  if (files.length === 0) return null;
  return (
    <View style={styles.list} accessibilityLabel="Attachments">
      {files.map((file) => (
        <Row key={file.fileId} file={file} open={open} />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  list: { gap: spacingUnit },
  row: { gap: spacingUnit / 2 },
  thumb: { width: 120, height: 120, borderRadius: spacingUnit },
  button: { minHeight: minTouchTarget },
});
