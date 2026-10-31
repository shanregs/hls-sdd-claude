import { fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import { AppState } from "react-native";
import { PaperProvider } from "react-native-paper";
import { NoConnectionError } from "../../src/api/errors";
import { listProspects } from "../../src/api/marketingApi";
import { logSameDayVisit } from "../../src/api/sameDayApi";
import { cacheProspects, readListCache } from "../../src/outbox/listCache";
import { enqueue } from "../../src/sameDay/sameDayQueue";
import { LogVisitTodayForm, NO_CACHED_SCHOOLS } from "../../src/visits/LogVisitTodayForm";
import { prospect } from "../support/visitTaskFixtures";

/** Spec 040 FR-017: a visit for today can be started with no connection, from the School list saved on the phone. */
jest.mock("../../src/api/marketingApi");
jest.mock("../../src/api/sameDayApi");
jest.mock("../../src/outbox/listCache");
jest.mock("../../src/sameDay/sameDayQueue", () => ({
  ...jest.requireActual("../../src/sameDay/sameDayQueue"),
  enqueue: jest.fn(),
  flushQueue: jest.fn(),
}));
jest.mock("../../src/sameDay/sameDayStorage", () => ({
  encryptedFileStorage: () => ({
    read: async () => [],
    write: async () => undefined,
  }),
}));

const SAVED = [
  prospect({ id: "p-1", name: "Green Valley Public School" }),
  prospect({ id: "p-2", name: "Lakeside Academy" }),
];

async function open() {
  const onDone = jest.fn();
  await render(
    <PaperProvider>
      <LogVisitTodayForm myUserId="u-1" onDone={onDone} onCancel={jest.fn()} />
    </PaperProvider>,
  );
  return onDone;
}

describe("logging a visit for today with no connection", () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.spyOn(AppState, "addEventListener").mockReturnValue({ remove: jest.fn() });
    (enqueue as jest.Mock).mockResolvedValue(undefined);
    (cacheProspects as jest.Mock).mockResolvedValue(undefined);
    const { flushQueue } = jest.requireMock("../../src/sameDay/sameDayQueue");
    (flushQueue as jest.Mock).mockResolvedValue({
      sent: 0,
      refused: 0,
      waiting: 0,
    });
  });

  it("offers the saved Schools, filters them by the search, and queues the visit flagged for today", async () => {
    (listProspects as jest.Mock).mockRejectedValue(new NoConnectionError());
    (logSameDayVisit as jest.Mock).mockRejectedValue(new NoConnectionError());
    (readListCache as jest.Mock).mockResolvedValue({ prospects: SAVED });
    const onDone = await open();

    expect(await screen.findByLabelText("Choose Green Valley Public School")).toBeTruthy();
    expect(screen.getByLabelText("Choose Lakeside Academy")).toBeTruthy();
    expect(screen.getByText(/Showing the Schools saved on this phone/)).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Search a School or prospect"), "lake");
    await waitFor(() => expect(screen.queryByLabelText("Choose Green Valley Public School")).toBeNull());

    await fireEvent.press(screen.getByLabelText("Choose Lakeside Academy"));
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "Met the principal");
    await fireEvent.press(screen.getByLabelText("Log visit"));

    await waitFor(() => expect(onDone).toHaveBeenCalledWith("QUEUED"));
    expect(enqueue).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        kind: "VISIT",
        capturedOn: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/),
        body: expect.objectContaining({
          prospectId: "p-2",
          outcome: "Met the principal",
        }),
      }),
    );
  });

  it("says clearly that no School list is saved yet", async () => {
    (listProspects as jest.Mock).mockRejectedValue(new NoConnectionError());
    (readListCache as jest.Mock).mockResolvedValue(null);
    await open();

    expect(await screen.findByText(NO_CACHED_SCHOOLS)).toBeTruthy();
    expect(screen.getByLabelText("Log visit").props.accessibilityState?.disabled).toBe(true);
  });

  it("saves the list when it loads online, so it is there next time", async () => {
    (listProspects as jest.Mock).mockResolvedValue({
      content: SAVED,
      page: 0,
      size: 25,
      totalElements: 2,
    });
    await open();

    await screen.findByLabelText("Choose Lakeside Academy");
    expect(cacheProspects).toHaveBeenCalledWith("u-1", SAVED);
  });
});
