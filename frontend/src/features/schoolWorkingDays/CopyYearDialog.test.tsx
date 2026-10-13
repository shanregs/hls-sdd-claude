import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CopyYearDialog } from "./CopyYearDialog";
import type { SchoolOption } from "./schoolWorkingDaysApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const SCHOOLS: SchoolOption[] = [
  {
    id: "s1",
    name: "Greenfield School",
    zoneId: "z1",
    zoneName: "North",
    active: true,
  },
];

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("CopyYearDialog (spec 037 US5)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("copies a year and lists the created months and the months not copied with their reasons", async () => {
    const user = userEvent.setup();
    const onCopied = vi.fn();
    authFetch.mockResolvedValue(
      jsonResponse({
        created: [
          { month: 1, workingDays: 26 },
          { month: 12, workingDays: 24.5 },
        ],
        skipped: [
          {
            month: 2,
            workingDays: 29,
            reason: "EXCEEDS_DAYS",
            daysInMonth: 28,
          },
          { month: 3, workingDays: 25, reason: "EXISTS" },
        ],
      }),
    );
    render(
      <CopyYearDialog
        schools={SCHOOLS}
        year={2026}
        schoolId="s1"
        onClose={() => {}}
        onCopied={onCopied}
      />,
    );

    expect(screen.getByLabelText(/Copy from year/)).toHaveValue("2026");
    expect(screen.getByLabelText(/Copy to year/)).toHaveValue("2027");
    await user.click(screen.getByRole("button", { name: "Copy" }));

    expect(
      await screen.findByText(/Copied 2 months: January 26, December 24\.5\./),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /February: 29 working days does not fit the 28 days; enter it yourself/,
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("March: already has a figure")).toBeInTheDocument();
    expect(onCopied).toHaveBeenCalledTimes(1);
    expect(authFetch).toHaveBeenCalledWith(
      "/api/v1/school-working-days/copy",
      expect.objectContaining({
        method: "POST",
        body: JSON.stringify({ schoolId: "s1", fromYear: 2026, toYear: 2027 }),
      }),
    );
  });

  it("names a missing School or year and shows the server's refusal", async () => {
    const user = userEvent.setup();
    authFetch.mockResolvedValue(
      jsonResponse({ reason: "Year must be 2022 to 2032." }, 400),
    );
    render(
      <CopyYearDialog
        schools={SCHOOLS}
        year={2026}
        schoolId=""
        onClose={() => {}}
        onCopied={() => {}}
      />,
    );

    await user.click(screen.getByRole("button", { name: "Copy" }));
    expect(await screen.findByText("Choose a School.")).toBeInTheDocument();
    expect(authFetch).not.toHaveBeenCalled();

    await user.click(screen.getByLabelText(/^School/));
    await user.click(
      await screen.findByRole("option", { name: "Greenfield School" }),
    );
    await user.click(screen.getByRole("button", { name: "Copy" }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Year must be 2022 to 2032.",
    );
  });
});
