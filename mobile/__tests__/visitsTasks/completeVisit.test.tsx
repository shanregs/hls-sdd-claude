import { fireEvent, screen } from "@testing-library/react-native";
import { chooseDate } from "../support/leaveActions";
import { openVisitTaskScreen } from "../support/visitTaskApp";
import { visit, visitTaskModel } from "../support/visitTaskFixtures";

/** Spec 034 T020 (US2): logging a visit with an outcome, then photos. */
const MENU = "Marketing Calendar";
const CARD = "Visit with Green Valley Public School, 06/10/2026, planned";
const press = async (label: string) => fireEvent.press(await screen.findByLabelText(label));
const pressText = async (text: string) => fireEvent.press(await screen.findByText(text));

async function openForm(setup: Parameters<typeof openVisitTaskScreen>[1] = {}) {
  const app = await openVisitTaskScreen(MENU, { marketing: { visits: [visit({ version: 2 })] }, ...setup });
  await press(CARD);
  await pressText("Complete visit");
  await screen.findByLabelText("Outcome");
  return app;
}

describe("the complete-visit form", () => {
  it("keeps Send disabled while the outcome is empty or only spaces, and says what the outcome should hold", async () => {
    await openForm();

    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);
    expect(screen.getByText(/Who was met, what was discussed and the next action/)).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "    ");
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(true);
    expect(await screen.findByText("An outcome is needed: who was met, what was discussed and the next action.")).toBeTruthy();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "Met the Principal");
    expect(screen.getByLabelText("Send").props.accessibilityState?.disabled).toBe(false);
  });

  it("limits the outcome and the notes to 1000 characters and shows how many are left", async () => {
    await openForm();

    expect(screen.getByLabelText("Outcome").props.maxLength).toBe(1000);
    expect(screen.getByLabelText("Notes").props.maxLength).toBe(1000);
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "x".repeat(40));
    expect(screen.getByLabelText("960 characters left in the outcome")).toBeTruthy();
  });

  it("is not offered without the Edit grant", async () => {
    await openVisitTaskScreen(MENU, { model: visitTaskModel({ visits: { edit: false } }), marketing: { visits: [visit()] } });
    await press(CARD);

    await screen.findByText("Attending: Manoj Manager");
    expect(screen.queryByText("Complete visit")).toBeNull();
  });
});

describe("sending the outcome", () => {
  it("posts the trimmed outcome, notes, follow-up date and the version it read, and shows the visit Completed", async () => {
    const { marketing } = await openForm();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "  Met the Principal and agreed a trial  ");
    await fireEvent.changeText(screen.getByLabelText("Notes"), "  Bring the proposal  ");
    await chooseDate("Follow-up date" as never, "not chosen", "Monday 12 October 2026");

    await press("Send");

    expect(await screen.findByText("Met the Principal and agreed a trial")).toBeTruthy();
    expect(screen.getByText("Completed")).toBeTruthy();
    expect(screen.getByText("Follow-up on 12/10/2026")).toBeTruthy();
    expect(marketing.completions).toEqual([
      {
        id: "v-1",
        body: { outcome: "Met the Principal and agreed a trial", notes: "Bring the proposal", followUpOn: "2026-10-12", version: 2 },
      },
    ]);
  });

  it("leaves out the notes and the follow-up date when none are given, and needs no photo", async () => {
    const { marketing, server } = await openForm();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "Short meeting");

    await press("Send");

    expect(await screen.findByText("Short meeting")).toBeTruthy();
    expect(marketing.completions[0].body).toEqual({ outcome: "Short meeting", version: 2 });
    expect(server.callsTo("POST /api/v1/marketing/activities/{id}/attachments")).toHaveLength(0);
  });

  it("uploads the photos after the visit is completed, one request each with the file field", async () => {
    const { marketing, server } = await openForm();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "Met the Principal");
    await pressText("Take a photo");
    await pressText("Choose from gallery");

    await press("Send");

    expect(await screen.findByLabelText("Photo camera.jpg")).toBeTruthy();
    const order = server.calls
      .filter((c) => c.method === "POST" && /complete|attachments/.test(c.path))
      .map((c) => (c.path.endsWith("complete") ? "complete" : "photo"));
    expect(order).toEqual(["complete", "photo", "photo"]);
    expect(marketing.uploads.map((u) => u.part)).toEqual([
      expect.objectContaining({ fieldName: "file", name: "camera.jpg", type: "image/jpeg" }),
      expect.objectContaining({ fieldName: "file", name: "gallery.png", type: "image/png" }),
    ]);
    expect(screen.getByText("camera.jpg, JPG, 12 B")).toBeTruthy();
    expect(screen.getByText("gallery.png, PNG, 12 B")).toBeTruthy();
  });

  it("asks the server for the follow-up task and never creates one itself", async () => {
    const { server, tasks } = await openForm();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "Met the Principal");
    await chooseDate("Follow-up date" as never, "not chosen", "Monday 12 October 2026");

    await press("Send");
    await screen.findByText("Follow-up on 12/10/2026");

    expect(tasks.details.map((d) => d.task.type)).toEqual(["FOLLOW_UP"]);
    expect(server.callsTo("POST /api/v1/tasks")).toHaveLength(0);
  });

  it("sends one request for a double tap", async () => {
    const { marketing } = await openForm();
    await fireEvent.changeText(screen.getByLabelText("Outcome"), "Met the Principal");
    const send = screen.getByLabelText("Send");

    await fireEvent.press(send);
    await fireEvent.press(send);

    await screen.findByText("Completed");
    expect(marketing.completions).toHaveLength(1);
  });
});
