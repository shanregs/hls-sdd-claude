import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { SchoolDialog } from "./SchoolDialog";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const SCHOOL = {
  id: "s1",
  name: "Demo School One",
  address: "1 Main Road",
  contactPerson: "Mr Kumar",
  contactPhone: "9000000000",
  billingContact: "accounts@school.test",
  active: true,
  version: 3,
  place: { id: "p1", name: "Adyar", pinCode: "600020" },
  zone: { id: "z1", name: "Demo Zone" },
};

describe("SchoolDialog contacts (spec 016 amendment A8)", () => {
  beforeEach(() => authFetch.mockReset());

  it("loads the principal and accountant, and saves them after the School", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith("/contacts") && !init?.method) {
        return json({
          principal: { name: "Mrs Rao", phone: "9111111111" },
          accountant: null,
        });
      }
      if (String(url).endsWith("/contacts") && init?.method === "PUT") {
        return json(JSON.parse(init.body as string));
      }
      return json({ ...SCHOOL });
    });
    const onSaved = vi.fn();
    render(
      <SchoolDialog
        school={SCHOOL as never}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    const principal = await screen.findByLabelText("Principal name");
    await waitFor(() => expect(principal).toHaveValue("Mrs Rao"));
    await userEvent.type(screen.getByLabelText("Accountant name"), "Mr Iyer");
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(onSaved).toHaveBeenCalled());
    const put = authFetch.mock.calls.find(
      ([url, init]) =>
        String(url).endsWith("/contacts") &&
        (init as RequestInit | undefined)?.method === "PUT",
    );
    const body = JSON.parse((put![1] as RequestInit).body as string);
    expect(body.principal.name).toBe("Mrs Rao");
    expect(body.accountant.name).toBe("Mr Iyer");
  });

  it("tells the user when the School saved but the contacts did not", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (String(url).endsWith("/contacts") && init?.method === "PUT")
        return json(
          { reason: "Accountant phone must be at most 20 characters." },
          400,
        );
      if (String(url).endsWith("/contacts"))
        return json({ principal: null, accountant: null });
      return json({ ...SCHOOL });
    });
    const onSaved = vi.fn();
    render(
      <SchoolDialog
        school={SCHOOL as never}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );

    await userEvent.type(
      await screen.findByLabelText("Accountant name"),
      "Mr Iyer",
    );
    await userEvent.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "contacts were not",
    );
    expect(onSaved).not.toHaveBeenCalled();
  });
});
