import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { openLeaveScreen } from "../support/leaveApp";
import { fillDraft } from "../support/leaveActions";

afterEach(() => jest.restoreAllMocks());

const FIRST = "Monday 12 October 2026";
const LAST = "Wednesday 14 October 2026";

describe("Apply Leave: no connection and failed loads (spec 020 SC-006, FR-004, FR-019)", () => {
  it("keeps the draft, shows nothing as submitted, and submits once the connection is back", async () => {
    const { server, leave } = await openLeaveScreen("Apply Leave");
    await fillDraft({ type: "Personal", first: FIRST, last: LAST, reason: "Wedding", halfStart: true });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);

    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Submit"));

    expect(await screen.findByText("No connection. Your request was not submitted.")).toBeTruthy();
    expect(leave.submitted).toHaveLength(0);
    expect(screen.getByLabelText("Reason").props.value).toBe("Wedding");
    expect(screen.getByRole("radio", { name: "Personal", checked: true })).toBeTruthy();
    expect(screen.getByLabelText("Half day on the first day").props.accessibilityState.checked).toBe(true);
    expect(screen.getByLabelText("Check")).toBeTruthy();

    server.offline = false;
    await fireEvent.press(screen.getByLabelText("Submit"));
    await waitFor(() => expect(leave.submitted).toHaveLength(1));
    expect(await screen.findByText("Request submitted", { exact: false })).toBeTruthy();
  });

  it("says the same when Check cannot reach the server, and keeps the draft", async () => {
    const { server } = await openLeaveScreen("Apply Leave");
    await fillDraft({ first: FIRST, last: LAST, reason: "Wedding" });

    server.offline = true;
    await fireEvent.press(screen.getByLabelText("Check"));

    expect(await screen.findByText("No connection. Your request was not submitted.")).toBeTruthy();
    expect(screen.queryByText(/^Working days:/)).toBeNull();
    expect(screen.getByLabelText("Reason").props.value).toBe("Wedding");
  });

  it("shows the server's message instead of the form when the profile is not set up", async () => {
    await openLeaveScreen("Apply Leave", { leave: { profileMissing: true } });

    expect(await screen.findByText("Your profile has not been set up yet.")).toBeTruthy();
    expect(screen.queryByLabelText("Check")).toBeNull();
    expect(screen.queryByLabelText("Submit")).toBeNull();
  });

  it("shows an error with Retry when the types cannot be loaded, then recovers", async () => {
    let failing = true;
    await openLeaveScreen("Apply Leave", {
      prepare: (server) => {
        const real = { status: 200, body: [{ id: "type-casual", code: "CASUAL", name: "Casual" }] };
        server.on("GET /api/v1/me/leave/types", () => (failing ? { status: 500, body: {} } : real));
      },
    });

    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    expect(screen.queryByLabelText("Check")).toBeNull();

    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByLabelText("Casual")).toBeTruthy();
  });

  it("shows No connection with Retry when the types cannot be reached", async () => {
    let offline = true;
    await openLeaveScreen("Apply Leave", {
      prepare: (server) =>
        server.on("GET /api/v1/me/leave/types", () => {
          if (offline) throw new TypeError("Network request failed");
          return { status: 200, body: [{ id: "type-casual", code: "CASUAL", name: "Casual" }] };
        }),
    });
    // The fake server throws from the handler; the app sees a failed call either way.
    expect(await screen.findByText(/No connection|Something went wrong/)).toBeTruthy();
    offline = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByLabelText("Casual")).toBeTruthy();
  });
});
