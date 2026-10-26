import { gcm } from "@noble/ciphers/aes.js";
import * as Crypto from "expo-crypto";
import * as SecureStore from "expo-secure-store";
import { fromBase64, toBase64 } from "../formats/base64";

/**
 * AES-256-GCM for the files kept on the phone (spec 036 research §5). A random 12-byte nonce is put in front of each
 * encrypted file. The 256-bit key is made once and kept in the Android Keystore through SecureStore; it survives sign-out
 * (waiting claims must) and is never sent anywhere.
 */
export const OUTBOX_KEY_NAME = "hls.outboxKey";
const NONCE_BYTES = 12;
const KEY_BYTES = 32;

/** The stored key cannot be read, so existing items cannot be opened. No new key is made over them. */
export class KeyUnreadableError extends Error {
  constructor() {
    super("The key to the saved claims cannot be read.");
    this.name = "KeyUnreadableError";
  }
}

/** A file could not be opened: it was changed, damaged, or made with another key. */
export class CannotOpenError extends Error {
  constructor() {
    super("This saved item could not be opened.");
    this.name = "CannotOpenError";
  }
}

let cachedKey: Uint8Array | null = null;

/** Forgets the key held in memory (tests, and after a failed read). */
export function resetCipherKey(): void {
  cachedKey = null;
}

async function getKey(): Promise<Uint8Array> {
  if (cachedKey) return cachedKey;
  let stored: string | null;
  try {
    stored = await SecureStore.getItemAsync(OUTBOX_KEY_NAME);
  } catch {
    throw new KeyUnreadableError();
  }
  if (stored) {
    const key = fromBase64(stored);
    if (key.length !== KEY_BYTES) throw new KeyUnreadableError();
    cachedKey = key;
    return key;
  }
  const created = Crypto.getRandomBytes(KEY_BYTES);
  await SecureStore.setItemAsync(OUTBOX_KEY_NAME, toBase64(created));
  cachedKey = created;
  return created;
}

export async function encryptBytes(plain: Uint8Array): Promise<Uint8Array> {
  const key = await getKey();
  const nonce = Crypto.getRandomBytes(NONCE_BYTES);
  const sealed = gcm(key, nonce).encrypt(plain);
  const out = new Uint8Array(NONCE_BYTES + sealed.length);
  out.set(nonce, 0);
  out.set(sealed, NONCE_BYTES);
  return out;
}

export async function decryptBytes(data: Uint8Array): Promise<Uint8Array> {
  const key = await getKey();
  if (data.length <= NONCE_BYTES) throw new CannotOpenError();
  try {
    return gcm(key, data.slice(0, NONCE_BYTES)).decrypt(data.slice(NONCE_BYTES));
  } catch {
    throw new CannotOpenError();
  }
}

/** Encrypts text and returns it as base64, ready to be written as a file. */
export async function encryptText(text: string): Promise<string> {
  return toBase64(await encryptBytes(new TextEncoder().encode(text)));
}

export async function decryptText(base64: string): Promise<string> {
  return new TextDecoder().decode(await decryptBytes(fromBase64(base64)));
}
