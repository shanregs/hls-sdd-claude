import { render, screen } from "@testing-library/react";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MyProfilePage } from "./MyProfilePage";
import { AuthProvider, useAuth } from "../../auth/AuthContext";

function mockFetchOnce(status: number, body: unknown) {
  vi.stubGlobal(
    "fetch",
    vi.fn(() =>
      Promise.resolve({
        ok: status < 400,
        status,
        json: () => Promise.resolve(body),
      } as Response),
    ),
  );
}

function LogInWithFakeToken() {
  const { setTokens } = useAuth();
  useEffect(() => {
    setTokens({ accessToken: "fake-token", expiresIn: 900 });
  }, [setTokens]);
  return null;
}

function renderAuthenticated() {
  return render(
    <AuthProvider>
      <LogInWithFakeToken />
      <MyProfilePage />
    </AuthProvider>,
  );
}

describe("MyProfilePage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("renders the caller's own profile (User Story 4)", async () => {
    mockFetchOnce(200, {
      id: "teacher-1",
      name: "Priya Sharma",
      phone: "+919811111111",
      hlsOfferedSalary: 17000,
      status: "ACTIVE",
      createdAt: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    expect(await screen.findByTestId("my-profile-name")).toHaveTextContent(
      "Priya Sharma",
    );
    expect(screen.getByTestId("my-profile-status")).toHaveTextContent("ACTIVE");
  });

  it("offers no edit control anywhere on the page (User Story 4, AC2)", async () => {
    mockFetchOnce(200, {
      id: "teacher-1",
      name: "Priya Sharma",
      phone: "+919811111111",
      hlsOfferedSalary: 17000,
      status: "ACTIVE",
      createdAt: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    await screen.findByTestId("my-profile-name");
    expect(screen.queryAllByRole("button")).toHaveLength(0);
    expect(screen.queryAllByRole("textbox")).toHaveLength(0);
  });

  it("shows an error when the caller has no linked teacher id", async () => {
    mockFetchOnce(403, {});
    renderAuthenticated();

    expect(await screen.findByTestId("my-profile-error")).toBeInTheDocument();
  });
});
