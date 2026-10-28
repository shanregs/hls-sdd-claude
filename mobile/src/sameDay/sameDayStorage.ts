import * as FileSystem from "expo-file-system/legacy";
import { decryptText, encryptText } from "../outbox/cipher";
import type { QueueStorage, QueuedItem } from "./sameDayQueue";

/** The waiting same-day items of one user, kept in one encrypted file (AES-256-GCM, the key of spec 036). */
const dirOf = () => `${FileSystem.documentDirectory}sameday/`;
const fileOf = (owner: string) => `${dirOf()}${owner}.enc`;

export function encryptedFileStorage(owner: string): QueueStorage {
  return {
    async read(): Promise<QueuedItem[]> {
      try {
        const info = await FileSystem.getInfoAsync(fileOf(owner));
        if (!info.exists) return [];
        const text = await decryptText(
          await FileSystem.readAsStringAsync(fileOf(owner), { encoding: FileSystem.EncodingType.Base64 }),
        );
        return JSON.parse(text) as QueuedItem[];
      } catch {
        return [];
      }
    },
    async write(items: QueuedItem[]): Promise<void> {
      await FileSystem.makeDirectoryAsync(dirOf(), { intermediates: true });
      await FileSystem.writeAsStringAsync(fileOf(owner), await encryptText(JSON.stringify(items)), {
        encoding: FileSystem.EncodingType.Base64,
      });
    },
  };
}
