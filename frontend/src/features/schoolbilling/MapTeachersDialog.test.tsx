import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MapTeachersDialog } from "./MapTeachersDialog";
import type { ContractRow } from "./schoolContractsApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const CONTRACT: ContractRow = {
  id: "c2",
  schoolId: "s1",
  state: "ACTIVE",
  status: "ACTIVE",
  salaryMode: "PER_TEACHER",
  teacherCount: 2,
  rate: null,
  signedOn: "2026-09-20",
  startsOn: "2026-11-01",
  endsOn: null,
  version: 0,
  positions: [
    {
      id: "p1",
      number: 1,
      title: "Maths PGT",
      salary: "20000.00",
      teacherId: null,
      teacherName: null,
    },
    {
      id: "p2",
      number: 2,
      title: null,
      salary: "18000.00",
      teacherId: null,
      teacherName: null,
    },
  ],
  signatories: [],
};

const TEACHERS = [
  {
    teacherId: "t1",
    teacherName: "Tara Teacher",
    from: "Position 1 of the earlier MoU",
  },
  { teacherId: "t2", teacherName: "Meena Selvi", from: "not mapped" },
];

describe("MapTeachersDialog (spec 012 US3)", () => {
  beforeEach(() => authFetch.mockReset());

  it("lists each Teacher with the positions and their salaries and posts the choices", async () => {
    authFetch.mockResolvedValue(json({ remapped: 2 }));
    const onSaved = vi.fn();
    render(
      <MapTeachersDialog
        contract={CONTRACT}
        teachers={TEACHERS}
        onClose={vi.fn()}
        onSaved={onSaved}
      />,
    );
    const user = userEvent.setup();
    const dialog = await screen.findByRole("dialog");

    const tara = within(dialog).getByLabelText(/Tara Teacher/);
    expect(
      within(tara).getByRole("option", {
        name: /Position 1 \(Maths PGT\).*20,000\.00/,
      }),
    ).toBeInTheDocument();
    await user.selectOptions(tara, "p1");
    await user.selectOptions(
      within(dialog).getByLabelText(/Meena Selvi/),
      "p2",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Map Teachers" }),
    );

    expect(onSaved).toHaveBeenCalled();
    const [url, init] = authFetch.mock.calls[0];
    expect(String(url)).toBe(
      "/api/v1/school-contracts/contracts/c2/map-teachers",
    );
    expect(JSON.parse(String(init.body))).toEqual([
      { teacherId: "t1", positionId: "p1" },
      { teacherId: "t2", positionId: "p2" },
    ]);
  });

  it("does not let one position be chosen for two Teachers", async () => {
    render(
      <MapTeachersDialog
        contract={CONTRACT}
        teachers={TEACHERS}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    const user = userEvent.setup();
    const dialog = await screen.findByRole("dialog");

    await user.selectOptions(
      within(dialog).getByLabelText(/Tara Teacher/),
      "p1",
    );

    const other = within(dialog).getByLabelText(/Meena Selvi/);
    expect(
      within(other).getByRole("option", { name: /Position 1/ }),
    ).toBeDisabled();
    expect(
      within(other).getByRole("option", { name: /Position 2/ }),
    ).not.toBeDisabled();
  });

  it("asks for at least one choice and shows the server's refusal inline", async () => {
    authFetch.mockResolvedValue(
      json({ reason: "A position is already filled on those dates." }, 409),
    );
    render(
      <MapTeachersDialog
        contract={CONTRACT}
        teachers={TEACHERS}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    const user = userEvent.setup();
    const dialog = await screen.findByRole("dialog");

    await user.click(
      within(dialog).getByRole("button", { name: "Map Teachers" }),
    );
    expect(
      await within(dialog).findByText(
        "Choose a position for at least one Teacher.",
      ),
    ).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();

    await user.selectOptions(
      within(dialog).getByLabelText(/Tara Teacher/),
      "p2",
    );
    await user.click(
      within(dialog).getByRole("button", { name: "Map Teachers" }),
    );
    expect(
      await within(dialog).findByText(
        "A position is already filled on those dates.",
      ),
    ).toBeInTheDocument();
  });

  it("says there is nothing to map when no Teacher is listed", async () => {
    render(
      <MapTeachersDialog
        contract={CONTRACT}
        teachers={[]}
        onClose={vi.fn()}
        onSaved={vi.fn()}
      />,
    );
    const dialog = await screen.findByRole("dialog");

    expect(
      within(dialog).getByText("There are no Teachers to map at this School."),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Map Teachers" }),
    ).toBeDisabled();
  });
});
