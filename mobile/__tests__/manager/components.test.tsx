import { fireEvent, render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { setAccessToken } from "../../src/security/memoryToken";
import { ActivityList, activitySummary } from "../../src/manager/ActivityList";
import { PhotoView } from "../../src/manager/PhotoView";
import { newServer } from "../support/fakeServer";
import { plannedActivity } from "../support/managerFixtures";
import { installManager } from "../support/managerServer";

/** Spec 033 T018 and T020: the activity rows and the photo view, on their own. */
const wrap = (node: React.ReactElement) => render(<PaperProvider>{node}</PaperProvider>);

describe("ActivityList", () => {
  const drive = plannedActivity({ id: "a1", kind: "COLLEGE_DRIVE", place: "Anna Nagar College", status: "PLANNED" });
  const task = plannedActivity({ id: "a2", kind: "TASK", place: null, status: "OPEN" });

  it("summarises an activity by its kind, place and status, leaving out what the server did not send", () => {
    expect(activitySummary(drive)).toBe("College drive, Anna Nagar College, planned");
    expect(activitySummary(task)).toBe("Task, open");
  });

  it("says when there are none", async () => {
    await wrap(<ActivityList activities={[]} />);

    expect(screen.getByText("No planned activities for today.")).toBeTruthy();
  });

  it("keeps the server's order and reports a toggle with the tapped activity", async () => {
    const onToggle = jest.fn();
    await wrap(<ActivityList activities={[task, drive]} selected={new Set(["a1"])} onToggle={onToggle} />);

    const labels = screen.getAllByLabelText(/selected/).map((n) => n.props.accessibilityLabel);
    expect(labels).toEqual(["Task, open, not selected", "College drive, Anna Nagar College, planned, selected"]);
    await fireEvent.press(screen.getByLabelText("Task, open, not selected"));
    expect(onToggle).toHaveBeenCalledWith(task);
  });

  it("opens an activity's details from a read-only row", async () => {
    const onOpen = jest.fn();
    await wrap(<ActivityList activities={[drive]} onOpen={onOpen} />);

    await fireEvent.press(screen.getByLabelText("College drive, Anna Nagar College, planned. Show details"));

    expect(onOpen).toHaveBeenCalledWith(drive);
  });
});

describe("PhotoView", () => {
  beforeEach(() => setAccessToken("access-1"));

  it("shows the photo with the name, type and size the server returns, and nothing about who or when", async () => {
    const server = newServer();
    installManager(server, { photo: { bytes: new Uint8Array(2048), contentType: "image/png", fileName: "gate.png" } });

    await wrap(<PhotoView date="2026-10-05" hasPhoto canAdd onChanged={jest.fn()} />);

    expect(await screen.findByText("gate.png, PNG, 2 KB")).toBeTruthy();
    expect(screen.getByLabelText("Your photo for this check-in")).toBeTruthy();
    expect(screen.queryByText(/attached by/i)).toBeNull();
  });

  it("shows a short message with Try again when the photo cannot be loaded, and loads it on the retry", async () => {
    const server = newServer();
    const fake = installManager(server);
    fake.photo = null;

    await wrap(<PhotoView date="2026-10-05" hasPhoto canAdd={false} onChanged={jest.fn()} />);
    expect(await screen.findByText("The photo could not be loaded.")).toBeTruthy();

    fake.photo = { bytes: new Uint8Array(512), contentType: "image/jpeg", fileName: "again.jpg" };
    await fireEvent.press(screen.getByText("Try again"));

    expect(await screen.findByText("again.jpg, JPG, 512 B")).toBeTruthy();
  });

  it("says there is no connection, not that the photo is missing", async () => {
    const server = newServer();
    installManager(server);
    server.offline = true;

    await wrap(<PhotoView date="2026-10-05" hasPhoto canAdd={false} onChanged={jest.fn()} />);

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
  });

  it("shows nothing for a day without a photo when the Manager cannot add one", async () => {
    newServer();
    await wrap(<PhotoView date="2026-10-05" hasPhoto={false} canAdd={false} onChanged={jest.fn()} />);

    expect(screen.queryByText("Add photo")).toBeNull();
  });
});
