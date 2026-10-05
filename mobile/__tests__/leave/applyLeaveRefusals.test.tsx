import { fireEvent, screen, waitFor } from "@testing-library/react-native";
import { APPLY_REFUSAL_COUNT } from "../../src/leave/leaveMessages";
import { openLeaveScreen } from "../support/leaveApp";
import { preview } from "../support/leaveFixtures";
import { fillDraft } from "../support/leaveActions";
import { APPLY_TEXTS } from "../support/leaveTexts";

afterEach(() => jest.restoreAllMocks());

const FIRST = "Monday 12 October 2026";
const LAST = "Wednesday 14 October 2026";

/** What the Teacher reads: the server's text, except the locked month which is reworded. */
const plain = (reason: string) =>
  reason.includes("month locked: 2026-09") ? "Attendance for September 2026 is locked." : reason;

describe("Apply Leave: refusals (spec 020 FR-003)", () => {
  it(`covers all ${APPLY_REFUSAL_COUNT} refusals the server can give when applying`, () => {
    expect(APPLY_TEXTS).toHaveLength(APPLY_REFUSAL_COUNT);
  });

  it.each(APPLY_TEXTS)("preview: shows %s in plain wording and keeps Submit off", async (reason) => {
    await openLeaveScreen("Apply Leave", {
      leave: { previewReply: () => preview({ days: [], workingDays: 0, problems: [reason] }) },
    });
    await fillDraft({ first: FIRST, last: LAST, reason: "Family function" });

    await fireEvent.press(screen.getByLabelText("Check"));

    expect(await screen.findByText(plain(reason))).toBeTruthy();
    expect(screen.getByLabelText("Submit").props.accessibilityState.disabled).toBe(true);
  });

  it.each(APPLY_TEXTS)("submit: shows %s, creates nothing and keeps the draft", async (reason) => {
    const { leave } = await openLeaveScreen("Apply Leave", {
      leave: { submitRefusal: () => ({ status: 409, body: { reason } }) },
    });
    await fillDraft({ type: "Sick", first: FIRST, last: LAST, reason: "Family function", halfEnd: true });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);

    await fireEvent.press(screen.getByLabelText("Submit"));

    expect(await screen.findByText(plain(reason))).toBeTruthy();
    expect(leave.submitted).toHaveLength(0);
    expect(leave.requests).toHaveLength(0);
    // The draft is still on screen.
    expect(screen.getByLabelText("Reason").props.value).toBe("Family function");
    expect(screen.getByRole("radio", { name: "Sick", checked: true })).toBeTruthy();
    expect(screen.getByLabelText("First date, Monday 12 October 2026")).toBeTruthy();
    expect(screen.getByLabelText("Half day on the last day").props.accessibilityState.checked).toBe(true);
  });

  it("treats a 400 like a 409: the same plain text, nothing created", async () => {
    const { leave } = await openLeaveScreen("Apply Leave", {
      leave: { submitRefusal: () => ({ status: 400, body: { reason: "Give a reason." } }) },
    });
    await fillDraft({ first: FIRST, last: LAST, reason: "x" });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    await fireEvent.press(screen.getByLabelText("Submit"));

    expect(await screen.findByText("Give a reason.")).toBeTruthy();
    expect(leave.submitted).toHaveLength(0);
  });

  it("never uses the phone's clock: a wrong phone date does not stop or allow anything", async () => {
    // The phone is 40 days ahead of the server. The 30-day look-back is the server's rule only.
    const { leave } = await openLeaveScreen("Apply Leave", { phoneOffsetHours: 40 * 24 });
    await fillDraft({ first: FIRST, last: LAST, reason: "Family function" });
    await fireEvent.press(screen.getByLabelText("Check"));
    await screen.findByText(/^Working days:/);
    await fireEvent.press(screen.getByLabelText("Submit"));

    await waitFor(() => expect(leave.submitted).toHaveLength(1));
  });
});
