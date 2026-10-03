import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MyTeacherProfile } from "./MyTeacherProfile";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("MyTeacherProfile (User Story 7)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows the teacher's own details and current school, labelled interim, with no salary", async () => {
    authFetch.mockResolvedValue(
      jsonResponse({
        id: "t1",
        name: "Tara Teacher",
        phone: "9800000004",
        email: "tara@example.com",
        address: "1 Main Road",
        status: "ACTIVE",
        statusEffectiveOn: "2026-04-01",
        school: { id: "s1", name: "St Mary's" },
      }),
    );

    render(<MyTeacherProfile />);

    expect(await screen.findByText("Tara Teacher")).toBeInTheDocument();
    expect(screen.getByText(/9800000004/)).toBeInTheDocument();
    expect(screen.getByText(/tara@example.com/)).toBeInTheDocument();
    expect(
      screen.getByText(/current school \(interim placement\): st mary's/i),
    ).toBeInTheDocument();
    expect(screen.getByText(/status since 01\/04\/2026/i)).toBeInTheDocument();
    expect(screen.queryByText(/salary/i)).not.toBeInTheDocument();
    expect(String(authFetch.mock.calls[0][0])).toBe("/api/v1/teachers/me");
  });

  it("explains that the profile is not set up yet instead of showing an error", async () => {
    authFetch.mockResolvedValue(
      jsonResponse({ reason: "Your profile has not been set up yet." }, 404),
    );

    render(<MyTeacherProfile />);

    expect(
      await screen.findByText(/your profile has not been set up yet/i),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows an error alert when loading fails", async () => {
    authFetch.mockResolvedValue(jsonResponse({}, 500));

    render(<MyTeacherProfile />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /could not load your profile/i,
    );
  });
});
