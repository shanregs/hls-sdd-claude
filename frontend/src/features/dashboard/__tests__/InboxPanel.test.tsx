import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { InboxPanel } from "../InboxPanel";
import { ManagementDashboard } from "../ManagementDashboard";

const authFetch = vi.fn();

vi.mock("../../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function jsonResponse(body: unknown) {
  return { ok: true, status: 200, json: async () => body } as Response;
}

const inbox = {
  totals: { MANAGER_LEAVE: 1, PAYROLL_RUN: 1 },
  takenAt: "2026-10-21T06:30:12Z",
  page: 0,
  size: 50,
  total: 2,
  items: [
    {
      kind: "MANAGER_LEAVE",
      itemId: "a",
      requester: "Asha K",
      subject: "Leave on 2026-10-18",
      askedOn: "2026-10-18",
      ageDays: 3,
      link: { path: "/operations/manager-leave", query: { id: "a" } },
    },
    {
      kind: "PAYROLL_RUN",
      itemId: "b",
      requester: "Ravi",
      subject: "Pay run for 2026-09",
      askedOn: "2026-10-20",
      ageDays: 1,
      link: { path: "/operations/payroll/b", query: {} },
    },
  ],
};

describe("InboxPanel (spec 028 US6)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows totals per kind and each item with requester, DD/MM/YYYY date, age and a link", async () => {
    authFetch.mockResolvedValue(jsonResponse(inbox));
    render(
      <MemoryRouter>
        <InboxPanel />
      </MemoryRouter>,
    );

    expect(await screen.findByText("Manager leave: 1")).toBeInTheDocument();
    expect(screen.getByText("Pay runs: 1")).toBeInTheDocument();
    const link = screen.getByRole("link", { name: /Leave on 2026-10-18 \(Asha K\)/ });
    expect(link).toHaveAttribute("href", "/operations/manager-leave?id=a");
    expect(screen.getByText(/18\/10\/2026/)).toBeInTheDocument();
    expect(screen.getByText(/3 working days ago/)).toBeInTheDocument();
    expect(screen.getByText(/1 working day ago/)).toBeInTheDocument();
  });

  it("reads again on Refresh", async () => {
    authFetch.mockResolvedValue(jsonResponse(inbox));
    render(
      <MemoryRouter>
        <InboxPanel />
      </MemoryRouter>,
    );
    await screen.findByText("Manager leave: 1");
    await userEvent.click(screen.getByRole("button", { name: /refresh/i }));
    await waitFor(() => expect(authFetch).toHaveBeenCalledTimes(2));
  });
});

describe("ManagementDashboard inbox flag", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  const base = {
    areas: [
      {
        area: "MANAGERS",
        title: "Managers",
        tiles: [
          {
            key: "m.x",
            label: "X",
            periodKind: "TODAY",
            zoneAware: true,
            link: { path: "/operations/tasks", query: {} },
          },
        ],
      },
    ],
    filters: { zones: [], defaultPeriod: "2026-10" },
  };

  function mock(inboxFlag: boolean) {
    authFetch.mockImplementation(async (url: string) => {
      if (url === "/api/v1/dashboard")
        return jsonResponse({ ...base, inbox: inboxFlag });
      if (url.startsWith("/api/v1/dashboard/inbox")) return jsonResponse(inbox);
      return jsonResponse({
        area: "MANAGERS",
        takenAt: "2026-10-21T06:30:12Z",
        tiles: [],
      });
    });
  }

  it("renders the inbox only when the manifest says so", async () => {
    mock(true);
    const { unmount } = render(
      <MemoryRouter>
        <ManagementDashboard title="Overview" />
      </MemoryRouter>,
    );
    expect(await screen.findByText("Waiting for your decision")).toBeInTheDocument();
    unmount();

    mock(false);
    render(
      <MemoryRouter>
        <ManagementDashboard title="Overview" />
      </MemoryRouter>,
    );
    await screen.findByText("Managers");
    expect(screen.queryByText("Waiting for your decision")).not.toBeInTheDocument();
  });
});
