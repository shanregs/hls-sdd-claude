import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ApplyLeavePage } from "./ApplyLeavePage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const TYPES = [
  { id: "t1", code: "CASUAL", name: "Casual" },
  { id: "t2", code: "SICK", name: "Sick" },
];

const OK_PREVIEW = {
  workingDays: 2.5,
  days: [
    { date: "2026-01-14", value: 1 },
    { date: "2026-01-15", value: 1 },
    { date: "2026-01-17", value: 0.5 },
  ],
  problems: [],
};

let previewResponse: Response;
let submitResponse: Response;

function mockApi() {
  authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url.endsWith("/types")) return json(TYPES);
    if (url.endsWith("/preview")) return previewResponse;
    if (init?.method === "POST") return submitResponse;
    return json({});
  });
}

async function fillForm(reason = "Pongal travel") {
  const user = userEvent.setup();
  await user.click(await screen.findByLabelText(/Leave type/));
  await user.click(await screen.findByRole("option", { name: "Casual" }));
  fireEvent.change(screen.getByLabelText(/First day/), {
    target: { value: "2026-01-14" },
  });
  fireEvent.change(screen.getByLabelText(/Last day/), {
    target: { value: "2026-01-17" },
  });
  await user.type(screen.getByLabelText(/Reason/), reason);
  return user;
}

describe("ApplyLeavePage", () => {
  beforeEach(() => {
    authFetch.mockReset();
    previewResponse = json(OK_PREVIEW);
    submitResponse = json({ id: "r1", status: "PENDING" }, 201);
    mockApi();
  });

  it("shows the working-day count for the chosen dates", async () => {
    render(<ApplyLeavePage />);
    await fillForm();

    expect(
      await screen.findByText(/covers 2.5 working days/),
    ).toBeInTheDocument();
    expect(screen.getByText(/17\/01\/2026 \(half\)/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send request" })).toBeEnabled();
  });

  it("shows a refusal and keeps Send disabled", async () => {
    previewResponse = json({
      workingDays: 0,
      days: [],
      problems: [
        "These dates overlap your pending request from 2026-01-12 to 2026-01-14.",
      ],
    });
    render(<ApplyLeavePage />);
    await fillForm();

    expect(
      await screen.findByText(/overlap your pending request/),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Send request" })).toBeDisabled();
  });

  it("tells a Teacher without a placement why they cannot apply", async () => {
    previewResponse = json({
      workingDays: 0,
      days: [],
      problems: [
        "You are not placed at a School on these dates, so you cannot apply for leave.",
      ],
    });
    render(<ApplyLeavePage />);
    await fillForm();

    expect(
      await screen.findByText(/not placed at a School/),
    ).toBeInTheDocument();
  });

  it("sends the request and confirms", async () => {
    render(<ApplyLeavePage />);
    const user = await fillForm("Family function");
    await screen.findByText(/covers 2.5 working days/);

    await user.click(screen.getByRole("button", { name: "Send request" }));

    expect(
      await screen.findByText(/leave request was sent/),
    ).toBeInTheDocument();
    const post = authFetch.mock.calls.find(
      ([url, init]) =>
        String(url).endsWith("/api/v1/me/leave") && init?.method === "POST",
    );
    expect(JSON.parse(post![1].body)).toEqual({
      leaveTypeId: "t1",
      firstDate: "2026-01-14",
      lastDate: "2026-01-17",
      halfDayStart: false,
      halfDayEnd: false,
      reason: "Family function",
    });
  });

  it("shows the server refusal when sending fails", async () => {
    submitResponse = json(
      { reason: "Attendance is locked for a month in this range." },
      409,
    );
    render(<ApplyLeavePage />);
    const user = await fillForm();
    await screen.findByText(/covers 2.5 working days/);

    await user.click(screen.getByRole("button", { name: "Send request" }));

    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("locked"),
    );
  });
});
