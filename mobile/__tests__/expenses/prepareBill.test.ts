import { BILL_TOO_LARGE_TEXT, PHOTO_UNREADABLE_TEXT } from "../../src/expenses/expenseMessages";
import { BillProblem, prepareBill, type BillSource } from "../../src/bills/prepareBill";

const manipulator = () => jest.requireMock("expo-image-manipulator").__state;
const assets = () =>
  (jest.requireMock("expo-image-picker") as { __assets: Record<string, { uri: string; mimeType: string; width: number; height: number }> }).__assets;

const MB = 1024 * 1024;

function source(overrides: Partial<BillSource> = {}): BillSource {
  return { uri: "file:///cache/photo.jpg", mimeType: "image/jpeg", width: 4000, height: 3000, ...overrides };
}

describe("prepareBill: images", () => {
  it("re-encodes a large photo as jpg with the long edge at 2000 px and compress 0.8", async () => {
    const bill = await prepareBill(source());
    expect(manipulator().calls).toHaveLength(1);
    expect(manipulator().calls[0].actions).toEqual([{ resize: { width: 2000 } }]);
    expect(manipulator().calls[0].options).toEqual({ compress: 0.8, format: "jpeg" });
    expect(bill.contentType).toBe("image/jpeg");
    expect(bill.fileName).toMatch(/^bill-[0-9a-f]+\.jpg$/);
    expect(bill.sizeBytes).toBe(700_000);
  });

  it("resizes by height for a portrait photo", async () => {
    await prepareBill(source({ width: 3000, height: 4000 }));
    expect(manipulator().calls[0].actions).toEqual([{ resize: { height: 2000 } }]);
  });

  it("never enlarges a small photo", async () => {
    await prepareBill(source({ width: 1200, height: 900 }));
    expect(manipulator().calls[0].actions).toEqual([]);
  });

  it("accepts HEIC and WebP and sends jpg", async () => {
    for (const asset of [assets().heic, assets().webp]) {
      const bill = await prepareBill({ uri: asset.uri, mimeType: asset.mimeType, width: asset.width, height: asset.height });
      expect(bill.fileName.endsWith(".jpg")).toBe(true);
      expect(bill.contentType).toBe("image/jpeg");
    }
    expect(manipulator().calls[0].options.format).toBe("jpeg");
  });

  it("converts a PNG too", async () => {
    const bill = await prepareBill(source({ uri: "file:///cache/a.png", mimeType: "image/png" }));
    expect(bill.fileName.endsWith(".jpg")).toBe(true);
  });

  it("makes each name unique", async () => {
    const a = await prepareBill(source());
    const b = await prepareBill(source());
    expect(a.fileName).not.toBe(b.fileName);
  });

  it("probes the size when the picker did not say", async () => {
    manipulator().sources.set("file:///cache/probe.jpg", { width: 3000, height: 2000 });
    await prepareBill(source({ uri: "file:///cache/probe.jpg", width: null, height: null }));
    expect(manipulator().calls[0].actions).toEqual([]);
    expect(manipulator().calls[1].actions).toEqual([{ resize: { width: 2000 } }]);
  });

  it("makes one more pass at 1600 px and compress 0.6 when over 10 MB", async () => {
    manipulator().outputBytes.push(11 * MB, 6 * MB);
    const bill = await prepareBill(source());
    expect(manipulator().calls).toHaveLength(2);
    expect(manipulator().calls[1].actions).toEqual([{ resize: { width: 1600 } }]);
    expect(manipulator().calls[1].options.compress).toBe(0.6);
    expect(bill.sizeBytes).toBe(6 * MB);
  });

  it("refuses with a plain message when still over 10 MB", async () => {
    manipulator().outputBytes.push(11 * MB, 12 * MB);
    await expect(prepareBill(source())).rejects.toMatchObject({ message: BILL_TOO_LARGE_TEXT });
  });

  it("says the photo could not be read when it cannot be decoded", async () => {
    manipulator().fail = true;
    const error = await prepareBill(source()).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(BillProblem);
    expect((error as Error).message).toBe(PHOTO_UNREADABLE_TEXT);
  });
});

describe("prepareBill: pdf", () => {
  it("passes a pdf through unchanged with a .pdf name", async () => {
    const bill = await prepareBill({ uri: "file:///cache/bill.pdf", mimeType: "application/pdf", sizeBytes: 120_000 });
    expect(manipulator().calls).toHaveLength(0);
    expect(bill).toMatchObject({ uri: "file:///cache/bill.pdf", contentType: "application/pdf", sizeBytes: 120_000 });
    expect(bill.fileName).toMatch(/\.pdf$/);
  });

  it("refuses a pdf over 10 MB", async () => {
    await expect(prepareBill({ uri: "file:///cache/big.pdf", mimeType: "application/pdf", sizeBytes: 11 * MB })).rejects.toMatchObject({
      message: BILL_TOO_LARGE_TEXT,
    });
  });
});
