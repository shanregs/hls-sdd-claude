import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { RecordSameDayLeaveDialog } from "./RecordSameDayLeaveDialog";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const TEACHERS = {
  content: [
    {
      id: "t1",
      name: "Tara Teacher",
      school: { id: "s1", name: "Demo School One" },
    },
  ],
  page: 0,
  size: 200,
  totalElements: 1,
};
const TYPES = [{ id: "ty1", code: "CASUAL", name: "Casual" }];

describe("RecordSameDayLeaveDialog", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("records leave for today for the chosen Teacher and reports success", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") return json({ id: "r1" }, 201);
      if (String(url).includes("/api/v1/teachers")) return json(TEACHERS);
      return json(TYPES);
    });
    const onRecorded = vi.fn();
    render(
      <RecordSameDayLeaveDialog onClose={vi.fn()} onRecorded={onRecorded} />,
    );

    await userEvent.click(
      await screen.findByRole("combobox", { name: /Teacher/ }),
    );
    await userEvent.click(
      await screen.findByRole("option", { name: /Tara Teacher/ }),
    );
    await userEvent.click(screen.getByRole("combobox", { name: /Leave type/ }));
    await userEvent.click(
      await screen.findByRole("option", { name: "Casual" }),
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: /Reason/ }),
      "Fever",
    );
    await userEvent.click(screen.getByRole("button", { name: "Record leave" }));

    await waitFor(() => expect(onRecorded).toHaveBeenCalled());
    const post = authFetch.mock.calls.find((c) => c[1]?.method === "POST");
    expect(String(post?.[0])).toBe("/api/v1/leave/same-day");
    const body = JSON.parse(String(post?.[1].body));
    expect(body.teacherId).toBe("t1");
    expect(body.firstDate).toBe(body.lastDate);
  });

  it("shows the server's refusal and stays open", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST")
        return json({ reason: "Attendance is locked for this month." }, 409);
      if (String(url).includes("/api/v1/teachers")) return json(TEACHERS);
      return json(TYPES);
    });
    const onRecorded = vi.fn();
    render(
      <RecordSameDayLeaveDialog onClose={vi.fn()} onRecorded={onRecorded} />,
    );

    await userEvent.click(
      await screen.findByRole("combobox", { name: /Teacher/ }),
    );
    await userEvent.click(
      await screen.findByRole("option", { name: /Tara Teacher/ }),
    );
    await userEvent.click(screen.getByRole("combobox", { name: /Leave type/ }));
    await userEvent.click(
      await screen.findByRole("option", { name: "Casual" }),
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: /Reason/ }),
      "Fever",
    );
    await userEvent.click(screen.getByRole("button", { name: "Record leave" }));

    expect(await screen.findByText(/locked/)).toBeInTheDocument();
    expect(onRecorded).not.toHaveBeenCalled();
  });
});
