import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BulkImportPlacesDialog } from "./BulkImportPlacesDialog";
import { parseRows } from "./bulkImportRows";
import type { ZoneSummary } from "./zonesApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

const ZONE: ZoneSummary = {
  id: "z1",
  name: "North Zone",
  version: 0,
  placeCount: 0,
  schoolCount: 0,
};

function jsonResponse(body: unknown, status = 200) {
  return { ok: status < 300, status, json: async () => body } as Response;
}

describe("parseRows", () => {
  it("reads name,pinCode lines, ignores blank lines and splits on the last comma", () => {
    expect(
      parseRows("Madurantakam,603306\n\n  Nagar, East ,603209 \r\nNoPin"),
    ).toEqual([
      { name: "Madurantakam", pinCode: "603306" },
      { name: "Nagar, East", pinCode: "603209" },
      { name: "NoPin", pinCode: "" },
    ]);
  });
});

describe("BulkImportPlacesDialog (User Story 8)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("submits the parsed rows and shows the per-row report", async () => {
    authFetch.mockResolvedValue(
      jsonResponse({
        added: 1,
        alreadyExisted: 1,
        rejected: 1,
        results: [
          { row: 1, outcome: "ADDED", reason: null },
          { row: 2, outcome: "ALREADY_EXISTS", reason: null },
          {
            row: 3,
            outcome: "REJECTED",
            reason: "PIN code must be six digits.",
          },
        ],
      }),
    );
    const onDone = vi.fn();
    const user = userEvent.setup();

    render(
      <BulkImportPlacesDialog zone={ZONE} onClose={vi.fn()} onDone={onDone} />,
    );
    await user.type(
      screen.getByLabelText(/places \(one per line/i),
      "Madurantakam,603306{Enter}Maraimalai,603209{Enter}Bad,12",
    );
    await user.click(screen.getByRole("button", { name: /^import$/i }));

    expect(await screen.findByRole("status")).toHaveTextContent(
      "1 added, 1 already existed, 1 rejected.",
    );
    expect(screen.getByText("Row 2: already exists")).toBeInTheDocument();
    expect(
      screen.getByText("Row 3: PIN code must be six digits."),
    ).toBeInTheDocument();
    const call = authFetch.mock.calls[0];
    expect(String(call[0])).toBe("/api/v1/places/bulk-import");
    expect(JSON.parse((call[1] as RequestInit).body as string)).toEqual({
      zoneId: "z1",
      rows: [
        { name: "Madurantakam", pinCode: "603306" },
        { name: "Maraimalai", pinCode: "603209" },
        { name: "Bad", pinCode: "12" },
      ],
    });
    await user.click(screen.getByRole("button", { name: /^done$/i }));
    expect(onDone).toHaveBeenCalled();
  });

  it("refuses an empty list and an oversize list before calling the server", async () => {
    const user = userEvent.setup();

    render(
      <BulkImportPlacesDialog zone={ZONE} onClose={vi.fn()} onDone={vi.fn()} />,
    );
    await user.click(screen.getByRole("button", { name: /^import$/i }));
    expect(await screen.findByRole("alert")).toHaveTextContent(
      /add at least one line/i,
    );

    const big = Array.from({ length: 5001 }, (_, i) => `P${i},100000`).join(
      "\n",
    );
    const field = screen.getByLabelText(/places \(one per line/i);
    // paste instead of typing 5,001 lines
    await user.click(field);
    await user.paste(big);
    await user.click(screen.getByRole("button", { name: /^import$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /more than 5000 rows/i,
    );
    expect(authFetch).not.toHaveBeenCalled();
  });

  it("shows the server's up-front rejection inline", async () => {
    authFetch.mockResolvedValue(
      jsonResponse({ reason: "Zone not found." }, 400),
    );
    const user = userEvent.setup();

    render(
      <BulkImportPlacesDialog zone={ZONE} onClose={vi.fn()} onDone={vi.fn()} />,
    );
    await user.type(
      screen.getByLabelText(/places \(one per line/i),
      "A,123456",
    );
    await user.click(screen.getByRole("button", { name: /^import$/i }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Zone not found.",
    );
  });
});
