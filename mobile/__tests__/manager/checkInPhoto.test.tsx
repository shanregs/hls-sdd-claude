import { fireEvent, screen } from "@testing-library/react-native";
import * as ImagePicker from "expo-image-picker";
import { managerDay, managerFieldModel, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";

/** Spec 033 T016 (US1): the optional photo, taken or chosen, uploaded after the check-in. */
const picker = jest.requireMock("expo-image-picker").__state as Record<string, unknown>;

beforeEach(() => jest.clearAllMocks());
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

describe("choosing a photo", () => {
  it("shows a taken photo with its name, type and size, and removes it", async () => {
    await openManagerScreen("Check-in");

    await pressText("Take a photo");

    expect(await screen.findByText("camera.jpg, JPG, 340 KB")).toBeTruthy();
    expect(screen.getByLabelText("Photo for this check-in")).toBeTruthy();
    await press("Remove photo");
    expect(screen.queryByText("camera.jpg, JPG, 340 KB")).toBeNull();
    expect(screen.getByText("Take a photo")).toBeTruthy();
  });

  it("accepts a picture chosen from the gallery", async () => {
    await openManagerScreen("Check-in");

    await pressText("Choose from gallery");

    expect(await screen.findByText("gallery.png, PNG, 1.2 MB")).toBeTruthy();
  });

  it("asks for the camera permission only when Take a photo is chosen, and a refusal does not stop the check-in", async () => {
    const { manager } = await openManagerScreen("Check-in");
    expect(ImagePicker.requestCameraPermissionsAsync).not.toHaveBeenCalled();
    picker.cameraPermission = "denied";

    await pressText("Take a photo");

    expect(await screen.findByText(/The camera is not allowed/)).toBeTruthy();
    expect(ImagePicker.launchCameraAsync).not.toHaveBeenCalled();
    await press("Check in");
    expect(await screen.findByText("Checked in at 09:42, Office")).toBeTruthy();
    expect(manager.photoUploads).toHaveLength(0);
  });

  it("keeps the form as it was when the picker is cancelled", async () => {
    await openManagerScreen("Check-in");
    picker.camera = { canceled: true };

    await pressText("Take a photo");

    expect(screen.queryByText(/camera\.jpg/)).toBeNull();
    expect(screen.getByText("Take a photo")).toBeTruthy();
  });
});

describe("the photo with the check-in", () => {
  it("sends the check-in first, then uploads the photo to the day's date as a file part, and shows the file's details", async () => {
    const { manager, server } = await openManagerScreen("Check-in");

    await pressText("Take a photo");
    await press("Check in");

    expect(await screen.findByText("camera.jpg, JPG, 12 B")).toBeTruthy();
    expect(manager.photoUploads).toHaveLength(1);
    expect(manager.photoUploads[0]).toMatchObject({
      date: "2026-10-05",
      part: { fieldName: "file", name: "camera.jpg", type: "image/jpeg" },
    });
    const order = server.calls
      .filter((c) => c.method === "POST" && /check-in|photo/.test(c.path))
      .map((c) => (c.path.endsWith("check-in") ? "check-in" : "photo"));
    expect(order).toEqual(["check-in", "photo"]);
    // only what the server returns with the photo is shown: no "who attached it" and no "when"
    expect(screen.queryByText(/attached by/i)).toBeNull();
    expect(screen.queryByText(/taken at/i)).toBeNull();
  });

  it("makes no photo call for a check-in without one", async () => {
    const { manager, server } = await openManagerScreen("Check-in");

    await press("Check in");
    await screen.findByText("Checked in at 09:42, Office");

    expect(manager.photoUploads).toHaveLength(0);
    expect(server.callsTo("POST /api/v1/manager-attendance/me/days/{date}/photo")).toHaveLength(0);
  });

  it("leaves the check-in recorded when the server refuses the photo, explains why, and keeps Add photo", async () => {
    const { manager } = await openManagerScreen("Check-in", {
      manager: { photoRefusal: () => ({ status: 400, body: { reason: "A photo must be a jpg or png file." } }) },
    });

    await pressText("Choose from gallery");
    await press("Check in");

    expect(await screen.findByText("Checked in at 09:42, Office")).toBeTruthy();
    expect(await screen.findByText("A photo must be a jpg or png file.")).toBeTruthy();
    expect(manager.today.day?.checkInAt).toBeTruthy();
    expect(screen.getByText("Add photo")).toBeTruthy();
  });

  it("adds a photo later from today's status while the day has none, and then shows it", async () => {
    const { manager } = await openManagerScreen("Check-in", {
      manager: { today: today({ day: managerDay({ hasPhoto: false }) }) },
    });

    await pressText("Add photo");
    await pressText("Take a photo");
    await pressText("Upload photo");

    expect(await screen.findByText("camera.jpg, JPG, 12 B")).toBeTruthy();
    expect(manager.photoUploads).toHaveLength(1);
    expect(screen.queryByText("Add photo")).toBeNull();
  });

  it("shows the server's reason when a later upload is refused, and keeps the picture to try again", async () => {
    await openManagerScreen("Check-in", {
      manager: {
        today: today({ day: managerDay({ hasPhoto: false }) }),
        photoRefusal: () => ({ status: 409, body: { reason: "This check-in already has a photo." } }),
      },
    });

    await pressText("Add photo");
    await pressText("Take a photo");
    await pressText("Upload photo");

    expect(await screen.findByText("This check-in already has a photo.")).toBeTruthy();
    expect(screen.getByText("camera.jpg, JPG, 340 KB")).toBeTruthy();
  });

  it("does not offer Add photo without the Create action", async () => {
    await openManagerScreen("Check-in", {
      model: managerFieldModel({ create: false }),
      manager: { today: today({ day: managerDay({ hasPhoto: false }) }) },
    });

    await screen.findByText("Checked in at 09:42, Office");
    expect(screen.queryByText("Add photo")).toBeNull();
  });
});
