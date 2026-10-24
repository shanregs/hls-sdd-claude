import * as Crypto from "expo-crypto";
import * as FileSystem from "expo-file-system/legacy";
import { fromBase64, toBase64 } from "../formats/base64";
import { CannotOpenError, decryptBytes, decryptText, encryptBytes, encryptText, KeyUnreadableError } from "./cipher";
import type { OutboxItem } from "./types";

/**
 * The encrypted files of the outbox (spec 036 research §5, data-model.md A): `outbox/<owner>/<itemId>/item.enc` and one
 * `bill-<localId>.enc` per photo, all AES-256-GCM. An item is written for, listed for and sent for its owner only.
 */

/** The phone could not take the write (storage full). Nothing half-written is left behind. */
export class StorageFullError extends Error {
  constructor() {
    super("Not enough space to save this on the phone.");
    this.name = "StorageFullError";
  }
}

const root = () => `${FileSystem.documentDirectory}outbox/`;
export const ownerDir = (owner: string) => `${root()}${owner}/`;
const itemDir = (owner: string, itemId: string) => `${ownerDir(owner)}${itemId}/`;
const itemFile = (owner: string, itemId: string) => `${itemDir(owner, itemId)}item.enc`;
const billFile = (owner: string, itemId: string, localId: string) => `${itemDir(owner, itemId)}bill-${localId}.enc`;

/** A write that fails for any reason except an unreadable key means the phone is full. */
async function writing<T>(work: () => Promise<T>): Promise<T> {
  try {
    return await work();
  } catch (error) {
    if (error instanceof KeyUnreadableError) throw error;
    throw new StorageFullError();
  }
}

/** SENDING is in memory only: whatever is written (or read after a restart) is WAITING. */
const normalised = (item: OutboxItem): OutboxItem => (item.state === "SENDING" ? { ...item, state: "WAITING" } : item);

async function writeBills(item: OutboxItem, sources: Record<string, string>, written: string[]): Promise<void> {
  for (const [localId, sourceUri] of Object.entries(sources)) {
    const plain = fromBase64(await FileSystem.readAsStringAsync(sourceUri, { encoding: FileSystem.EncodingType.Base64 }));
    const target = billFile(item.ownerUserId, item.itemId, localId);
    await FileSystem.writeAsStringAsync(target, toBase64(await encryptBytes(plain)), { encoding: FileSystem.EncodingType.Base64 });
    written.push(target);
  }
}

async function writeItemFile(item: OutboxItem): Promise<void> {
  await FileSystem.writeAsStringAsync(itemFile(item.ownerUserId, item.itemId), await encryptText(JSON.stringify(normalised(item))), {
    encoding: FileSystem.EncodingType.Base64,
  });
}

/**
 * Saves a new item. `billSources` maps a bill's local id to the prepared photo file to keep (encrypted) with the item. The
 * item file is written last, so a failure leaves nothing behind: the folder is removed and StorageFullError is thrown.
 */
export async function saveItem(item: OutboxItem, billSources: Record<string, string> = {}): Promise<void> {
  const dir = itemDir(item.ownerUserId, item.itemId);
  try {
    await writing(async () => {
      await FileSystem.makeDirectoryAsync(dir, { intermediates: true });
      await writeBills(item, billSources, []);
      await writeItemFile(item);
    });
  } catch (error) {
    await FileSystem.deleteAsync(dir, { idempotent: true }).catch(() => undefined);
    throw error;
  }
}

/**
 * Replaces an item. New photos are written first; the item file replaces the old one only after they are in place, and
 * bill files the item no longer lists are removed. A failure keeps the previous version.
 */
export async function updateItem(item: OutboxItem, billSources: Record<string, string> = {}): Promise<void> {
  const written: string[] = [];
  try {
    await writing(async () => {
      await writeBills(item, billSources, written);
      await writeItemFile(item);
    });
  } catch (error) {
    for (const uri of written) await FileSystem.deleteAsync(uri, { idempotent: true }).catch(() => undefined);
    throw error;
  }
  const keep = new Set(item.bills.map((b) => `bill-${b.localId}.enc`));
  const dir = itemDir(item.ownerUserId, item.itemId);
  try {
    for (const name of await FileSystem.readDirectoryAsync(dir)) {
      if (name.startsWith("bill-") && !keep.has(name)) await FileSystem.deleteAsync(`${dir}${name}`, { idempotent: true });
    }
  } catch {
    // Leftover photo files are removed with the item.
  }
}

export interface ListedItems {
  items: OutboxItem[];
  /** Folders whose item file could not be opened (damaged, or made with another key); the user can delete them. */
  unreadable: string[];
}

/** The owner's items in the order they were captured. A key that cannot be read is thrown, not hidden. */
export async function listItems(owner: string): Promise<ListedItems> {
  const items: OutboxItem[] = [];
  const unreadable: string[] = [];
  let names: string[] = [];
  try {
    names = await FileSystem.readDirectoryAsync(ownerDir(owner));
  } catch {
    return { items, unreadable };
  }
  for (const name of names) {
    const info = await FileSystem.getInfoAsync(itemFile(owner, name));
    if (!info.exists) continue;
    try {
      const text = await decryptText(await FileSystem.readAsStringAsync(itemFile(owner, name), { encoding: FileSystem.EncodingType.Base64 }));
      items.push(normalised(JSON.parse(text) as OutboxItem));
    } catch (error) {
      if (error instanceof KeyUnreadableError) throw error;
      unreadable.push(name);
    }
  }
  items.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  return { items, unreadable };
}

/** Deletes the item folder with every file in it. */
export async function removeItem(owner: string, itemId: string): Promise<void> {
  await FileSystem.deleteAsync(itemDir(owner, itemId), { idempotent: true });
}

/** A saved photo's bytes, for a preview. */
export async function readBill(owner: string, itemId: string, localId: string): Promise<Uint8Array> {
  const text = await FileSystem.readAsStringAsync(billFile(owner, itemId, localId), { encoding: FileSystem.EncodingType.Base64 });
  return decryptBytes(fromBase64(text));
}

/**
 * Decrypts a photo to a temporary file in the cache for an upload (the upload needs a file address), runs `use`, and
 * deletes the temporary file afterwards, whether or not the upload worked.
 */
export async function withBillFile<T>(
  owner: string,
  itemId: string,
  localId: string,
  fileName: string,
  use: (uri: string) => Promise<T>,
): Promise<T> {
  const plain = await readBill(owner, itemId, localId);
  const temp = `${FileSystem.cacheDirectory}outbox-${Crypto.randomUUID()}-${fileName}`;
  await FileSystem.writeAsStringAsync(temp, toBase64(plain), { encoding: FileSystem.EncodingType.Base64 });
  try {
    return await use(temp);
  } finally {
    await FileSystem.deleteAsync(temp, { idempotent: true }).catch(() => undefined);
  }
}

export { CannotOpenError };
