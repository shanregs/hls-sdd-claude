import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SameDayReport } from "../SameDayReport";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const MENU = {
  reports: [],
  zones: [
    { id: "z1", name: "North Zone" },
    { id: "z2", name: "South Zone" },
  ],
  schools: [],
};

/** Answers the report menu (the Zone list) and the same-day report separately. */
function answer(report: Response) {
  authFetch.mockImplementation(async (url: unknown) =>
    String(url).includes("/reports/same-day") ? report : json(MENU),
  );
}

function reportCalls(): string[] {
  return authFetch.mock.calls
    .map((c) => String(c[0]))
    .filter((u) => u.includes("/reports/same-day"));
}

describe("SameDayReport", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("filters by Zone: the chosen Zone is sent and an out-of-scope Zone shows the reason", async () => {
    answer(json({ tasks: { byManager: [], bySchool: [] } }));
    render(
      <MemoryRouter>
        <SameDayReport />
      </MemoryRouter>,
    );
    await screen.findByRole("region", { name: "Tasks logged for the day" });
    expect(reportCalls()[0]).not.toContain("zoneId");

    answer(json({ reason: "That Zone is outside your area." }, 403));
    await userEvent.click(screen.getByLabelText("Zone"));
    await userEvent.click(
      await screen.findByRole("option", { name: "South Zone" }),
    );

    await waitFor(() => expect(reportCalls().at(-1)).toContain("&zoneId=z2"));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "That Zone is outside your area.",
    );
  });

  it("shows only the sections the server sends, with counts per Manager and per School", async () => {
    answer(
      json({
        leave: {
          byManager: [{ id: "m1", name: "Manoj Manager", count: 2 }],
          bySchool: [{ id: "s1", name: "Demo School One", count: 2 }],
        },
        tasks: {
          byManager: [{ id: "m1", name: "Manoj Manager", count: 3 }],
          bySchool: [],
        },
      }),
    );
    render(
      <MemoryRouter>
        <SameDayReport />
      </MemoryRouter>,
    );

    const leave = await screen.findByRole("region", {
      name: "Leave days recorded for the day",
    });
    expect(within(leave).getByText("Demo School One")).toBeInTheDocument();
    expect(
      screen.getByRole("region", { name: "Tasks logged for the day" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("region", { name: "Visits logged for the day" }),
    ).not.toBeInTheDocument();
    expect(reportCalls()[0]).toContain("/api/v1/reports/same-day?month=");
  });

  it("says why when the report cannot be shown", async () => {
    answer(json({ reason: "You cannot see this report." }, 403));
    render(
      <MemoryRouter>
        <SameDayReport />
      </MemoryRouter>,
    );

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "You cannot see this report.",
    );
  });
});
