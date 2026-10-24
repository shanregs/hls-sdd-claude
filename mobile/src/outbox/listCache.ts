import * as FileSystem from "expo-file-system/legacy";
import type { Area, ClaimRow, LinkOption } from "../api/expensesApi";
import { decryptText, encryptText } from "./cipher";
import { ownerDir } from "./outboxStore";

/**
 * The last loaded list, the areas and the link options of one user, kept encrypted so My Expenses can be read with no
 * connection (spec 036 research §9, §12). One file per user; replaced on each successful load.
 */
export interface ListCache {
  /** ISO instant of the last successful list load; null when only areas or link options were cached. */
  loadedAt: string | null;
  owed: string;
  rows: ClaimRow[];
  areas: Area[];
  /** Last link options per category key. */
  linkOptions: Record<string, LinkOption[]>;
}

const EMPTY: ListCache = { loadedAt: null, owed: "0.00", rows: [], areas: [], linkOptions: {} };
const fileOf = (owner: string) => `${ownerDir(owner)}list-cache.enc`;

/** The cache, or null when there is none or it cannot be opened (a cache is never worth an error). */
export async function readListCache(owner: string): Promise<ListCache | null> {
  try {
    const info = await FileSystem.getInfoAsync(fileOf(owner));
    if (!info.exists) return null;
    const text = await decryptText(await FileSystem.readAsStringAsync(fileOf(owner), { encoding: FileSystem.EncodingType.Base64 }));
    return { ...EMPTY, ...(JSON.parse(text) as Partial<ListCache>) };
  } catch {
    return null;
  }
}

/** Changes part of the cache and keeps the rest. A failure to write is ignored. */
export async function updateListCache(owner: string, change: Partial<ListCache>): Promise<void> {
  try {
    const current = (await readListCache(owner)) ?? EMPTY;
    await FileSystem.makeDirectoryAsync(ownerDir(owner), { intermediates: true });
    await FileSystem.writeAsStringAsync(fileOf(owner), await encryptText(JSON.stringify({ ...current, ...change })), {
      encoding: FileSystem.EncodingType.Base64,
    });
  } catch {
    // The cache is a convenience.
  }
}

export async function cacheLinkOptions(owner: string, category: string, options: LinkOption[]): Promise<void> {
  const current = (await readListCache(owner)) ?? EMPTY;
  await updateListCache(owner, { linkOptions: { ...current.linkOptions, [category]: options } });
}
