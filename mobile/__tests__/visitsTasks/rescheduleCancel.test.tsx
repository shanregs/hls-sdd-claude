import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { visit, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T035 (US4) and the button rules of T015: moving and cancelling a planned visit. */
const MENU = "Marketing Calendar";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

async function open(v = visit({ version: 4 }), setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
  const app = await openVisitTaskScreen(MENU, { marketing: { visits: [v] }, ...setup });
  await press(`Visit with Green Valley Public School, ${v.date.split("-").reverse().join("/")}, ${v.effectiveStatus.toLowerCase()}`);
  await screen.findByText("History").catch(() => undefined);
  await screen.findByText("Attending: Manoj Manager");
  return app;
}

describe("which buttons a visit offers", () => {
  it("offers Complete visit, Reschedule and Cancel visit on a planned visit with the Edit grant", async () => {
    await open();

    for (const label of ["Complete visit", "Reschedule", "Cancel visit"]) expect(screen.getByText(label)).toBeTruthy();
  });

  it("offers the three on a Missed visit, because the server still stores it as planned", async () => {
    await open(visit({ date: "2026-10-01", effectiveStatus: "MISSED" }));

    for (const label of ["Complete visit", "Reschedule", "Cancel visit"]) expect(screen.getByText(label)).toBeTruthy();
  });

  it.each([
    ["completed", { status: "COMPLETED", effectiveStatus: "COMPLETED", outcome: "Met" } as const],
    ["cancelled", { status: "CANCELLED", effectiveStatus: "CANCELLED", cancelReason: "Away" } as const],
  ])("offers none of the three on a %s visit, whatever its date", async (_name, patch) => {
    await open(visit({ date: "2026-10-30", ...patch }));

    for (const label of ["Complete visit", "Reschedule", "Cancel visit"]) expect(screen.queryByText(label)).toBeNull();
  });

  it("offers none of the three without the Edit grant, but still Add photo with Create", async () => {
    await open(visit(), { model: visitTaskModel({ visits: { edit: false } }) });

    for (const label of ["Complete visit", "Reschedule", "Cancel visit"]) expect(screen.queryByText(label)).toBeNull();
    expect(screen.getByText("Add photo")).toBeTruthy();
  });

  it("offers no Add photo without the Create grant", async () => {
    await open(visit(), { model: visitTaskModel({ visits: { create: false } }) });

    expect(screen.queryByText("Add photo")).toBeNull();
    expect(screen.getByText("Complete visit")).toBeTruthy();
  });
});

describe("rescheduling", () => {
  it("keeps Send disabled until a date is chosen, then posts the date and the version and shows where it was moved from", async () => {
    const { marketing } = await open();
    await pressText("Reschedule");
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);

    await chooseDate("New date" as never, "not chosen", "Wednesday 7 October 2026");
    await press("Send");

    expect(await screen.findByText("Visit, 07/10/2026")).toBeTruthy();
    expect(screen.getByText("Moved from 06/10/2026 to 07/10/2026")).toBeTruthy();
    expect(marketing.reschedules).toEqual([{ id: "v-1", body: { date: "2026-10-07", version: 4 } }]);
  });

  it("shows the server's reason for the date it is already on, and keeps the form", async () => {
    await open();
    await pressText("Reschedule");
    await chooseDate("New date" as never, "not chosen", "Tuesday 6 October 2026");

    await press("Send");

    expect(await screen.findByText("The activity is already on this date.")).toBeTruthy();
    expect(screen.getByLabelText("New date, Tuesday 6 October 2026")).toBeTruthy();
  });

  it("shows the server's reason for a stale version, reloads the visit and keeps the chosen date", async () => {
    const { marketing } = await open();
    await pressText("Reschedule");
    await chooseDate("New date" as never, "not chosen", "Wednesday 7 October 2026");
    marketing.visits = [visit({ version: 9, notes: "Changed on the web" })];

    await press("Send");

    expect(await screen.findByText("The visit was changed meanwhile.")).toBeTruthy();
    expect(screen.getByLabelText("New date, Wednesday 7 October 2026")).toBeTruthy();
    await press("Send");
    expect(await screen.findByText("Visit, 07/10/2026")).toBeTruthy();
    expect(marketing.reschedules.map((r) => (r.body as { version: number }).version)).toEqual([4, 9]);
  });

  it("says nothing was sent when offline", async () => {
    const { server } = await open();
    await pressText("Reschedule");
    await chooseDate("New date" as never, "not chosen", "Wednesday 7 October 2026");
    server.offline = true;

    await press("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.getByLabelText("New date, Wednesday 7 October 2026")).toBeTruthy();
  });
});

describe("cancelling", () => {
  it("needs a reason of at most 300 characters, then posts it with the version and shows Cancelled with the reason", async () => {
    const { marketing } = await open();
    await pressText("Cancel visit");
    expect(await screen.findByText("Cancel this visit?")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.maxLength).toBe(300);
    await fireEvent.changeText(screen.getByLabelText("Reason"), "   ");
    const buttons = screen.getAllByText("Cancel visit");
    await fireEvent.press(buttons[buttons.length - 1]);
    expect(marketing.cancels).toHaveLength(0);

    await fireEvent.changeText(screen.getByLabelText("Reason"), "Principal is away");
    const confirm = screen.getAllByText("Cancel visit");
    await fireEvent.press(confirm[confirm.length - 1]);

    expect(await screen.findByText("Cancelled because: Principal is away")).toBeTruthy();
    expect(marketing.cancels).toEqual([{ id: "v-1", body: { reason: "Principal is away", version: 4 } }]);
    expect(screen.queryByText("Complete visit")).toBeNull();
  });

  it("keeps the visit when the Manager keeps it", async () => {
    const { marketing } = await open();
    await pressText("Cancel visit");

    await pressText("Keep visit");

    expect(marketing.cancels).toHaveLength(0);
    expect(screen.queryByText("Cancel this visit?")).toBeNull();
    expect(screen.getByText("Complete visit")).toBeTruthy();
  });

  it("shows the server's reason for a stale version and keeps the typed reason", async () => {
    const { marketing } = await open();
    await pressText("Cancel visit");
    await fireEvent.changeText(await screen.findByLabelText("Reason"), "Principal is away");
    marketing.visits = [visit({ version: 9 })];

    const buttons = screen.getAllByText("Cancel visit");
    await fireEvent.press(buttons[buttons.length - 1]);

    expect(await screen.findByText("The visit was changed meanwhile.")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.value).toBe("Principal is away");
    expect(screen.queryByText(/Cancelled because/)).toBeNull();
  });

  it("says nothing was sent when offline", async () => {
    const { server, marketing } = await open();
    await pressText("Cancel visit");
    await fireEvent.changeText(await screen.findByLabelText("Reason"), "Principal is away");
    server.offline = true;

    const buttons = screen.getAllByText("Cancel visit");
    await fireEvent.press(buttons[buttons.length - 1]);

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(marketing.cancels).toHaveLength(0);
  });
});

