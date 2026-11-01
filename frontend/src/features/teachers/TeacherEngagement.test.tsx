import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { ThemeProvider } from "@mui/material/styles";
import { axe } from "vitest-axe";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { buildMuiTheme } from "../../theme/tokens";
import { TeacherDialog } from "./TeacherDialog";
import type { TeacherSummary } from "./teachersApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({ useAuth: () => ({ authFetch }) }));

function teacher(overrides: Partial<TeacherSummary> = {}): TeacherSummary {
  return {
    id: "t-1",
    name: "Sita",
    phone: "9800000099",
    email: null,
    address: null,
    status: "ACTIVE",
    statusEffectiveOn: "2026-10-01",
    allowedNextStatuses: [],
    userId: null,
    version: 4,
    school: null,
    manager: null,
    pendingPlacement: null,
    placements: [],
    engagement: "REGULAR",
    ...overrides,
  };
}

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function renderDialog(props: Partial<React.ComponentProps<typeof TeacherDialog>> = {}, mode: "light" | "dark" = "light") {
  return render(
    <ThemeProvider theme={buildMuiTheme(mode)}>
      <TeacherDialog onClose={() => undefined} onSaved={() => undefined} {...props} />
    </ThemeProvider>,
  );
}

beforeEach(() => {
  authFetch.mockReset();
  authFetch.mockImplementation(async (url: string, init?: RequestInit) =>
    json(teacher({ version: 5, engagement: init?.body && String(init.body).includes("SUBSTITUTE") ? "SUBSTITUTE" : "REGULAR" }), 200),
  );
});

describe("Teacher engagement (spec 017b)", () => {
  it("creates a Substitute when the Engagement field says so", async () => {
    renderDialog();
    await userEvent.type(screen.getByLabelText(/Teacher name/), "Sita Substitute");
    await userEvent.click(screen.getByRole("combobox", { name: /Engagement/ }));
    await userEvent.click(await screen.findByRole("option", { name: "Substitute" }));
    await userEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() =>
      expect(authFetch).toHaveBeenCalledWith(
        "/api/v1/teachers",
        expect.objectContaining({ method: "POST" }),
      ),
    );
    const body = JSON.parse(String(authFetch.mock.calls[0][1].body)) as { engagement: string; name: string };
    expect(body).toMatchObject({ name: "Sita Substitute", engagement: "SUBSTITUTE" });
  });

  it("creates a Regular Teacher by default", async () => {
    renderDialog();
    await userEvent.type(screen.getByLabelText(/Teacher name/), "Rita Regular");
    await userEvent.click(screen.getByRole("button", { name: "Create" }));

    await waitFor(() => expect(authFetch).toHaveBeenCalled());
    expect(JSON.parse(String(authFetch.mock.calls[0][1].body))).toMatchObject({ engagement: "REGULAR" });
  });

  it("changes the engagement of an existing Teacher with the version after saving the contact", async () => {
    renderDialog({ teacher: teacher() });
    await userEvent.click(screen.getByRole("combobox", { name: /Engagement/ }));
    await userEvent.click(await screen.findByRole("option", { name: "Substitute" }));
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(authFetch).toHaveBeenCalledTimes(2));
    expect(authFetch.mock.calls[0][0]).toBe("/api/v1/teachers/t-1");
    expect(authFetch.mock.calls[1][0]).toBe("/api/v1/teachers/t-1/engagement");
    expect(JSON.parse(String(authFetch.mock.calls[1][1].body))).toEqual({ engagement: "SUBSTITUTE", version: 5 });
  });

  it("does not show the field to a Zone Manager, who edits contact details only", () => {
    renderDialog({ teacher: teacher(), limited: true });

    expect(screen.queryByRole("combobox", { name: /Engagement/ })).not.toBeInTheDocument();
  });

  it.each(["light", "dark"] as const)("has no WCAG 2.2 AA violations in %s mode", async (mode) => {
    const options = { runOnly: { type: "tag" as const, values: ["wcag2a", "wcag2aa", "wcag22aa"] } };
    renderDialog({}, mode);
    await screen.findByRole("dialog");

    expect((await axe(document.body, options)).violations.map((v) => `${v.id}: ${v.help}`)).toEqual([]);
  }, 30_000);
});
