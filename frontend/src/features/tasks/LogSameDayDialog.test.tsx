import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { LogSameDayDialog } from "./LogSameDayDialog";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const TYPES = [{ value: "SCHOOL_VISIT", label: "School visit" }];

describe("LogSameDayDialog", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("needs a note of at least 10 characters before a task can be saved as Completed", async () => {
    render(
      <LogSameDayDialog types={TYPES} onClose={vi.fn()} onLogged={vi.fn()} />,
    );

    await userEvent.type(
      screen.getByRole("textbox", { name: /Title/ }),
      "Visit St Mary's",
    );
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
    await userEvent.type(
      screen.getByRole("textbox", { name: /What was done/ }),
      "Met the Principal",
    );
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });

  it("sends no assignee and no date, and reports the logged task", async () => {
    authFetch.mockResolvedValue(
      json({ id: "t1", status: "VERIFIED", sameDay: true }, 201),
    );
    const onLogged = vi.fn();
    render(
      <LogSameDayDialog types={TYPES} onClose={vi.fn()} onLogged={onLogged} />,
    );

    await userEvent.type(
      screen.getByRole("textbox", { name: /Title/ }),
      "Visit St Mary's",
    );
    await userEvent.type(
      screen.getByRole("textbox", { name: /What was done/ }),
      "Met the Principal",
    );
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(onLogged).toHaveBeenCalled());
    const [url, init] = authFetch.mock.calls[0];
    expect(String(url)).toBe("/api/v1/tasks/same-day");
    const body = JSON.parse(String(init.body));
    expect(body.status).toBe("COMPLETED");
    expect(body).not.toHaveProperty("assigneeUserId");
    expect(body).not.toHaveProperty("dueOn");
  });

  it("In progress needs no note", async () => {
    render(
      <LogSameDayDialog types={TYPES} onClose={vi.fn()} onLogged={vi.fn()} />,
    );

    await userEvent.type(
      screen.getByRole("textbox", { name: /Title/ }),
      "Visit St Mary's",
    );
    await userEvent.click(screen.getByRole("radio", { name: "In progress" }));

    expect(
      screen.queryByRole("textbox", { name: /What was done/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });
});
