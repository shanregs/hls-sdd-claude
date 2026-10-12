import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MySubstitutionsPage } from "./MySubstitutionsPage";
import { SubstitutionDetailPage } from "./SubstitutionDetailPage";
import { SubstitutionsPage } from "./SubstitutionsPage";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function json(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

const REQ = {
  id: "r1",
  teacherId: "t1",
  teacherName: "Tara Teacher",
  schoolId: "s1",
  schoolName: "Demo School One",
  dutyDate: "2026-10-05",
  dayValue: 1,
  kind: "ABSENT",
  status: "OPEN",
  substituteTeacherId: null,
  substituteName: null,
  substituteNowUnavailable: false,
  coverName: null,
  informed: null,
  principalContact: "Mrs. Rani",
  note: null,
  version: 0,
};

describe("SubstitutionsPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("lists the requirements with status and a link to each", async () => {
    authFetch.mockResolvedValue(json({ content: [REQ], page: 0, size: 25, totalElements: 1 }));
    render(
      <MemoryRouter>
        <SubstitutionsPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("Tara Teacher")).toBeInTheDocument();
    expect(screen.getByText("Open")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "05/10/2026" })).toHaveAttribute(
      "href",
      "/operations/substitutions/r1",
    );
  });

  it("says so when there is nothing to do", async () => {
    authFetch.mockResolvedValue(json({ content: [], page: 0, size: 25, totalElements: 0 }));
    render(
      <MemoryRouter>
        <SubstitutionsPage />
      </MemoryRouter>,
    );
    expect(await screen.findByText("No substitution requirements.")).toBeInTheDocument();
  });
});

describe("SubstitutionDetailPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  function renderDetail() {
    return render(
      <MemoryRouter initialEntries={["/operations/substitutions/r1"]}>
        <Routes>
          <Route path="/operations/substitutions/:id" element={<SubstitutionDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it("shows who is free, disables the unavailable, and assigns the chosen Teacher", async () => {
    authFetch.mockImplementation(async (url: string, init?: RequestInit) => {
      if (init?.method === "POST") return json({ ...REQ, status: "ASSIGNED", version: 1 });
      if (url.endsWith("/candidates")) {
        return json([
          { teacherId: "b1", name: "Bina Bench", available: true, reason: null, current: false },
          { teacherId: "b2", name: "Busy Bee", available: false, reason: "Teaching at Demo School Two that day", current: false },
        ]);
      }
      return json({ requirement: REQ, history: [] });
    });
    renderDetail();
    expect(await screen.findByText("Bina Bench")).toBeInTheDocument();
    expect(screen.getByText(/Not available: Teaching at Demo School Two/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Assign Busy Bee" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Assign Bina Bench" }));
    const post = authFetch.mock.calls.find((c) => c[1]?.method === "POST");
    expect(post?.[0]).toBe("/api/v1/substitutions/r1/assign");
    expect(JSON.parse(post?.[1].body)).toEqual({ substituteTeacherId: "b1", version: 0 });
  });

  it("offers School informed only once there is an outcome, prefilled with the Principal", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url.endsWith("/candidates")) return json([]);
      return json({
        requirement: { ...REQ, status: "MANAGER_COVER", coverName: "Manoj Manager" },
        history: [],
      });
    });
    renderDetail();
    expect(await screen.findByText("Covered by Manoj Manager")).toBeInTheDocument();
    expect(screen.getByLabelText("Who was told")).toHaveValue("Mrs. Rani");
    expect(screen.getByRole("button", { name: "Record School informed" })).toBeEnabled();
  });

  it("shows a cancelled requirement without actions", async () => {
    authFetch.mockImplementation(async (url: string) => {
      if (url.endsWith("/candidates")) return json([]);
      return json({ requirement: { ...REQ, status: "CANCELLED" }, history: [] });
    });
    renderDetail();
    expect(await screen.findByText(/cancelled because the absence was withdrawn/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Assign/ })).not.toBeInTheDocument();
  });
});

describe("MySubstitutionsPage", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("lists only the days I cover, never the absent Teacher", async () => {
    authFetch.mockResolvedValue(
      json([{ id: "r1", schoolId: "s1", schoolName: "Demo School One", dutyDate: "2026-10-05", dayValue: 0.5, status: "ASSIGNED" }]),
    );
    render(<MySubstitutionsPage />);
    expect(await screen.findByText("Demo School One")).toBeInTheDocument();
    expect(screen.getByText("Half day")).toBeInTheDocument();
    expect(screen.queryByText("Tara Teacher")).not.toBeInTheDocument();
  });

  it("explains an empty list", async () => {
    authFetch.mockResolvedValue(json([]));
    render(<MySubstitutionsPage />);
    expect(await screen.findByText("You have no substitution days.")).toBeInTheDocument();
  });
});
