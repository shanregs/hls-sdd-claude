import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useEffect } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TeacherProfilesPage } from "./TeacherProfilesPage";
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
      <TeacherProfilesPage />
    </AuthProvider>,
  );
}

describe("TeacherProfilesPage", () => {
  afterEach(() => vi.unstubAllGlobals());

  it("creates a profile (User Story 1)", async () => {
    mockFetchOnce(200, {
      id: "teacher-1",
      name: "Priya Sharma",
      phone: "+919811111111",
      hlsOfferedSalary: 17000,
      status: "IN_TRAINING",
      createdAt: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("teacher-name-input"),
      "Priya Sharma",
    );
    await userEvent.type(
      screen.getByTestId("teacher-phone-input"),
      "+919811111111",
    );
    await userEvent.type(screen.getByTestId("teacher-salary-input"), "17000");
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));

    expect(
      await screen.findByTestId("create-teacher-message"),
    ).toHaveTextContent("teacher-1");
  });

  it("shows a validation error when a required field is missing (User Story 1, AC2)", async () => {
    mockFetchOnce(400, {
      message: "Missing or invalid field(s): hlsOfferedSalary",
    });
    renderAuthenticated();

    await userEvent.type(screen.getByTestId("teacher-name-input"), "No Salary");
    await userEvent.type(
      screen.getByTestId("teacher-phone-input"),
      "+919800000000",
    );
    await userEvent.click(screen.getByRole("button", { name: /^create$/i }));

    expect(await screen.findByTestId("create-teacher-error")).toHaveTextContent(
      "hlsOfferedSalary",
    );
  });

  it("updates a teacher's contact email (User Story 2)", async () => {
    mockFetchOnce(200, {
      id: "teacher-1",
      name: "Priya Sharma",
      phone: "+919811111111",
      email: "priya.new@example.com",
      hlsOfferedSalary: 17000,
      status: "IN_TRAINING",
      createdAt: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("update-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.type(
      screen.getByTestId("update-email-input"),
      "priya.new@example.com",
    );
    await userEvent.click(screen.getByRole("button", { name: /^update$/i }));

    expect(
      await screen.findByTestId("update-teacher-message"),
    ).toHaveTextContent("priya.new@example.com");
  });

  it("changes a teacher's status (User Story 2)", async () => {
    mockFetchOnce(200, {
      id: "teacher-1",
      name: "Priya Sharma",
      phone: "+919811111111",
      hlsOfferedSalary: 17000,
      status: "ACTIVE",
      createdAt: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("status-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(
      screen.getByRole("button", { name: /change status/i }),
    );

    expect(await screen.findByTestId("status-message")).toHaveTextContent(
      "ACTIVE",
    );
  });

  it("a Director views any profile (User Story 3)", async () => {
    mockFetchOnce(200, {
      id: "teacher-1",
      name: "Priya Sharma",
      phone: "+919811111111",
      hlsOfferedSalary: 17000,
      status: "ACTIVE",
      createdAt: "2026-09-22T00:00:00Z",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("view-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(
      within(screen.getByTestId("view-teacher-form")).getByRole("button", {
        name: /look up/i,
      }),
    );

    expect(await screen.findByTestId("viewed-profile")).toHaveTextContent(
      "Priya Sharma",
    );
  });

  it("shows an error when a Manager views a non-assigned teacher's profile (User Story 3)", async () => {
    mockFetchOnce(403, {});
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("view-teacher-id-input"),
      "teacher-2",
    );
    await userEvent.click(
      within(screen.getByTestId("view-teacher-form")).getByRole("button", {
        name: /look up/i,
      }),
    );

    expect(await screen.findByTestId("view-teacher-error")).toHaveTextContent(
      "not allowed",
    );
  });

  it("records a salary change (specs/009 User Story 2)", async () => {
    mockFetchOnce(200, {
      id: "entry-1",
      teacherId: "teacher-1",
      amount: 19000,
      effectiveFrom: "2026-10-01",
    });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("salary-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.type(screen.getByTestId("salary-amount-input"), "19000");
    await userEvent.click(screen.getByRole("button", { name: /^record$/i }));

    expect(
      await screen.findByTestId("record-salary-message"),
    ).toHaveTextContent("19000");
  });

  it("looks up the salary as of a past date (specs/009 User Story 4)", async () => {
    mockFetchOnce(200, { state: "RECORDED", amount: 17000 });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("lookup-salary-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(
      within(screen.getByTestId("salary-lookup-form")).getByRole("button", {
        name: /look up/i,
      }),
    );

    expect(await screen.findByTestId("salary-lookup-result")).toHaveTextContent(
      "17000",
    );
  });

  it("shows a clear not-yet-recorded result for a date before the first entry (specs/009 User Story 4, AC2)", async () => {
    mockFetchOnce(200, { state: "NOT_YET_RECORDED" });
    renderAuthenticated();

    await userEvent.type(
      screen.getByTestId("lookup-salary-teacher-id-input"),
      "teacher-1",
    );
    await userEvent.click(
      within(screen.getByTestId("salary-lookup-form")).getByRole("button", {
        name: /look up/i,
      }),
    );

    expect(
      await screen.findByTestId("salary-lookup-not-yet-recorded"),
    ).toBeInTheDocument();
  });
});
