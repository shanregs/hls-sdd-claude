import * as ImagePicker from "expo-image-picker";
import { BILL_CAMERA_REFUSED, chooseBillPdf, chooseBillPhoto, takeBillPhoto } from "../../src/bills/pickBill";
import { BILL_TOO_LARGE_TEXT } from "../../src/expenses/expenseMessages";

const picker = () => jest.requireMock("expo-image-picker").__state;
const documents = () => jest.requireMock("expo-document-picker").__state;

describe("pickBill", () => {
  it("takes a photo and returns it prepared as jpg", async () => {
    const pick = await takeBillPhoto();
    expect(pick.problem).toBeNull();
    expect(pick.bill?.fileName).toMatch(/\.jpg$/);
    expect(ImagePicker.launchCameraAsync).toHaveBeenCalledWith(expect.objectContaining({ quality: 1 }));
  });

  it("explains a refused camera and does not open it, leaving the gallery available", async () => {
    picker().cameraPermission = "denied";
    (ImagePicker.launchCameraAsync as jest.Mock).mockClear();
    const camera = await takeBillPhoto();
    expect(camera).toEqual({ bill: null, problem: BILL_CAMERA_REFUSED });
    expect(ImagePicker.launchCameraAsync).not.toHaveBeenCalled();
    expect((await chooseBillPhoto()).bill).not.toBeNull();
  });

  it("returns nothing when the user cancels", async () => {
    picker().camera = { canceled: true };
    picker().library = { canceled: true };
    documents().result = { canceled: true };
    expect(await takeBillPhoto()).toEqual({ bill: null, problem: null });
    expect(await chooseBillPhoto()).toEqual({ bill: null, problem: null });
    expect(await chooseBillPdf()).toEqual({ bill: null, problem: null });
  });

  it("accepts a pdf unchanged and refuses one over 10 MB", async () => {
    expect((await chooseBillPdf()).bill).toMatchObject({ contentType: "application/pdf", sizeBytes: 120_000 });
    documents().result = {
      canceled: false,
      assets: [{ uri: "file:///cache/big.pdf", name: "big.pdf", mimeType: "application/pdf", size: 11 * 1024 * 1024 }],
    };
    expect(await chooseBillPdf()).toEqual({ bill: null, problem: BILL_TOO_LARGE_TEXT });
  });
});
