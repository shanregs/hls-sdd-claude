import { fromBase64 } from "../../src/formats/base64";
import { listItems, readBill, removeItem, saveItem, StorageFullError, updateItem, withBillFile } from "../../src/outbox/outboxStore";
import type { OutboxItem } from "../../src/outbox/types";
import { makeItem } from "../support/outboxFixtures";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;
const fileNames = (): string[] => [...fsState().files.keys()];

describe("outboxStore", () => {
  it("saves an item and lists it back for its owner", async () => {
    const item = makeItem({ itemId: "i-1" });
    await saveItem(item);
    const listed = await listItems("u-1");
    expect(listed.items).toHaveLength(1);
    expect(listed.items[0]).toEqual(item);
  });

  it("never shows the items of one owner to another", async () => {
    await saveItem(makeItem({ itemId: "i-1", ownerUserId: "u-1" }));
    await saveItem(makeItem({ itemId: "i-2", ownerUserId: "u-2" }));
    expect((await listItems("u-1")).items.map((i) => i.itemId)).toEqual(["i-1"]);
    expect((await listItems("u-2")).items.map((i) => i.itemId)).toEqual(["i-2"]);
    expect((await listItems("u-3")).items).toEqual([]);
  });

  it("lists in the order the items were captured", async () => {
    await saveItem(makeItem({ itemId: "late", createdAt: "2026-10-23T10:00:00.000Z" }));
    await saveItem(makeItem({ itemId: "early", createdAt: "2026-10-23T08:00:00.000Z" }));
    expect((await listItems("u-1")).items.map((i) => i.itemId)).toEqual(["early", "late"]);
  });

  it("turns SENDING into WAITING after a restart", async () => {
    await saveItem(makeItem({ itemId: "i-1", state: "SENDING" }));
    expect((await listItems("u-1")).items[0].state).toBe("WAITING");
  });

  it("encrypts the item and the bill: no plain text or bytes reach the file system", async () => {
    fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
    await saveItem(makeItem({ itemId: "i-1", fields: { note: "Petrol for the school visit" } }), { "bill-a": "file:///cache/photo.jpg" });
    for (const [uri, content] of fsState().files as Map<string, string>) {
      if (uri.startsWith("file:///cache/")) continue;
      expect(content).not.toContain("Petrol");
      expect(content).not.toContain("SEVMTE9");
      expect(new TextDecoder().decode(fromBase64(content))).not.toContain("Petrol");
      expect(new TextDecoder().decode(fromBase64(content))).not.toContain("HELLO");
    }
    expect(fileNames().filter((n) => n.includes("/outbox/"))).toEqual(
      expect.arrayContaining([expect.stringContaining("item.enc"), expect.stringContaining("bill-bill-a.enc")]),
    );
  });

  it("reads a saved bill back, and gives a temporary file for an upload that is deleted afterwards", async () => {
    fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
    await saveItem(makeItem({ itemId: "i-1" }), { "bill-a": "file:///cache/photo.jpg" });
    expect(new TextDecoder().decode(await readBill("u-1", "i-1", "bill-a"))).toBe("HELLO_BILL_BYTES");

    let seen = "";
    await withBillFile("u-1", "i-1", "bill-a", "bill-1.jpg", async (uri) => {
      seen = uri;
      expect(fsState().files.get(uri)).toBe("SEVMTE9fQklMTF9CWVRFUw==");
    });
    expect(seen).toMatch(/^file:\/\/\/cache\//);
    expect(fsState().files.has(seen)).toBe(false);
  });

  it("deletes the temporary file even when the upload fails", async () => {
    fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
    await saveItem(makeItem({ itemId: "i-1" }), { "bill-a": "file:///cache/photo.jpg" });
    let seen = "";
    await expect(
      withBillFile("u-1", "i-1", "bill-a", "bill-1.jpg", async (uri) => {
        seen = uri;
        throw new Error("upload failed");
      }),
    ).rejects.toThrow("upload failed");
    expect(fsState().files.has(seen)).toBe(false);
  });

  it("updates an item and drops the bill files it no longer lists", async () => {
    fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
    const item = makeItem({ itemId: "i-1" });
    await saveItem(item, { "bill-a": "file:///cache/photo.jpg" });
    const next: OutboxItem = { ...item, bills: [], fields: { ...item.fields, note: "changed" } };
    await updateItem(next);
    expect(fileNames().some((n) => n.includes("bill-bill-a.enc"))).toBe(false);
    expect((await listItems("u-1")).items[0].fields.note).toBe("changed");
  });

  it("removes every file of an item", async () => {
    fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
    await saveItem(makeItem({ itemId: "i-1" }), { "bill-a": "file:///cache/photo.jpg" });
    await removeItem("u-1", "i-1");
    expect(fileNames().filter((n) => n.includes("/outbox/"))).toEqual([]);
    expect((await listItems("u-1")).items).toEqual([]);
  });

  it("throws StorageFullError and leaves no half-written item when the phone is full", async () => {
    fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
    fsState().full = true;
    await expect(saveItem(makeItem({ itemId: "i-1" }), { "bill-a": "file:///cache/photo.jpg" })).rejects.toBeInstanceOf(StorageFullError);
    fsState().full = false;
    expect(fileNames().filter((n) => n.includes("/outbox/"))).toEqual([]);
    expect((await listItems("u-1")).items).toEqual([]);
  });

  it("keeps the old item when an update fails on a full phone", async () => {
    const item = makeItem({ itemId: "i-1" });
    await saveItem(item);
    fsState().full = true;
    await expect(updateItem({ ...item, fields: { ...item.fields, note: "new" } })).rejects.toBeInstanceOf(StorageFullError);
    fsState().full = false;
    expect((await listItems("u-1")).items[0].fields.note).toBe(item.fields.note);
  });

  it("lists an item it cannot open as unreadable so it can be deleted", async () => {
    await saveItem(makeItem({ itemId: "i-1" }));
    const path = fileNames().find((n) => n.endsWith("item.enc")) as string;
    fsState().files.set(path, "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
    const listed = await listItems("u-1");
    expect(listed.items).toEqual([]);
    expect(listed.unreadable).toEqual(["i-1"]);
  });
});
