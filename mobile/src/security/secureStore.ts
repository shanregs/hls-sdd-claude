import * as SecureStore from "expo-secure-store";

/**
 * The only place the renewal credential is persisted: Android Keystore-backed storage via Expo
 * SecureStore (spec 018 FR-004, research.md §4). The short-lived access token is never stored (see
 * memoryToken.ts).
 */
const RENEWAL_CREDENTIAL_KEY = "hls.renewalCredential";

export async function saveRenewalCredential(value: string): Promise<void> {
  await SecureStore.setItemAsync(RENEWAL_CREDENTIAL_KEY, value);
}

export async function readRenewalCredential(): Promise<string | null> {
  try {
    return await SecureStore.getItemAsync(RENEWAL_CREDENTIAL_KEY);
  } catch {
    // An unreadable store is the same as no stored session: the user signs in again.
    return null;
  }
}

export async function clearRenewalCredential(): Promise<void> {
  try {
    await SecureStore.deleteItemAsync(RENEWAL_CREDENTIAL_KEY);
  } catch {
    // Nothing more can be done; the credential is also dropped from memory by the caller.
  }
}
