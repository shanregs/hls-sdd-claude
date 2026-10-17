import { fireEvent, screen } from "@testing-library/react-native";
import { captureHardwareBack } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { visit } from "../support/visitTaskFixtures";

/** Spec 034 T015 (US1): one visit as the server holds it. */
const MENU = "Marketing Calendar";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const GATE = { fileId: "f-gate", name: "gate.jpg", sizeBytes: 2048, contentType: "image/jpeg" };
const BROCHURE = { fileId: "f-brochure", name: "brochure.pdf", sizeBytes: 1_048_576, contentType: "application/pdf" };

describe("the visit's details", () => {
  it("shows a planned visit's target, date, kind, colleagues and notes", async () => {
    await openVisitTaskScreen(MENU, {
      marketing: {
        visits: [
          visit({
            type: "PROPOSAL_MEETING",
            notes: "Bring the proposal",
            attendees: [
              { userId: "u-1", name: "Manoj Manager" },
              { userId: "u-2", name: "Divya Director" },
            ],
          }),
        ],
      },
    });

    await press("Proposal meeting with Green Valley Public School, 06/10/2026, planned");

    expect(await screen.findByText("Green Valley Public School")).toBeTruthy();
    expect(screen.getByText("Proposal meeting, 06/10/2026")).toBeTruthy();
    expect(screen.getByText("Planned")).toBeTruthy();
    expect(screen.getByText("Attending: Manoj Manager, Divya Director")).toBeTruthy();
    expect(screen.getByText("Notes: Bring the proposal")).toBeTruthy();
  });

  it("shows a completed visit's outcome and follow-up date, and says when the follow-up date has passed", async () => {
    await openVisitTaskScreen(MENU, {
      marketing: {
        visits: [
          visit({
            id: "a",
            date: "2026-10-02",
            status: "COMPLETED",
            effectiveStatus: "COMPLETED",
            outcome: "Met the Principal; agreed to a trial",
            followUpOn: "2026-10-04",
            followUpOverdue: true,
          }),
          visit({
            id: "b",
            date: "2026-10-03",
            status: "COMPLETED",
            effectiveStatus: "COMPLETED",
            outcome: "Short meeting",
            followUpOn: "2026-10-20",
            prospect: { id: "p-2", name: "Second School" },
          }),
        ],
      },
    });

    await press("Visit with Green Valley Public School, 02/10/2026, completed");
    expect(await screen.findByText("Met the Principal; agreed to a trial")).toBeTruthy();
    expect(screen.getByText("Follow-up on 04/10/2026. The follow-up date has passed.")).toBeTruthy();
    await press("Back to the visits");

    await press("Visit with Second School, 03/10/2026, completed");
    expect(await screen.findByText("Follow-up on 20/10/2026")).toBeTruthy();
    expect(screen.queryByText(/has passed/)).toBeNull();
  });

  it("shows a cancelled visit's reason and every earlier date it was moved from", async () => {
    await openVisitTaskScreen(MENU, {
      marketing: {
        visits: [
          visit({
            date: "2026-10-03",
            status: "CANCELLED",
            effectiveStatus: "CANCELLED",
            cancelReason: "Principal is away",
            rescheduled: true,
            dateHistory: [
              { from: "2026-10-01", to: "2026-10-02" },
              { from: "2026-10-02", to: "2026-10-03" },
            ],
          }),
        ],
      },
    });

    await press("Visit with Green Valley Public School, 03/10/2026, cancelled, moved");

    expect(await screen.findByText("Cancelled because: Principal is away")).toBeTruthy();
    expect(screen.getByText("Moved from 01/10/2026 to 02/10/2026")).toBeTruthy();
    expect(screen.getByText("Moved from 02/10/2026 to 03/10/2026")).toBeTruthy();
  });

  it("lists the attachments with name, type and size, a thumbnail for an image only", async () => {
    const { marketing } = await openVisitTaskScreen(MENU, { marketing: { visits: [visit({ attachments: [GATE, BROCHURE] })] } });
    marketing.files["f-gate"] = { bytes: new Uint8Array([1, 2, 3]), contentType: "image/jpeg", name: "gate.jpg" };

    await press("Visit with Green Valley Public School, 06/10/2026, planned");

    expect(await screen.findByLabelText("Photo gate.jpg")).toBeTruthy();
    expect(screen.getByText("gate.jpg, JPG, 2 KB")).toBeTruthy();
    expect(screen.getByText("brochure.pdf, PDF, 1.0 MB")).toBeTruthy();
    expect(screen.getByText("Open it on the web.")).toBeTruthy();
  });
});

describe("leaving the detail", () => {
  it("closes the detail with the hardware Back button instead of leaving the screen", async () => {
    const back = captureHardwareBack();
    await openVisitTaskScreen(MENU, { marketing: { visits: [visit()] } });
    await press("Visit with Green Valley Public School, 06/10/2026, planned");
    await screen.findByText("Attending: Manoj Manager");

    expect(await back()).toBe(true);

    expect(await screen.findByText("Later")).toBeTruthy();
    expect(screen.queryByText("Attending: Manoj Manager")).toBeNull();
  });

  it("reads the visit again when it is opened, so its version is the server's current one", async () => {
    const { server, marketing } = await openVisitTaskScreen(MENU, { marketing: { visits: [visit({ version: 1 })] } });
    await screen.findByText("Green Valley Public School");
    marketing.visits = [visit({ version: 5, notes: "Changed on the web" })];

    await press("Visit with Green Valley Public School, 06/10/2026, planned");

    expect(await screen.findByText("Notes: Changed on the web")).toBeTruthy();
    expect(server.callsTo("GET /api/v1/marketing/activities/{id}")).toHaveLength(1);
  });

  it("says a visit that the server no longer finds is not found, and returns to the list", async () => {
    const { marketing } = await openVisitTaskScreen(MENU, { marketing: { visits: [visit()] } });
    await screen.findByText("Green Valley Public School");
    marketing.visits = [];

    await press("Visit with Green Valley Public School, 06/10/2026, planned");

    expect(await screen.findByText("Visit not found")).toBeTruthy();
    expect(screen.getByText("Activity not found.")).toBeTruthy();
    await press("Back to the visits");
    expect(await screen.findByText("No visits this month. Plan a visit to add one.")).toBeTruthy();
  });
});
