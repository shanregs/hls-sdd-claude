import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SalaryDialog } from "./SalaryDialog";
import type { TeacherSummary } from "./teachersApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const TARA = { id: "t1", name: "Tara Teacher" } as TeacherSummary;

const HISTORY = {
  current: { id: "e2", amount: 22000, effectiveOn: "2026-10-01" },
  history: [
    { id: "e2", amount: 22000, effectiveOn: "2026-10-01" },
    { id: "e1", amount: 1250000.5, effectiveOn: "2026-04-01" },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("SalaryDialog (User Story 9)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the current amount and every dated entry in rupees with Indian grouping", async () => {
    authFetch.mockResolvedValue(jsonResponse(HISTORY));

    render(<SalaryDialog teacher={TARA} onClose={vi.fn()} />);

    expect(
      await screen.findByText(/current: ₹22,000\.00/i),
    ).toBeInTheDocument();
    expect(screen.getByText("₹12,50,000.50")).toBeInTheDocument();
    expect(screen.getByText("From 01/04/2026")).toBeInTheDocument();
    expect(String(authFetch.mock.calls[0][0])).toBe(
      "/api/v1/teachers/t1/salary",
    );
  });

  it("appends an entry and reloads, validating the amount first", async () => {
    authFetch.mockImplementation(async (_url: string, init?: RequestInit) =>
      init?.method === "POST"
        ? jsonResponse({ id: "e3" }, 201)
        : jsonResponse(HISTORY),
    );
    const user = userEvent.setup();

    render(<SalaryDialog teacher={TARA} onClose={vi.fn()} />);
    await screen.findByText(/current:/i);
    await user.click(screen.getByRole("button", { name: /add entry/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /enter a salary amount/i,
    );

    await user.type(screen.getByLabelText(/salary amount/i), "24000");
    await user.click(screen.getByRole("button", { name: /add entry/i }));

    const post = authFetch.mock.calls.find(
      (c) => (c[1] as RequestInit | undefined)?.method === "POST",
    )!;
    expect(JSON.parse((post[1] as RequestInit).body as string)).toMatchObject({
      amount: 24000,
    });
  });

  it("answers an as-of lookup, including no salary recorded", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url.includes("asOf=2026-06-15"))
        return jsonResponse({ amount: 20000, effectiveOn: "2026-04-01" });
      if (url.includes("asOf="))
        return jsonResponse({ amount: null, effectiveOn: null });
      return jsonResponse(HISTORY);
    });
    const user = userEvent.setup();

    render(<SalaryDialog teacher={TARA} onClose={vi.fn()} />);
    await screen.findByText(/current:/i);
    await user.type(screen.getByLabelText(/salary as of/i), "2026-06-15");
    await user.click(screen.getByRole("button", { name: /look up/i }));
    expect(
      await screen.findByText("₹20,000.00 on 15/06/2026"),
    ).toBeInTheDocument();

    await user.clear(screen.getByLabelText(/salary as of/i));
    await user.type(screen.getByLabelText(/salary as of/i), "2026-01-01");
    await user.click(screen.getByRole("button", { name: /look up/i }));
    expect(
      await screen.findByText(/no salary recorded for that date/i),
    ).toBeInTheDocument();
  });

  it("shows the refusal when the caller has no salary access", async () => {
    authFetch.mockResolvedValue(jsonResponse({ reason: "Access denied" }, 403));

    render(<SalaryDialog teacher={TARA} onClose={vi.fn()} />);

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
