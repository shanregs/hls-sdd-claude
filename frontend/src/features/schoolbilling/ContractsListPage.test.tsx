import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ContractsListPage } from "./ContractsListPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

function row(extra: object) {
  return {
    schoolId: "s1",
    schoolName: "Demo School One",
    zoneManagerName: "Manoj Manager",
    status: "ACTIVE",
    contractId: "c1",
    startsOn: "2026-10-01",
    endsOn: null,
    teacherCount: 4,
    filled: 3,
    vacant: 1,
    unmapped: 0,
    salaryMode: "SAME_FOR_ALL",
    signedOn: "2026-09-20",
    ...extra,
  };
}

function page(content: unknown[]) {
  return json({
    content,
    page: 0,
    size: 25,
    totalElements: content.length,
  });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/operations/school-contracts"]}>
      <Routes>
        <Route
          path="/operations/school-contracts"
          element={<ContractsListPage />}
        />
        <Route
          path="/operations/school-contracts/schools/:schoolId"
          element={<p>School page</p>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("ContractsListPage (spec 012 US4)", () => {
  beforeEach(() => authFetch.mockReset());

  it("shows each School's status, filled and vacant positions and dates", async () => {
    authFetch.mockResolvedValue(
      page([
        row({}),
        row({
          schoolId: "s2",
          schoolName: "Demo School Two",
          status: "MOU_PENDING",
          teacherCount: null,
          contractId: "c2",
          filled: 0,
          vacant: 0,
          unmapped: 2,
          signedOn: null,
          endsOn: "2026-12-31",
        }),
        row({
          schoolId: "s3",
          schoolName: "Holy Cross",
          status: "NONE",
          contractId: null,
          startsOn: null,
          teacherCount: null,
          filled: 0,
          vacant: 0,
        }),
      ]),
    );
    renderPage();

    const table = await screen.findByRole("table", {
      name: "School contracts",
    });
    expect(within(table).getByText("Demo School One")).toBeInTheDocument();
    expect(within(table).getByText("Active")).toBeInTheDocument();
    expect(within(table).getByText("3 filled, 1 vacant")).toBeInTheDocument();
    expect(within(table).getByText("01/10/2026 onwards")).toBeInTheDocument();
    expect(within(table).getByText("MoU pending")).toBeInTheDocument();
    expect(within(table).getByText("2 not mapped")).toBeInTheDocument();
    expect(
      within(table).getByText("01/10/2026 to 31/12/2026"),
    ).toBeInTheDocument();
    expect(within(table).getByText("No MoU yet")).toBeInTheDocument();
  });

  it("filters by status", async () => {
    authFetch.mockResolvedValue(page([row({})]));
    renderPage();
    const user = userEvent.setup();
    await screen.findByRole("table", { name: "School contracts" });

    await user.click(screen.getByLabelText("Status"));
    await user.click(
      await screen.findByRole("option", { name: "MoU pending" }),
    );

    await vi.waitFor(() =>
      expect(String(authFetch.mock.calls.at(-1)?.[0])).toContain(
        "status=MOU_PENDING",
      ),
    );
  });

  it("opens a School's contract page", async () => {
    authFetch.mockResolvedValue(page([row({})]));
    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", {
        name: "Open contract of Demo School One",
      }),
    );

    expect(await screen.findByText("School page")).toBeInTheDocument();
  });

  it("shows an empty state and an error alert", async () => {
    authFetch.mockResolvedValueOnce(page([]));
    const { unmount } = renderPage();
    expect(await screen.findByText(/No Schools match/)).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValueOnce(json({ reason: "Boom" }, 500));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("Boom");
  });
});
