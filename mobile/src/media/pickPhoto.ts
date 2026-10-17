import * as ImagePicker from "expo-image-picker";

/** A picture chosen on the phone, held in memory until it is uploaded or removed. */
export interface PickedPhoto {
  uri: string;
  name: string;
  type: string;
  sizeBytes: number | null;
}

export const CAMERA_REFUSED =
  "The camera is not allowed. Allow it in your phone settings, or choose a picture from the gallery. " +
  "You can also continue without a photo.";

const OPTIONS: ImagePicker.ImagePickerOptions = { mediaTypes: ["images"], quality: 0.7 };

function toPhoto(result: ImagePicker.ImagePickerResult): PickedPhoto | null {
  const asset = result.canceled ? undefined : result.assets?.[0];
  if (!asset) return null;
  const type = asset.mimeType ?? "image/jpeg";
  return {
    uri: asset.uri,
    name: asset.fileName ?? (type === "image/png" ? "photo.png" : "photo.jpg"),
    type,
    sizeBytes: asset.fileSize ?? null,
  };
}

export interface PickResult {
  photo: PickedPhoto | null;
  /** Set when the camera permission was refused; nothing else is affected. */
  problem: string | null;
}

/** Takes a photo with the camera. The permission is asked here, only when the Manager chooses this. */
export async function takePhoto(): Promise<PickResult> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) return { photo: null, problem: CAMERA_REFUSED };
  return { photo: toPhoto(await ImagePicker.launchCameraAsync(OPTIONS)), problem: null };
}

/** Chooses a photo from the gallery with the system picker, which needs no storage permission. */
export async function choosePhoto(): Promise<PickResult> {
  return { photo: toPhoto(await ImagePicker.launchImageLibraryAsync(OPTIONS)), problem: null };
}
