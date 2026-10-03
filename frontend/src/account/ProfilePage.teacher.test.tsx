import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ProfilePage } from "./ProfilePage";

const authFetch = vi.fn();
let roles = ["TEACHER"];

vi.mock("../auth/useAuth", () => ({
  useAuth: () => ({
    user: { id: "u1", displayName: "Tara Teacher", roles },
    authFetch,
  }),
}));

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("ProfilePage Teacher block (spec 005 User Story 7)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    roles = ["TEACHER"];
  });

  it("shows the teacher's own profile above their sessions", async () => {
    authFetch.mockImplementation(async (url: string) =>
      url === "/api/v1/teachers/me"
        ? jsonResponse({
            id: "t1",
            name: "Tara Teacher",
            phone: "9800000004",
            email: null,
            address: null,
            status: "ACTIVE",
            statusEffectiveOn: "2026-04-01",
            school: { id: "s1", name: "St Mary's" },
          })
        : jsonResponse([]),
    );

    render(<ProfilePage />);

    expect(
      await screen.findByText(/current school \(interim placement\)/i),
    ).toBeInTheDocument();
    expect(screen.getByText("My sessions")).toBeInTheDocument();
  });

  it("explains a missing record without an error", async () => {
    authFetch.mockImplementation(async (url: string) =>
      url === "/api/v1/teachers/me"
        ? jsonResponse({ reason: "Your profile has not been set up yet." }, 404)
        : jsonResponse([]),
    );

    render(<ProfilePage />);

    expect(
      await screen.findByText(/your profile has not been set up yet/i),
    ).toBeInTheDocument();
  });

  it("does not ask for a teacher profile when the user is not a Teacher", async () => {
    roles = ["MANAGER"];
    authFetch.mockResolvedValue(jsonResponse([]));

    render(<ProfilePage />);
    await screen.findByText(/no active sessions/i);

    expect(authFetch.mock.calls.map((c) => String(c[0]))).not.toContain(
      "/api/v1/teachers/me",
    );
  });
});
