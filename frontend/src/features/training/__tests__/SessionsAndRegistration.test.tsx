import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../../theme/tokens";
import { TrainingCalendarPage } from "../TrainingCalendarPage";
import { UpcomingTrainingPage } from "../UpcomingTrainingPage";

const authFetch = vi.fn();
const access = { roles: ["ADMIN"] as string[] };

vi.mock("../../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));
vi.mock("../../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: { roles: access.roles, dataScope: {}, navigation: [] },
  }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function today(offset = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offset);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function session(over: Record<string, unknown> = {}) {
  return {
    id: "s1",
    type: "WEEKEND",
    topic: "Phonics refresher",
    date: today(5),
    startsAt: "10:00:00",
    endsAt: "13:00:00",
    trainerName: "Trainer T",
    trainerContact: null,
    venueKind: "PHYSICAL",
    venue: "Head office",
    seatLimit: 3,
    description: null,
    status: "PUBLISHED",
    patternId: null,
    patternMonth: null,
    handEdited: false,
    cancelReason: null,
    skipReason: null,
    version: 0,
    registered: 0,
    seatsLeft: 3,
    counts: null,
    actions: [],
    holidayNotice: false,
    attendanceNotRecorded: false,
    groupNotFullyAssigned: null,
    earlierDates: [],
    history: null,
    my: null,
    ...over,
  };
}

function renderUi(ui: React.ReactElement) {
  return render(
    <ThemeProvider theme={buildMuiTheme("light")}>
      <MemoryRouter>{ui}</MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  access.roles = ["ADMIN"];
});

describe("Training sessions and registration (spec 024 US1)", () => {
  it("shows the page title by role", async () => {
    authFetch.mockResolvedValue(json([]));
    renderUi(<TrainingCalendarPage />);
    expect(
      await screen.findByRole("heading", { name: "Training Calendar" }),
    ).toBeTruthy();
  });

  it("titles the Manager screen My Training and offers no New session button", async () => {
    access.roles = ["MANAGER"];
    authFetch.mockResolvedValue(json([]));
    renderUi(<TrainingCalendarPage />);
    expect(
      await screen.findByRole("heading", { name: "My Training" }),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "New session" })).toBeNull();
  });

  it("validates the dialog before anything is sent", async () => {
    authFetch.mockResolvedValue(json([]));
    renderUi(<TrainingCalendarPage />);
    await userEvent.click(
      await screen.findByRole("button", { name: "New session" }),
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save as draft" }),
    );
    expect(await screen.findByText("Enter the topic.")).toBeTruthy();
    expect(screen.getByText("Choose the date.")).toBeTruthy();
    const posts = authFetch.mock.calls.filter(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    );
    expect(posts).toHaveLength(0);
  });

  it("refuses an online session whose link is not https", async () => {
    authFetch.mockResolvedValue(json([]));
    renderUi(<TrainingCalendarPage />);
    await userEvent.click(
      await screen.findByRole("button", { name: "New session" }),
    );
    await userEvent.selectOptions(
      screen.getByLabelText("Venue kind"),
      "VIRTUAL",
    );
    await userEvent.type(
      screen.getByLabelText(/Meeting link/),
      "http://example.org/x",
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Save as draft" }),
    );
    expect(
      await screen.findByText("The meeting link must start with https://"),
    ).toBeTruthy();
  });

  it("lets a Teacher register and then cancel", async () => {
    access.roles = ["TEACHER"];
    let registered = false;
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") {
        registered = true;
        return json(
          {
            sessionId: "s1",
            teacherId: "t1",
            status: "REGISTERED",
            created: true,
            seatsLeft: 2,
          },
          201,
        );
      }
      if (init?.method === "DELETE") {
        registered = false;
        return json({
          sessionId: "s1",
          teacherId: "t1",
          status: "CANCELLED",
          created: false,
          seatsLeft: 3,
        });
      }
      return json([
        {
          label: registered ? "REGISTERED" : "OPEN",
          session: session({
            my: { status: registered ? "REGISTERED" : null, open: !registered },
            actions: registered ? ["CANCEL_REGISTRATION"] : ["REGISTER"],
          }),
        },
      ]);
    });
    renderUi(<UpcomingTrainingPage />);
    await userEvent.click(
      await screen.findByRole("button", { name: "Register" }),
    );
    expect(await screen.findByText("Registered")).toBeTruthy();
    await userEvent.click(
      screen.getByRole("button", { name: "Cancel registration" }),
    );
    expect(
      await screen.findByRole("button", { name: "Register" }),
    ).toBeTruthy();
  });

  it("does not offer a full session", async () => {
    access.roles = ["TEACHER"];
    authFetch.mockResolvedValue(json([]));
    renderUi(<UpcomingTrainingPage />);
    expect(
      await screen.findByText("No upcoming training for you right now."),
    ).toBeTruthy();
    expect(screen.queryByRole("button", { name: "Register" })).toBeNull();
  });

  it("shows the server's refusal when registering fails", async () => {
    access.roles = ["TEACHER"];
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST")
        return json({ reason: "No seats left." }, 409);
      return json([
        {
          label: "OPEN",
          session: session({
            my: { status: null, open: true },
            actions: ["REGISTER"],
          }),
        },
      ]);
    });
    renderUi(<UpcomingTrainingPage />);
    await userEvent.click(
      await screen.findByRole("button", { name: "Register" }),
    );
    expect(await screen.findByText("No seats left.")).toBeTruthy();
  });
});
