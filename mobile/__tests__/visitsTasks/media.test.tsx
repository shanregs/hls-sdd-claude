import { fireEvent, render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { ApiError, NoConnectionError } from "../../src/api/errors";
import { AttachmentList } from "../../src/media/AttachmentList";
import { PhotoSlots } from "../../src/media/PhotoSlots";
import type { PickedPhoto } from "../../src/media/pickPhoto";
import { uploadPhotos } from "../../src/media/uploadPhotos";

/** Spec 034 T012: photos for a visit or a task, the count against the limit, and files already attached. */
const picker = jest.requireMock("expo-image-picker").__state as Record<string, unknown>;
const wrap = (node: React.ReactElement) => render(<PaperProvider>{node}</PaperProvider>);
const photo = (name: string): PickedPhoto => ({ uri: `file:///cache/${name}`, name, type: "image/jpeg", sizeBytes: 348_160 });

describe("PhotoSlots", () => {
  it("counts the photos against the limit, adds from the camera and the gallery, and removes", async () => {
    const onChange = jest.fn();
    const first = await wrap(<PhotoSlots photos={[]} onChange={onChange} limit={5} alreadyAttached={2} />);
    expect(screen.getByLabelText("Photos, 2 of 5")).toBeTruthy();

    await fireEvent.press(screen.getByText("Take a photo"));
    expect(onChange).toHaveBeenLastCalledWith([expect.objectContaining({ name: "camera.jpg", type: "image/jpeg" })]);
    await first.unmount();

    const camera = photo("camera.jpg");
    await wrap(<PhotoSlots photos={[camera]} onChange={onChange} limit={5} alreadyAttached={2} />);
    expect(screen.getByLabelText("Photos, 3 of 5")).toBeTruthy();
    expect(screen.getByText("camera.jpg, JPG, 340 KB")).toBeTruthy();
    await fireEvent.press(screen.getByText("Choose from gallery"));
    expect(onChange).toHaveBeenLastCalledWith([camera, expect.objectContaining({ name: "gallery.png", type: "image/png" })]);
    await fireEvent.press(screen.getByLabelText("Remove camera.jpg"));
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it("stops offering photos at the limit and says so", async () => {
    await wrap(<PhotoSlots photos={[photo("a.jpg")]} onChange={jest.fn()} limit={3} alreadyAttached={2} />);

    expect(screen.getByLabelText("Photos, 3 of 3")).toBeTruthy();
    expect(screen.queryByText("Take a photo")).toBeNull();
    expect(screen.queryByText("Choose from gallery")).toBeNull();
    expect(screen.getByText("The limit of 3 photos is reached.")).toBeTruthy();
  });

  it("explains a refused camera and still allows the gallery", async () => {
    picker.cameraPermission = "denied";
    const onChange = jest.fn();
    await wrap(<PhotoSlots photos={[]} onChange={onChange} limit={5} />);

    await fireEvent.press(screen.getByText("Take a photo"));

    expect(await screen.findByText(/The camera is not allowed/)).toBeTruthy();
    expect(onChange).not.toHaveBeenCalled();
    await fireEvent.press(screen.getByText("Choose from gallery"));
    expect(onChange).toHaveBeenCalledTimes(1);
  });

  it("does nothing when the picker is cancelled", async () => {
    picker.camera = { canceled: true };
    const onChange = jest.fn();
    await wrap(<PhotoSlots photos={[]} onChange={onChange} limit={5} />);

    await fireEvent.press(screen.getByText("Take a photo"));

    expect(onChange).not.toHaveBeenCalled();
  });
});

describe("AttachmentList", () => {
  const image = { fileId: "f1", name: "gate.jpg", contentType: "image/jpeg", sizeBytes: 2048 };
  const pdf = { fileId: "f2", name: "brochure.pdf", contentType: "application/pdf", sizeBytes: 1_048_576 };
  const view = { dataUri: "data:image/jpeg;base64,AAAA", contentType: "image/jpeg", sizeBytes: 2048, fileName: "gate.jpg" };

  it("shows name, type and size for each file, a thumbnail for an image only, and a note for a document", async () => {
    const open = jest.fn(async () => view);
    await wrap(<AttachmentList files={[image, pdf]} open={open} />);

    expect(await screen.findByLabelText("Photo gate.jpg")).toBeTruthy();
    expect(screen.getByText("gate.jpg, JPG, 2 KB")).toBeTruthy();
    expect(screen.getByText("brochure.pdf, PDF, 1.0 MB")).toBeTruthy();
    expect(screen.getByText("Open it on the web.")).toBeTruthy();
    expect(open).toHaveBeenCalledTimes(1);
    expect(open).toHaveBeenCalledWith("f1");
  });

  it("says a photo could not be loaded and loads it on Try again", async () => {
    const open = jest.fn().mockRejectedValueOnce(new Error("no")).mockResolvedValue(view);
    await wrap(<AttachmentList files={[image]} open={open} />);
    expect(await screen.findByText("The photo could not be loaded.")).toBeTruthy();

    await fireEvent.press(screen.getByLabelText("Try again for gate.jpg"));

    expect(await screen.findByLabelText("Photo gate.jpg")).toBeTruthy();
  });

  it("shows nothing for no files", async () => {
    await wrap(<AttachmentList files={[]} open={jest.fn()} />);

    expect(screen.queryByLabelText("Attachments")).toBeNull();
  });
});

describe("uploadPhotos", () => {
  it("sends the photos one after another and reports each failure with the server's reason, never throwing", async () => {
    const send = jest
      .fn()
      .mockResolvedValueOnce(undefined)
      .mockRejectedValueOnce(new ApiError("A visit can have at most 10 files.", 409))
      .mockRejectedValueOnce(new NoConnectionError());

    const result = await uploadPhotos([photo("a.jpg"), photo("b.jpg"), photo("c.jpg")], send);

    expect(send).toHaveBeenCalledTimes(3);
    expect(send).toHaveBeenNthCalledWith(1, { uri: "file:///cache/a.jpg", name: "a.jpg", type: "image/jpeg" });
    expect(result.failed).toEqual([
      { name: "b.jpg", reason: "A visit can have at most 10 files." },
      { name: "c.jpg", reason: "No connection. Nothing was sent." },
    ]);
  });

  it("makes no request for no photos", async () => {
    const send = jest.fn();

    expect(await uploadPhotos([], send)).toEqual({ failed: [] });
    expect(send).not.toHaveBeenCalled();
  });
});
