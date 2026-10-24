import * as Crypto from "expo-crypto";
import * as FileSystem from "expo-file-system/legacy";
import { manipulateAsync, SaveFormat } from "expo-image-manipulator";
import { BILL_TOO_LARGE_TEXT, PHOTO_UNREADABLE_TEXT } from "../expenses/expenseMessages";

/** A file chosen on the phone, before it is prepared. */
export interface BillSource {
  uri: string;
  mimeType?: string | null;
  name?: string | null;
  sizeBytes?: number | null;
  width?: number | null;
  height?: number | null;
}

/** A bill ready to be kept and sent: a jpg of at most 2000 px, or an untouched pdf, always under 10 MB. */
export interface PreparedBill {
  uri: string;
  fileName: string;
  contentType: "image/jpeg" | "application/pdf";
  sizeBytes: number;
}

/** The bill cannot be used; the message is meant to be shown as it is. */
export class BillProblem extends Error {
  constructor(message: string) {
    super(message);
    this.name = "BillProblem";
  }
}

export const MAX_BILL_BYTES = 10 * 1024 * 1024;
const LONG_EDGE = 2000;
const SMALLER_EDGE = 1600;

const shortId = () => Crypto.randomUUID().replace(/-/g, "").slice(-8);
const isPdf = (s: BillSource) => s.mimeType === "application/pdf" || /\.pdf$/i.test(s.name ?? s.uri);

async function sizeOf(uri: string): Promise<number> {
  const info = await FileSystem.getInfoAsync(uri);
  return info.exists && "size" in info ? info.size : 0;
}

async function dimensionsOf(source: BillSource): Promise<{ width: number; height: number }> {
  if (source.width && source.height) return { width: source.width, height: source.height };
  const probe = await manipulateAsync(source.uri, [], { format: SaveFormat.JPEG, compress: 1 });
  return { width: probe.width, height: probe.height };
}

function shrinkTo(edge: number, size: { width: number; height: number }) {
  if (Math.max(size.width, size.height) <= edge) return [];
  return [{ resize: size.width >= size.height ? { width: edge } : { height: edge } }];
}

/**
 * Any image (jpeg, png, HEIC, WebP) becomes a jpg with the long edge at most 2000 px, never enlarged, at compress 0.8; over
 * 10 MB gets one more pass at 1600 px and compress 0.6, and still over is refused. A pdf is checked for 10 MB and passed
 * through unchanged. The output name is generated and unique, which the lost-reply check of the sync relies on.
 */
export async function prepareBill(source: BillSource): Promise<PreparedBill> {
  if (isPdf(source)) {
    const sizeBytes = source.sizeBytes ?? (await sizeOf(source.uri));
    if (sizeBytes > MAX_BILL_BYTES) throw new BillProblem(BILL_TOO_LARGE_TEXT);
    return { uri: source.uri, fileName: `bill-${shortId()}.pdf`, contentType: "application/pdf", sizeBytes };
  }

  let size: { width: number; height: number };
  let result;
  try {
    size = await dimensionsOf(source);
    result = await manipulateAsync(source.uri, shrinkTo(LONG_EDGE, size), { compress: 0.8, format: SaveFormat.JPEG });
  } catch {
    throw new BillProblem(PHOTO_UNREADABLE_TEXT);
  }
  let sizeBytes = await sizeOf(result.uri);
  if (sizeBytes > MAX_BILL_BYTES) {
    try {
      result = await manipulateAsync(source.uri, shrinkTo(SMALLER_EDGE, size), { compress: 0.6, format: SaveFormat.JPEG });
    } catch {
      throw new BillProblem(PHOTO_UNREADABLE_TEXT);
    }
    sizeBytes = await sizeOf(result.uri);
    if (sizeBytes > MAX_BILL_BYTES) throw new BillProblem(BILL_TOO_LARGE_TEXT);
  }
  return { uri: result.uri, fileName: `bill-${shortId()}.jpg`, contentType: "image/jpeg", sizeBytes };
}
