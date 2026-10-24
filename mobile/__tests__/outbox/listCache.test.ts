import { cacheLinkOptions, readListCache, updateListCache } from "../../src/outbox/listCache";
import { claimRow, linkOption, area } from "../support/expenseFixtures";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;

describe("listCache", () => {
  it("returns null before anything is cached", async () => {
    expect(await readListCache("u-1")).toBeNull();
  });

  it("keeps the list, areas and link options and returns them", async () => {
    await updateListCache("u-1", { loadedAt: "2026-10-23T09:00:00.000Z", owed: "1850.00", rows: [claimRow({ id: "c-1" })] });
    await updateListCache("u-1", { areas: [area()] });
    await cacheLinkOptions("u-1", "TRAVEL_FUEL", [linkOption()]);
    const cache = await readListCache("u-1");
    expect(cache?.rows.map((r) => r.id)).toEqual(["c-1"]);
    expect(cache?.owed).toBe("1850.00");
    expect(cache?.areas).toEqual([area()]);
    expect(cache?.linkOptions.TRAVEL_FUEL).toHaveLength(1);
  });

  it("keeps one cache per user", async () => {
    await updateListCache("u-1", { rows: [claimRow({ id: "mine" })] });
    expect(await readListCache("u-2")).toBeNull();
  });

  it("is encrypted at rest", async () => {
    await updateListCache("u-1", { rows: [claimRow({ id: "c-1", note: "Petrol for Green Valley" })] });
    for (const [uri, content] of fsState().files as Map<string, string>) {
      if (!uri.includes("/outbox/")) continue;
      expect(content).not.toContain("Petrol");
      expect(Buffer.from(content, "base64").toString("utf8")).not.toContain("Green Valley");
    }
  });

  it("returns null for a cache that cannot be opened", async () => {
    await updateListCache("u-1", { rows: [claimRow()] });
    const path = [...(fsState().files as Map<string, string>).keys()].find((k) => k.endsWith("list-cache.enc")) as string;
    fsState().files.set(path, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    expect(await readListCache("u-1")).toBeNull();
  });
});
