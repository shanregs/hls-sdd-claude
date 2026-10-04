/**
 * The 15-minute access token lives only in memory and is never persisted (spec 018 FR-004,
 * research.md §4). A killed app simply renews from the secure-store credential on the next start.
 */
let accessToken: string | null = null;

export function setAccessToken(token: string | null): void {
  accessToken = token;
}

export function getAccessToken(): string | null {
  return accessToken;
}
