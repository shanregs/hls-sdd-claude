import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveTypes, preview, previewDays } from "../support/leaveFixtures";
import { chooseDate, fillDraft } from "../support/leaveActions";

afterEach(() => jest.restoreAllMocks());

const FIRST = "Monday 12 October 2026";
const LAST = "Wednesday 14 October 2026";

describe("Apply Leave (spec 020 US1)", () => {
  it("lists exactly the server's leave types in server order, none built in", async () => {
    await openLeaveScreen("Apply Leave", {
      leave: { types: [{ id: "x1", code: "ZETA", name: "Zeta leave" }, { id: "x2", code: "ALPHA", name: "Alpha leave" }] },
    });

    await screen.findByLabelText("Zeta leave");
    expect(
      screen.getAllByLabelText(/^(Zeta leave|Alpha leave|Casual|Sick|Personal|Other)$/).map((n) => n.props.accessibilityLabel),
    ).toEqual(["Zeta leave", "Alpha leave"]);
  });

  it("shows the types, two dates, two half-day boxes and a reason field with a counter", async () => {
    await openLeaveScreen("Apply Leave");

    expect(await screen.findByLabelText("Casual")).toBeTruthy();
    for (const type of leaveTypes()) expect(screen.getByLabelText(type.name)).toBeTruthy();
    expect(screen.getByLabelText("First date, not chosen")).toBeTruthy();
    expect(screen.getByLabelText("Last date, not chosen")).toBeTruthy();
    expect(screen.getByLabelText("Half day on the first day")).toBeTruthy();
    expect(screen.getByLabelText("Half day on the last day")).toBeTruthy();
    expect(screen.getByLabelText("Reason")).toBeTruthy();
    expect(screen.getByText("500 characters left")).toBeTruthy();
  });

  it("checks the draft: posts it, then shows the server's total, counted days and the other dates", async () => {
    const { server } = await openLeaveScreen("Apply Leave", {
      leave: {
        // Counted by the server: Saturday 3 and Monday 5 (half day). 1 Oct is before the placement.
        previewReply: () => preview({ days: previewDays(["2026-10-03", "2026-10-05"], ["2026-10-05"]) }),
      },
    });
    await fillDraft({
      first: "Thursday 1 October 2026",
      last: "Monday 5 October 2026",
      reason: "Pongal travel",
      halfEnd: true,
    });

    await fireEvent.press(screen.getByLabelText("Check"));

    expect(await screen.findByText("Working days: 1.5")).toBeTruthy();
    expect(screen.getByLabelText("Thursday 1 October 2026, not counted")).toBeTruthy();
    expect(screen.getByLabelText("Friday 2 October 2026, holiday, not counted")).toBeTruthy();
    expect(screen.getByLabelText("Saturday 3 October 2026, counted, whole day")).toBeTruthy();
    expect(screen.getByLabelText("Sunday 4 October 2026, weekly off, not counted")).toBeTruthy();
    expect(screen.getByLabelText("Monday 5 October 2026, counted, half day")).toBeTruthy();

    const calls = server.callsTo("POST /api/v1/me/leave/preview");
    expect(calls).toHaveLength(1);
    expect(calls[0].body).toEqual({
      leaveTypeId: "type-casual",
      firstDate: "2026-10-01",
      lastDate: "2026-10-05",
      halfDayStart: false,
      halfDayEnd: true,
      reason: "Pongal travel",
    });
    // The calendar was fetched once, only to label the other dates.
    expect(server.callsTo("GET /api/v1/attendance/calendar")).toHaveLength(1);
    // Nothing was created.
    expect(server.callsTo("POST /api/v1/me/leave")).toHaveLength(0);
  });

  it("clears the preview when any field changes, and Submit needs a preview of the current draft", async () => {
    await openLeaveScreen("Apply Leave");
    await fillDraft({ first: FIRST, last: LAST, reason: "Family function" });
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);

    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    await waitFor(() => expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(false));

    await fireEvent.press(screen.getByLabelText("Half day on the first day"));
    expect(screen.queryByText(/^Working days:/)).toBeNull();
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);

    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "Changed my mind");
    expect(screen.queryByText(/^Working days:/)).toBeNull();
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);
  });

  it("keeps Submit disabled while the preview lists a problem, and shows the problem", async () => {
    await openLeaveScreen("Apply Leave", {
      leave: {
        previewReply: () =>
          preview({ days: [], workingDays: 0, problems: ["None of these dates is a working day for you."] }),
      },
    });
    await fillDraft({ first: FIRST, last: LAST, reason: "Family function" });
    await fireEvent.press(screen.getByLabelText("Check"));

    expect(await screen.findByText("None of these dates is a working day for you.")).toBeTruthy();
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);
  });

  it("blocks only a missing type, date or reason and a last date before the first date", async () => {
    await openLeaveScreen("Apply Leave");
    await screen.findByLabelText("Casual");

    // Nothing chosen yet: Check and Submit are off.
    expect(screen.getByLabelText("Check").props.accessibilityState.disabled).toBe(true);
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);

    await fillDraft({ first: FIRST, last: LAST });
    expect(screen.getByLabelText("Check").props.accessibilityState.disabled).toBe(false);
    // A reason is still missing, so Submit cannot be reached even after a preview.
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);

    // A last date before the first date.
    await chooseDate("Last date", `Wednesday 14 October 2026`, "Monday 5 October 2026");
    expect(await screen.findByText("The last date cannot be before the first date.")).toBeTruthy();
    expect(screen.getByLabelText("Check").props.accessibilityState.disabled).toBe(true);
  });

  it("submits the draft, clears the form and opens My Leave History", async () => {
    const { server } = await openLeaveScreen("Apply Leave");
    await fillDraft({ type: "Sick", first: FIRST, last: LAST, reason: "  Family function ", halfEnd: true });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);

    await fireEvent.press(screen.getByLabelText("Submit"));

    await waitFor(() => expect(server.callsTo("POST /api/v1/me/leave")).toHaveLength(1));
    expect(server.callsTo("POST /api/v1/me/leave")[0].body).toEqual({
      leaveTypeId: "type-sick",
      firstDate: "2026-10-12",
      lastDate: "2026-10-14",
      halfDayStart: false,
      halfDayEnd: true,
      reason: "Family function",
    });
    // My Leave History is now the open screen; the form is gone.
    expect(await screen.findByText("Request submitted", { exact: false })).toBeTruthy();
    expect(screen.queryByLabelText("Check")).toBeNull();
  });
});
