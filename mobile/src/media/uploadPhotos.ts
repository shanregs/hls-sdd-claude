import type { UploadFile } from "../api/httpClient";
import { managerRefusalText } from "../manager/managerMessages";
import type { PickedPhoto } from "./pickPhoto";

export interface FailedPhoto {
  name: string;
  /** The server's reason, or the no-connection text. */
  reason: string;
}

/**
 * Uploads photos one after another, after the action they belong to has succeeded (spec 034 FR-005, FR-010). It never
 * throws: a photo the server refuses or that cannot be sent is reported by name and the next one is still tried, so one
 * failure never undoes the action or the other photos.
 */
export async function uploadPhotos(
  photos: PickedPhoto[],
  send: (file: UploadFile) => Promise<unknown>,
): Promise<{ failed: FailedPhoto[] }> {
  const failed: FailedPhoto[] = [];
  for (const photo of photos) {
    try {
      await send({ uri: photo.uri, name: photo.name, type: photo.type });
    } catch (error) {
      failed.push({ name: photo.name, reason: managerRefusalText(error) });
    }
  }
  return { failed };
}
