import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MouApprovalsPage } from "./MouApprovalsPage";
import { json } from "./mouTestData";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function row(extra: object) {
  return {
    contractId: "c1",
    schoolId: "s1",
    schoolName: "Demo School One",
    zoneManagerName: "Manoj Manager",
    round: 1,
    submittedBy: { id: "u1", name: "Anita Admin" },
    submittedAt: "2026-10-02T05:30:00Z",
    documentState: "ATTACHED",
    teacherCount: 4,
    salaryMode: "SAME_FOR_ALL",
    rate: "15000.00",
    startsOn: "2026-10-01",
    endsOn: null,
    approvalVersion: 3,
    ...extra,
  };
}

function page(content: unknown[]) {
  return json({ content, page: 0, size: 25, totalElements: content.length });
}

function renderPage() {
  return render(
    <MemoryRouter initialEntries={["/operations/school-contracts/approvals"]}>
      <Routes>
        <Route
          path="/operations/school-contracts/approvals"
          element={<MouApprovalsPage />}
        />
        <Route
          path="/operations/school-contracts/schools/:schoolId"
          element={<p>School page</p>}
        />
      </Routes>
    </MemoryRouter>,
  );
}

describe("MouApprovalsPage (spec 031 US3)", () => {
  beforeEach(() => authFetch.mockReset());

  it("lists the MoUs waiting for a decision", async () => {
    authFetch.mockResolvedValue(
      page([
        row({}),
        row({
          contractId: "c2",
          schoolId: "s2",
          schoolName: "St Mary",
          zoneManagerName: null,
          documentState: "NONE",
          round: 2,
          teacherCount: 1,
        }),
      ]),
    );
    renderPage();

    const table = await screen.findByRole("table", {
      name: "MoUs waiting for approval",
    });
    expect(within(table).getByText("Demo School One")).toBeInTheDocument();
    expect(within(table).getAllByText("02/10/2026 11:00 by Anita Admin")).toHaveLength(2);
    expect(within(table).getByText("Attached")).toBeInTheDocument();
    expect(within(table).getByText("No document")).toBeInTheDocument();
    expect(within(table).getByText("None")).toBeInTheDocument();
    expect(String(authFetch.mock.calls[0][0])).toContain("/approvals/pending");
  });

  it("opens the School page of a row", async () => {
    authFetch.mockResolvedValue(page([row({})]));
    renderPage();
    const user = userEvent.setup();

    await user.click(
      await screen.findByRole("button", { name: "Open MoU of Demo School One" }),
    );

    expect(await screen.findByText("School page")).toBeInTheDocument();
  });

  it("shows the empty state and an error", async () => {
    authFetch.mockResolvedValueOnce(page([]));
    const { unmount } = renderPage();
    expect(await screen.findByText("No MoU is waiting for approval.")).toBeInTheDocument();
    unmount();

    authFetch.mockResolvedValueOnce(json({ reason: "Boom" }, 500));
    renderPage();
    expect(await screen.findByRole("alert")).toHaveTextContent("Boom");
  });

  it("shows a loading message first", () => {
    authFetch.mockReturnValue(new Promise(() => undefined));
    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading approvals...");
  });
});
