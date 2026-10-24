import { fireEvent, screen } from "@testing-library/react-native";
import { BILL_CAMERA_REFUSED } from "../../src/bills/pickBill";
import { PHOTO_UNREADABLE_TEXT } from "../../src/expenses/expenseMessages";
import type { MultipartBody } from "../support/fakeServer";
import { openExpenseScreen } from "../support/expenseApp";
import { fillClaim, openAddForm, press, takeBill } from "../support/expenseActions";

const picker = () => jest.requireMock("expo-image-picker").__state;
const assets = () => jest.requireMock("expo-image-picker").__assets;
const manip = () => jest.requireMock("expo-image-manipulator").__state;

const MB = 1024 * 1024;

async function startForm(setup = {}) {
  const opened = await openExpenseScreen(setup);
  await openAddForm();
  await fillClaim({ amount: "850", category: "Marketing" });
  return opened;
}

/** Spec 036 T020: bill photos and files (US1). */
describe("bill slots", () => {
  it("previews a photo, retakes it and removes it", async () => {
    await startForm();
    await takeBill();
    expect(screen.getByLabelText("Bill 1 preview")).toBeTruthy();
    expect(screen.getByText("1 of 5")).toBeTruthy();
    await press("Retake bill 1");
    await screen.findByLabelText("Remove bill 1");
    expect(screen.queryByLabelText("Remove bill 2")).toBeNull();
    await press("Remove bill 1");
    expect(screen.queryByLabelText("Remove bill 1")).toBeNull();
    expect(screen.getByText("0 of 5")).toBeTruthy();
    expect(screen.getByLabelText("Reason there is no bill")).toBeTruthy();
  });

  it("accepts a HEIC and a WebP photo and uploads jpg parts of at most 2000 px and 10 MB", async () => {
    const { expenses, server } = await startForm();
    picker().library = { canceled: false, assets: [assets().heic] };
    await press("Choose photo");
    await screen.findByLabelText("Remove bill 1");
    picker().library = { canceled: false, assets: [assets().webp] };
    await press("Choose photo");
    await screen.findByLabelText("Remove bill 2");
    await press("Submit");
    await screen.findByText("Submitted.");

    const parts = server.callsTo("POST /api/v1/my-expenses/{id}/bills").map((c) => (c.body as MultipartBody).multipart[0]);
    expect(parts).toHaveLength(2);
    for (const part of parts) {
      expect(part.name).toMatch(/^bill-[0-9a-f]+\.jpg$/);
      expect(part.type).toBe("image/jpeg");
    }
    // The long edge of the 4032 x 3024 HEIC was scaled to 2000; the 1600 x 1200 WebP was not enlarged.
    expect(manip().calls[0].actions).toEqual([{ resize: { width: 2000 } }]);
    expect(manip().calls[1].actions).toEqual([]);
    expect(manip().calls.every((c: { options: { format: string } }) => c.options.format === "jpeg")).toBe(true);
    expect(expenses.claims[0].billCount).toBe(2);
  });

  it("passes a pdf through unchanged", async () => {
    const { server } = await startForm();
    await press("Choose PDF");
    await screen.findByLabelText("Remove bill 1");
    expect(screen.queryByLabelText("Retake bill 1")).toBeNull();
    await press("Submit");
    await screen.findByText("Submitted.");
    const part = (server.callsTo("POST /api/v1/my-expenses/{id}/bills")[0].body as MultipartBody).multipart[0];
    expect(part).toMatchObject({ type: "application/pdf" });
    expect(part.name).toMatch(/\.pdf$/);
    expect(manip().calls).toHaveLength(0);
  });

  it("refuses a pdf over 10 MB with a plain message", async () => {
    await startForm();
    jest.requireMock("expo-document-picker").__state.result = {
      canceled: false,
      assets: [{ uri: "file:///cache/big.pdf", name: "big.pdf", mimeType: "application/pdf", size: 11 * MB }],
    };
    await press("Choose PDF");
    expect(await screen.findByText(/larger than 10 MB/)).toBeTruthy();
    expect(screen.queryByLabelText("Remove bill 1")).toBeNull();
  });

  it("refuses a sixth bill with the reason", async () => {
    await startForm();
    for (let n = 1; n <= 5; n += 1) await takeBill(n);
    expect(screen.getByText("5 of 5")).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Take photo"));
    expect(await screen.findByText("A claim can have at most 5 bills.")).toBeTruthy();
    expect(screen.queryByLabelText("Remove bill 6")).toBeNull();
  });

  it("explains a refused camera, keeps the gallery and the no-bill reason available", async () => {
    await startForm();
    picker().cameraPermission = "denied";
    await fireEvent.press(screen.getByLabelText("Take photo"));
    expect(await screen.findByText(BILL_CAMERA_REFUSED)).toBeTruthy();
    expect(screen.getByLabelText("Choose photo")).toBeTruthy();
    expect(screen.getByLabelText("Reason there is no bill")).toBeTruthy();
    await press("Choose photo");
    await screen.findByLabelText("Remove bill 1");
  });

  it("says an unreadable photo could not be read and leaves the claim unchanged", async () => {
    const { expenses } = await startForm();
    manip().fail = true;
    await fireEvent.press(screen.getByLabelText("Take photo"));
    expect(await screen.findByText(PHOTO_UNREADABLE_TEXT)).toBeTruthy();
    expect(screen.queryByLabelText("Remove bill 1")).toBeNull();
    expect(screen.getByLabelText("Amount").props.value).toBe("850");
    expect(expenses.claims).toHaveLength(0);
  });

  it("lets a claim without a bill be submitted with the no-bill reason", async () => {
    const { expenses } = await startForm();
    await fireEvent.changeText(screen.getByLabelText("Reason there is no bill"), "Paid in cash at a stall");
    await press("Submit");
    await screen.findByText("Submitted.");
    expect(expenses.claims[0]).toMatchObject({ status: "SUBMITTED", billCount: 0, noBillReason: "Paid in cash at a stall" });
  });
});
