import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { NotificationsPage } from "./NotificationsPage";
import { NOTIFICATIONS_CHANGED } from "./notificationsApi";

const authFetch = vi.fn();
let grantedActions = ["VIEW", "DELETE"];

vi.mock("../../access-model/useAccessModel", () => ({
  useAccessModel: () => ({
    loading: false,
    accessModel: {
      roles: ["TEACHER"],
      dataScope: {},
      navigation: [
        {
          section: "ACCOUNT",
          items: [
            {
              label: "Notifications",
              route: "/account/notifications",
              actions: grantedActions,
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

function note(
  id: string,
  read: boolean,
  link: string | null = "/leave/history",
) {
  return {
    id,
    type: "LEAVE_DECIDED",
    title: `Title ${id}`,
    message: `Message ${id}`,
    link,
    channel: "IN_APP",
    read,
    createdAt: "2026-10-05T08:30:00Z",
    updatedAt: "2026-10-05T08:30:00Z",
  };
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

let pageOne = [note("n1", false), note("n2", true, null)];
let total = 2;

function mockApi() {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.startsWith("/api/v1/me/notifications?")) {
      const params = new URLSearchParams(url.split("?")[1]);
      if (params.get("page") === "1") {
        return json({
          content: [note("n3", true)],
          page: 1,
          size: 25,
          totalElements: total,
          unread: 1,
        });
      }
      const rows =
        params.get("unread") === "true"
          ? pageOne.filter((n) => !n.read)
          : pageOne;
      return json({
        content: rows,
        page: 0,
        size: 25,
        totalElements: total,
        unread: 1,
      });
    }
    if (init?.method === "POST" && url.endsWith("/read-all"))
      return json({ marked: 1 });
    if (init?.method === "POST") return json(note("n1", true));
    if (init?.method === "DELETE" && url.endsWith("/read"))
      return json({ deleted: 1 });
    return json({}, 200);
  });
}

function calls(method: string) {
  return authFetch.mock.calls.filter(([, init]) => init?.method === method);
}

function renderPage() {
  render(
    <MemoryRouter initialEntries={["/account/notifications"]}>
      <Routes>
        <Route path="/account/notifications" element={<NotificationsPage />} />
        <Route path="/leave/history" element={<p>Leave history screen</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe("NotificationsPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
    grantedActions = ["VIEW", "DELETE"];
    pageOne = [note("n1", false), note("n2", true, null)];
    total = 2;
    mockApi();
  });

  it("lists notifications and marks unread ones with text", async () => {
    renderPage();
    expect(await screen.findByText("Title n1")).toBeInTheDocument();
    expect(screen.getByText("Title n2")).toBeInTheDocument();
    expect(screen.getAllByText("Unread")).toHaveLength(1);
  });

  it("shows an empty state", async () => {
    pageOne = [];
    total = 0;
    renderPage();
    expect(await screen.findByText("No notifications.")).toBeInTheDocument();
  });

  it("shows an error when loading fails", async () => {
    authFetch.mockResolvedValue(json({ reason: "boom" }, 500));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("boom");
  });

  it("opens an unread notification: marks it read then navigates to its link", async () => {
    renderPage();
    await userEvent.click(await screen.findByText("Title n1"));
    expect(await screen.findByText("Leave history screen")).toBeInTheDocument();
    expect(calls("POST").map(([url]) => url)).toEqual([
      "/api/v1/me/notifications/n1/read",
    ]);
  });

  it("does not call read again for an already-read notification", async () => {
    pageOne = [note("n2", true)];
    renderPage();
    await userEvent.click(await screen.findByText("Title n2"));
    expect(await screen.findByText("Leave history screen")).toBeInTheDocument();
    expect(calls("POST")).toHaveLength(0);
  });

  it("marks all as read", async () => {
    renderPage();
    await screen.findByText("Title n1");
    await userEvent.click(
      screen.getByRole("button", { name: "Mark all as read" }),
    );
    expect(calls("POST").map(([url]) => url)).toContain(
      "/api/v1/me/notifications/read-all",
    );
  });

  it("tells the header bell after reading, marking all and deleting", async () => {
    const changed = vi.fn();
    window.addEventListener(NOTIFICATIONS_CHANGED, changed);
    try {
      renderPage();
      await screen.findByText("Title n1");
      await userEvent.click(
        screen.getByRole("button", { name: "Mark all as read" }),
      );
      await vi.waitFor(() => expect(changed).toHaveBeenCalledTimes(1));
      await userEvent.click(
        await screen.findByRole("button", { name: "Delete Title n1" }),
      );
      const dialog = await screen.findByRole("dialog");
      await userEvent.click(
        within(dialog).getByRole("button", { name: "Delete" }),
      );
      await vi.waitFor(() => expect(changed).toHaveBeenCalledTimes(2));
      await userEvent.click(await screen.findByText("Title n1"));
      await vi.waitFor(() => expect(changed).toHaveBeenCalledTimes(3));
    } finally {
      window.removeEventListener(NOTIFICATIONS_CHANGED, changed);
    }
  });

  it("filters to unread only", async () => {
    renderPage();
    await screen.findByText("Title n2");
    await userEvent.click(screen.getByRole("switch", { name: "Unread only" }));
    expect(await screen.findByText("Title n1")).toBeInTheDocument();
    expect(screen.queryByText("Title n2")).not.toBeInTheDocument();
  });

  it("loads more pages", async () => {
    total = 3;
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", { name: "Load more" }),
    );
    expect(await screen.findByText("Title n3")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Load more" }),
    ).not.toBeInTheDocument();
  });

  it("deletes one notification after confirmation", async () => {
    renderPage();
    await userEvent.click(
      await screen.findByRole("button", { name: "Delete Title n1" }),
    );
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Delete" }),
    );
    await vi.waitFor(() =>
      expect(calls("DELETE").map(([url]) => url)).toEqual([
        "/api/v1/me/notifications/n1",
      ]),
    );
  });

  it("clears read notifications after confirmation", async () => {
    renderPage();
    await screen.findByText("Title n1");
    await userEvent.click(screen.getByRole("button", { name: "Clear read" }));
    const dialog = await screen.findByRole("dialog");
    await userEvent.click(
      within(dialog).getByRole("button", { name: "Clear read" }),
    );
    await vi.waitFor(() =>
      expect(calls("DELETE").map(([url]) => url)).toEqual([
        "/api/v1/me/notifications/read",
      ]),
    );
  });

  it("hides delete controls without the DELETE action", async () => {
    grantedActions = ["VIEW"];
    renderPage();
    await screen.findByText("Title n1");
    expect(
      screen.queryByRole("button", { name: /^Delete/ }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Clear read" }),
    ).not.toBeInTheDocument();
  });
});
