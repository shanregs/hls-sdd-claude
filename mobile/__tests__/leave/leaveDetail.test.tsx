import { fireEvent, screen } from "@testing-library/react-native";
import { MANAGER_MODEL } from "../support/fixtures";
import { openLeaveScreen } from "../support/leaveApp";
import { leaveRequest, previewDays } from "../support/leaveFixtures";

afterEach(() => jest.restoreAllMocks());

async function openDetail(over: Parameters<typeof leaveRequest>[0], extra: Record<string, unknown> = {}, setup = {}) {
  const request = leaveRequest({ as: "supervisor", teacherName: "Asha Rao", id: "r1", ...over });
  const opened = await openLeaveScreen("Leave Management", {
    model: MANAGER_MODEL,
    leave: {
      requests: [request],
      detailOf: () => ({ days: previewDays(["2026-10-12", "2026-10-13", "2026-10-14"], ["2026-10-14"]), problems: [] }),
      ...extra,
    },
    ...setup,
  });
  // The list opens on Pending; other statuses are reached through the filter.
  if (request.status !== "PENDING") await fireEvent.press(await screen.findByLabelText(request.status[0] + request.status.slice(1).toLowerCase()));
  await fireEvent.press(await screen.findByLabelText(/^Asha Rao, Demo School One/));
  await screen.findByText("Days it would mark");
  return opened;
}

const offered = () => ["Approve", "Reject", "Revoke"].filter((label) => screen.queryByLabelText(label) !== null);

describe("Leave request detail (spec 020 FR-008, FR-009, SC-007)", () => {
  it("shows the request and the days approving it would mark, with whole and half days", async () => {
    await openDetail({ halfDayEnd: true, workingDays: 2.5, reason: "Pongal travel" });

    expect(screen.getByText("Asha Rao")).toBeTruthy();
    expect(screen.getByText("12/10/2026 – 14/10/2026")).toBeTruthy();
    expect(screen.getByText("Reason: Pongal travel")).toBeTruthy();
    expect(screen.getByLabelText("Monday 12 October 2026, whole day")).toBeTruthy();
    expect(screen.getByLabelText("Wednesday 14 October 2026, half day")).toBeTruthy();
  });

  it("shows the server's problems in plain wording", async () => {
    await openDetail(
      {},
      { detailOf: () => ({ days: previewDays(["2026-10-12"]), problems: ["days set by a supervisor: 2026-10-13", "month locked: 2026-10"] }) },
    );

    expect(screen.getByText("Some of these days were marked by a supervisor (13/10/2026). Correct them first, then approve.")).toBeTruthy();
    expect(screen.getByText("Attendance for October 2026 is locked.")).toBeTruthy();
  });

  it("offers Approve and Reject for a Pending request the server lets the user decide", async () => {
    await openDetail({});
    expect(offered()).toEqual(["Approve", "Reject"]);
  });

  it("offers Revoke for an Approved request when the server lists it", async () => {
    await openDetail({ status: "APPROVED", decidedByName: "Manoj" });
    expect(offered()).toEqual(["Revoke"]);
  });

  it.each(["REJECTED", "CANCELLED"] as const)("offers nothing for a %s request", async (status) => {
    await openDetail({ status });
    expect(offered()).toEqual([]);
  });

  it("offers exactly what allowedActions lists, never what the status or dates suggest", async () => {
    // A Pending request in the future for which the server offers nothing (for example out of this user's rights).
    await openDetail({ allowedActions: [], firstDate: "2027-01-10", lastDate: "2027-01-12" });
    expect(offered()).toEqual([]);
  });

  it("is not affected by the phone's clock", async () => {
    await openDetail({}, {}, { phoneOffsetHours: 24 * 400 });
    expect(offered()).toEqual(["Approve", "Reject"]);
  });

  it("shows an error with Retry when the detail cannot be loaded, then recovers", async () => {
    let failing = true;
    await openLeaveScreen("Leave Management", {
      model: MANAGER_MODEL,
      leave: { requests: [leaveRequest({ as: "supervisor", id: "r1", teacherName: "Asha Rao" })] },
      prepare: (server) => {
        const real = {
          status: 200,
          body: { request: leaveRequest({ as: "supervisor", id: "r1", teacherName: "Asha Rao" }), days: [], problems: [] },
        };
        server.on("GET /api/v1/leave/{id}", () => (failing ? { status: 500, body: {} } : real));
      },
    });
    await fireEvent.press(await screen.findByLabelText(/^Asha Rao, Demo School One/));

    expect(await screen.findByText("Something went wrong")).toBeTruthy();
    failing = false;
    await fireEvent.press(screen.getByLabelText("Retry"));
    expect(await screen.findByText("Days it would mark")).toBeTruthy();
  });
});
