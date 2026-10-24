import * as SecureStore from "expo-secure-store";
import { fromBase64, toBase64 } from "../../src/formats/base64";
import {
  CannotOpenError,
  decryptBytes,
  decryptText,
  encryptBytes,
  encryptText,
  KeyUnreadableError,
  OUTBOX_KEY_NAME,
  resetCipherKey,
} from "../../src/outbox/cipher";

beforeEach(() => resetCipherKey());

const bytes = (...values: number[]) => new Uint8Array(values);

describe("cipher", () => {
  it("round trips bytes and text", async () => {
    const plain = bytes(1, 2, 3, 250, 0, 99);
    expect(Array.from(await decryptBytes(await encryptBytes(plain)))).toEqual(Array.from(plain));
    expect(await decryptText(await encryptText("Petrol, school visit ₹850"))).toBe("Petrol, school visit ₹850");
  });

  it("does not leave the plain bytes in the output", async () => {
    const encrypted = await encryptText("amount 850.00 Travel and Fuel");
    expect(encrypted).not.toContain("850");
    expect(new TextDecoder().decode(fromBase64(encrypted))).not.toContain("Travel");
  });

  it("fails to open a tampered file", async () => {
    const sealed = await encryptBytes(bytes(1, 2, 3, 4, 5));
    sealed[sealed.length - 1] ^= 0xff;
    await expect(decryptBytes(sealed)).rejects.toBeInstanceOf(CannotOpenError);
    await expect(decryptBytes(bytes(1, 2))).rejects.toBeInstanceOf(CannotOpenError);
  });

  it("makes two encryptions of the same bytes differ", async () => {
    const plain = bytes(9, 9, 9, 9);
    const a = await encryptBytes(plain);
    const b = await encryptBytes(plain);
    expect(toBase64(a)).not.toBe(toBase64(b));
  });

  it("makes a 256-bit key at first use, keeps it in secure storage and reuses it", async () => {
    expect(await SecureStore.getItemAsync(OUTBOX_KEY_NAME)).toBeNull();
    const sealed = await encryptBytes(bytes(7, 7));
    const stored = await SecureStore.getItemAsync(OUTBOX_KEY_NAME);
    expect(fromBase64(stored as string)).toHaveLength(32);
    resetCipherKey();
    expect(Array.from(await decryptBytes(sealed))).toEqual([7, 7]);
    expect(await SecureStore.getItemAsync(OUTBOX_KEY_NAME)).toBe(stored);
  });

  it("reports an unreadable key instead of making a new one over existing items", async () => {
    const sealed = await encryptBytes(bytes(5));
    const stored = await SecureStore.getItemAsync(OUTBOX_KEY_NAME);
    resetCipherKey();
    (SecureStore.getItemAsync as jest.Mock).mockRejectedValueOnce(new Error("keystore locked"));
    await expect(decryptBytes(sealed)).rejects.toBeInstanceOf(KeyUnreadableError);
    expect(await SecureStore.getItemAsync(OUTBOX_KEY_NAME)).toBe(stored);
    // The same file opens again once the key can be read.
    expect(Array.from(await decryptBytes(sealed))).toEqual([5]);
  });
});
