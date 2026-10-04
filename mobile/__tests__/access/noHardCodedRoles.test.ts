import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

/**
 * Spec 018 FR-009, T049: menus and screens come only from the server access model. No source file
 * in the app may decide anything by a role name, so a quoted role code anywhere in `src/` fails the
 * build.
 */
function sourceFiles(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) return sourceFiles(path);
    return /\.(ts|tsx)$/.test(name) ? [path] : [];
  });
}

const QUOTED_ROLE = /["'`](ADMIN|DIRECTOR|MANAGER|TEACHER|SYSTEM)["'`]/;

describe("no hard-coded roles in the app", () => {
  const files = sourceFiles(join(__dirname, "..", "..", "src"));

  it("scans the source tree", () => {
    expect(files.length).toBeGreaterThan(10);
  });

  it.each(files.map((f) => [f.replace(/.*[\\/]src[\\/]/, "src/"), f]))("%s has no role code literal", (_name, file) => {
    const match = readFileSync(file, "utf8").match(QUOTED_ROLE);
    expect(match?.[0] ?? null).toBeNull();
  });
});
