import { render, screen, within } from "@testing-library/react";
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

describe("SameDayReport", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows only the sections the server sends, with counts per Manager and per School", async () => {
    authFetch.mockResolvedValue(
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
    expect(String(authFetch.mock.calls[0][0])).toContain(
      "/api/v1/reports/same-day?month=",
    );
  });

  it("says why when the report cannot be shown", async () => {
    authFetch.mockResolvedValue(
      json({ reason: "You cannot see this report." }, 403),
    );
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
