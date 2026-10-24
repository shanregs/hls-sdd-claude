import * as DocumentPicker from "expo-document-picker";
import * as ImagePicker from "expo-image-picker";
import { BillProblem, prepareBill, type BillSource, type PreparedBill } from "./prepareBill";

export const BILL_CAMERA_REFUSED =
  "The camera is not allowed. Allow it in your phone settings, or choose a photo from the gallery. " +
  "You can also give the reason there is no bill.";

export interface BillPick {
  /** The prepared bill, or null when the user cancelled or the file could not be used. */
  bill: PreparedBill | null;
  /** What to show when the file cannot be used or the camera is refused; null otherwise. */
  problem: string | null;
}

const IMAGE_OPTIONS: ImagePicker.ImagePickerOptions = { mediaTypes: ["images"], quality: 1 };

async function prepare(source: BillSource): Promise<BillPick> {
  try {
    return { bill: await prepareBill(source), problem: null };
  } catch (error) {
    if (error instanceof BillProblem) return { bill: null, problem: error.message };
    throw error;
  }
}

async function fromImageResult(result: ImagePicker.ImagePickerResult): Promise<BillPick> {
  const asset = result.canceled ? undefined : result.assets?.[0];
  if (!asset) return { bill: null, problem: null };
  return prepare({
    uri: asset.uri,
    mimeType: asset.mimeType ?? null,
    name: asset.fileName ?? null,
    sizeBytes: asset.fileSize ?? null,
    width: asset.width ?? null,
    height: asset.height ?? null,
  });
}

/** Photographs a bill. The camera permission is asked here, only when the user chooses this. */
export async function takeBillPhoto(): Promise<BillPick> {
  const permission = await ImagePicker.requestCameraPermissionsAsync();
  if (!permission.granted) return { bill: null, problem: BILL_CAMERA_REFUSED };
  return fromImageResult(await ImagePicker.launchCameraAsync(IMAGE_OPTIONS));
}

/** Chooses a photo with the system gallery picker, which needs no storage permission. */
export async function chooseBillPhoto(): Promise<BillPick> {
  return fromImageResult(await ImagePicker.launchImageLibraryAsync(IMAGE_OPTIONS));
}

/** Chooses a pdf with the system document picker. */
export async function chooseBillPdf(): Promise<BillPick> {
  const result = await DocumentPicker.getDocumentAsync({ type: "application/pdf", copyToCacheDirectory: true });
  const asset = result.canceled ? undefined : result.assets?.[0];
  if (!asset) return { bill: null, problem: null };
  return prepare({ uri: asset.uri, mimeType: asset.mimeType ?? "application/pdf", name: asset.name, sizeBytes: asset.size ?? null });
}
