import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { visit } from "../support/visitTaskFixtures";

/** Spec 034 T021 (US2): what the server may refuse, and a photo that fails after the visit was completed. */
const MENU = "Marketing Calendar";
const CARD = "Visit with Green Valley Public School, 06/10/2026, planned";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

async function openForm(setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
  const app = await openVisitTaskScreen(MENU, { marketing: { visits: [visit({ version: 2 })] }, ...setup });
  await press(CARD);
  await pressText("Complete visit");
  await fireEvent.changeText(await screen.findByLabelText("Outcome"), "Met the Principal");
  await fireEvent.changeText(screen.getByLabelText("Notes"), "Bring the proposal");
  return app;
}

describe("refusals of the completion", () => {
  it("shows the server's reason for a follow-up date before the visit and keeps every entry", async () => {
    const { marketing } = await openForm();
    await chooseDate("Follow-up date" as never, "not chosen", "Monday 5 October 2026");

    await press("Send");

    expect(await screen.findByText("The follow-up date cannot be before the visit.")).toBeTruthy();
    expect(marketing.visits[0].status).toBe("PLANNED");
    expect(screen.getByLabelText("Outcome").props.value).toBe("Met the Principal");
    expect(screen.getByLabelText("Notes").props.value).toBe("Bring the proposal");
    expect(screen.getByLabelText("Follow-up date, Monday 5 October 2026")).toBeTruthy();
  });

  it("shows the server's reason when the visit was changed meanwhile, keeps the entries and reloads the visit", async () => {
    const { marketing, server } = await openForm();
    marketing.visits = [visit({ version: 7, notes: "Changed on the web" })];

    await press("Send");

    expect(await screen.findByText("The visit was changed meanwhile.")).toBeTruthy();
    expect(screen.getByLabelText("Outcome").props.value).toBe("Met the Principal");
    // the visit was read again, so the next try carries the new version
    expect(server.callsTo("GET /api/v1/marketing/activities/{id}").length).toBeGreaterThanOrEqual(2);
    await press("Send");
    expect(await screen.findByText("Completed")).toBeTruthy();
    expect(marketing.completions.map((c) => (c.body as { version: number }).version)).toEqual([2, 7]);
  });

  it("shows the server's reason when the visit is no longer planned, and nothing is shown as completed", async () => {
    const { marketing } = await openForm();
    marketing.visits = [visit({ version: 2, status: "CANCELLED", effectiveStatus: "CANCELLED", cancelReason: "Cancelled on the web" })];

    await press("Send");

    expect(await screen.findByText("Only a planned visit can be completed.")).toBeTruthy();
    expect(screen.queryByText("Completed")).toBeNull();
    expect(screen.getByLabelText("Outcome").props.value).toBe("Met the Principal");
  });

  it("shows the server's reason when the visit is no longer there", async () => {
    const { marketing } = await openForm();
    marketing.refusals.complete = { status: 404, body: { reason: "Activity not found." } };

    await press("Send");

    expect(await screen.findByText("Activity not found.")).toBeTruthy();
    expect(screen.getByLabelText("Outcome").props.value).toBe("Met the Principal");
  });
});

describe("a photo that fails after the completion", () => {
  it("leaves the visit Completed, names the photo and the server's reason, and keeps Add photo", async () => {
    const { marketing } = await openForm({
      marketing: { visits: [visit({ version: 2 })], refusals: { attach: { status: 400, body: { reason: "Only jpg, png, pdf, docx, xlsx and txt files can be uploaded." } } } },
    });
    await pressText("Take a photo");

    await press("Send");

    expect(await screen.findByText("Completed")).toBeTruthy();
    expect(screen.getByText("These photos were not added:")).toBeTruthy();
    expect(screen.getByText("camera.jpg: Only jpg, png, pdf, docx, xlsx and txt files can be uploaded.")).toBeTruthy();
    expect(marketing.visits[0].status).toBe("COMPLETED");
    expect(screen.getByText("Add photo")).toBeTruthy();
  });

  it("adds a photo later from the visit while it has fewer than 10 files", async () => {
    const { marketing } = await openVisitTaskScreen(MENU, {
      marketing: { visits: [visit({ status: "COMPLETED", effectiveStatus: "COMPLETED", outcome: "Met" })] },
    });
    await press("Visit with Green Valley Public School, 06/10/2026, completed");

    await pressText("Add photo");
    await pressText("Take a photo");
    await pressText("Upload photos");

    expect(await screen.findByLabelText("Photo camera.jpg")).toBeTruthy();
    expect(marketing.uploads).toHaveLength(1);
  });

  it("does not offer Add photo at 10 files", async () => {
    const ten = Array.from({ length: 10 }, (_, i) => ({ fileId: `f${i}`, name: `p${i}.jpg`, sizeBytes: 100, contentType: "application/pdf" }));
    await openVisitTaskScreen(MENU, { marketing: { visits: [visit({ attachments: ten })] } });
    await press(CARD);

    await screen.findByText("Attending: Manoj Manager");
    expect(screen.queryByText("Add photo")).toBeNull();
  });
});

describe("without a connection", () => {
  it("says nothing was sent, keeps every entry and shows nothing as completed, then completes on the next try", async () => {
    const { server, marketing } = await openForm();
    server.offline = true;

    await press("Send");

    expect(await screen.findByText("No connection. Nothing was sent.")).toBeTruthy();
    expect(screen.queryByText("Completed")).toBeNull();
    expect(screen.getByLabelText("Outcome").props.value).toBe("Met the Principal");
    expect(marketing.completions).toHaveLength(0);

    server.offline = false;
    await press("Send");
    expect(await screen.findByText("Completed")).toBeTruthy();
    expect(marketing.completions).toHaveLength(1);
  });
});
