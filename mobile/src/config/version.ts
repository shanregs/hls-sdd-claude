import * as Application from "expo-application";

/** The app's own version as `major.minor.patch`, the form the server compares. */
export function currentAppVersion(): string {
  const raw = Application.nativeApplicationVersion ?? "0.0.0";
  const parts = raw.split(/[.-]/).slice(0, 3);
  while (parts.length < 3) parts.push("0");
  return parts.map((p) => (/^\d+$/.test(p) ? p : "0")).join(".");
}

/** Numeric comparison of dotted versions: negative when a is older than b. */
export function compareVersions(a: string, b: string): number {
  const pa = a.split(".").map((n) => Number.parseInt(n, 10) || 0);
  const pb = b.split(".").map((n) => Number.parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i += 1) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) return diff;
  }
  return 0;
}
