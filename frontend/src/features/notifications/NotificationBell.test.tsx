import { act, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationBell, POLL_MS } from "./NotificationBell";

const authFetch = vi.fn();
let actions: string[] = ["VIEW", "DELETE"];
let unread = 3;

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["TEACHER"],
      dataScope: {},
      navigation:
        actions.length === 0
          ? []
          : [
              {
                section: "ACCOUNT",
                items: [
                  {
                    label: "Notifications",
                    route: "/account/notifications",
                    actions,
                  },
                ],
              },
            ],
    },
  }),
}));

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function setVisibility(state: "visible" | "hidden") {
  Object.defineProperty(document, "visibilityState", {
    configurable: true,
    get: () => state,
  });
}

function countCalls() {
  return authFetch.mock.calls.filter(([url]) =>
    String(url).endsWith("/unread-count"),
  ).length;
}

async function renderBell() {
  render(
    <MemoryRouter>
      <NotificationBell />
    </MemoryRouter>,
  );
  await act(async () => {});
}

describe("NotificationBell", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    actions = ["VIEW", "DELETE"];
    unread = 3;
    setVisibility("visible");
    authFetch.mockReset();
    authFetch.mockImplementation(async () => ({
      ok: true,
      status: 200,
      json: async () => ({ unread }),
    }));
  });

  afterEach(() => {
    vi.useRealTimers();
    setVisibility("visible");
  });

  it("shows the unread count and links to the page", async () => {
    await renderBell();
    const link = screen.getByRole("link", { name: "Notifications, 3 unread" });
    expect(link).toHaveAttribute("href", "/account/notifications");
    expect(screen.getByText("3")).toBeInTheDocument();
  });

  it("polls the count every 30 seconds", async () => {
    await renderBell();
    expect(countCalls()).toBe(1);
    unread = 5;
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS);
    });
    expect(countCalls()).toBe(2);
    expect(
      screen.getByRole("link", { name: "Notifications, 5 unread" }),
    ).toBeInTheDocument();
  });

  it("hides the badge at zero", async () => {
    unread = 0;
    await renderBell();
    expect(
      screen.getByRole("link", { name: "Notifications, none unread" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("shows 99+ above 99", async () => {
    unread = 150;
    await renderBell();
    expect(screen.getByText("99+")).toBeInTheDocument();
  });

  it("renders nothing and never polls without the permission", async () => {
    actions = [];
    await renderBell();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS * 2);
    });
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("pauses polling while the tab is hidden", async () => {
    await renderBell();
    const before = countCalls();
    setVisibility("hidden");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(POLL_MS * 3);
    });
    expect(countCalls()).toBe(before);
  });

  it("refreshes when the window regains focus", async () => {
    await renderBell();
    const before = countCalls();
    unread = 9;
    await act(async () => {
      window.dispatchEvent(new Event("focus"));
    });
    expect(countCalls()).toBe(before + 1);
    expect(
      screen.getByRole("link", { name: "Notifications, 9 unread" }),
    ).toBeInTheDocument();
  });
});
