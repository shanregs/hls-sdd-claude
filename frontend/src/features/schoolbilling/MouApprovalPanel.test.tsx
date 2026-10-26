import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MouApprovalPanel } from "./MouApprovalPanel";
import { CONTRACT, approval, json } from "./mouTestData";

const authFetch = vi.fn();
const onTermsChanged = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function renderPanel() {
  return render(
    <MouApprovalPanel contract={CONTRACT} onTermsChanged={onTermsChanged} />,
  );
}

const PERSON = { id: "u1", name: "Divya Director" };

describe("MouApprovalPanel approval (spec 031 US2)", () => {
  beforeEach(() => {
    authFetch.mockReset();
    onTermsChanged.mockReset();
  });

  it("offers Send for approval to Admin and never Approve or Reject", async () => {
    authFetch.mockResolvedValue(json(approval(["UPLOAD", "SUBMIT"])));
    renderPanel();

    expect(await screen.findByRole("button", { name: "Send for approval" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Reject" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Correct the terms" })).toBeNull();
  });

  it("offers Approve, Reject and Correct the terms to an approver of a pending MoU", async () => {
    authFetch.mockResolvedValue(
      json(
        approval(["APPROVE", "REJECT", "AMEND_TERMS"], {
          state: "PENDING",
          round: 1,
          submittedBy: PERSON,
          submittedAt: "2026-10-02T05:30:00Z",
        }),
      ),
    );
    renderPanel();

    expect(await screen.findByRole("button", { name: "Approve" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reject" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Correct the terms" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Send for approval" })).toBeNull();
    expect(screen.getByText(/Waiting for approval since 02\/10\/2026 11:00/)).toBeInTheDocument();
  });

  it("shows nothing to do for Teacher and System", async () => {
    authFetch.mockResolvedValue(json(approval([], { state: "PENDING" })));
    renderPanel();

    await screen.findByText("Pending approval");
    expect(screen.queryByRole("button", { name: /Approve|Reject|Send for approval/ })).toBeNull();
  });

  it("sends for approval after a confirmation", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["SUBMIT"])));
    authFetch.mockResolvedValueOnce(json(approval([], { state: "PENDING", round: 1 })));
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Send for approval" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByText(/No signed document is attached yet/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Send for approval" }));

    expect(await screen.findByText("The MoU was sent for approval.")).toBeInTheDocument();
    const call = authFetch.mock.calls[1];
    expect(String(call[0])).toContain("/contracts/c1/submit");
    expect(JSON.parse(call[1].body)).toEqual({ version: 3 });
  });

  it("requires a reason to reject and sends it with the version", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["APPROVE", "REJECT"], { state: "PENDING" })));
    authFetch.mockResolvedValueOnce(
      json(approval([], { state: "REJECTED", decidedBy: PERSON, decisionReason: "Rate differs" })),
    );
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Reject" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));
    expect(within(dialog).getByText("Give a reason.")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledTimes(1);

    await user.type(within(dialog).getByLabelText("Reason"), "Rate differs");
    await user.click(within(dialog).getByRole("button", { name: "Reject" }));

    expect(await screen.findByText(/Rejected by Divya Director/)).toBeInTheDocument();
    expect(screen.getByText(/Rate differs/)).toBeInTheDocument();
    const call = authFetch.mock.calls[1];
    expect(String(call[0])).toContain("/contracts/c1/reject");
    expect(JSON.parse(call[1].body)).toEqual({ version: 3, reason: "Rate differs" });
  });

  it("approves and then shows the approver", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["APPROVE", "REJECT"], { state: "PENDING" })));
    authFetch.mockResolvedValueOnce(
      json(
        approval([], {
          state: "APPROVED_WITHOUT_DOCUMENT",
          decidedBy: PERSON,
          decidedAt: "2026-10-02T05:30:00Z",
          selfDecided: true,
        }),
      ),
    );
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Approve" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Approve" }));

    expect(await screen.findByText("The MoU was approved.")).toBeInTheDocument();
    expect(screen.getByText(/Approved by Divya Director on 02\/10\/2026 11:00/)).toBeInTheDocument();
    expect(screen.getByText(/the person who submitted it/)).toBeInTheDocument();
    expect(screen.getByText("Approved without document")).toBeInTheDocument();
  });

  it("tells the user when someone else already decided and reloads", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["APPROVE", "REJECT"], { state: "PENDING" })));
    authFetch.mockResolvedValueOnce(
      json({ reason: "This MoU was already decided: approved by Manoj on 02/10/2026 11:00." }, 409),
    );
    authFetch.mockResolvedValueOnce(
      json(approval([], { state: "APPROVED", decidedBy: PERSON, decidedAt: "2026-10-02T05:30:00Z" })),
    );
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Approve" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Approve" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("already decided: approved by Manoj");
    expect(await screen.findByText(/Approved by Divya Director/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Approve" })).toBeNull();
  });

  it("sends only the changed fields when correcting the terms", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["AMEND_TERMS"], { state: "PENDING" })));
    authFetch.mockResolvedValueOnce(
      json({ approval: approval(["AMEND_TERMS"], { state: "PENDING", version: 4 }), contract: CONTRACT }),
    );
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Correct the terms" }));
    const dialog = await screen.findByRole("dialog");
    const rate = within(dialog).getByLabelText("Monthly salary for each Teacher");
    await user.clear(rate);
    await user.type(rate, "16000");
    await user.click(within(dialog).getByRole("button", { name: "Save corrections" }));

    expect(await screen.findByText("The terms were corrected.")).toBeInTheDocument();
    const call = authFetch.mock.calls[1];
    expect(String(call[0])).toContain("/contracts/c1/terms");
    expect(call[1].method).toBe("PATCH");
    expect(JSON.parse(call[1].body)).toEqual({ version: 3, rate: "16000" });
    expect(onTermsChanged).toHaveBeenCalled();
  });

  it("refuses an amount with too many decimals before sending", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["AMEND_TERMS"], { state: "PENDING" })));
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Correct the terms" }));
    const dialog = await screen.findByRole("dialog");
    const rate = within(dialog).getByLabelText("Monthly salary for each Teacher");
    await user.clear(rate);
    await user.type(rate, "10.123");
    await user.click(within(dialog).getByRole("button", { name: "Save corrections" }));

    expect(within(dialog).getByRole("alert")).toHaveTextContent("at most two decimals");
    expect(authFetch).toHaveBeenCalledTimes(1);
  });

  it("shows the history oldest first with dates as DD/MM/YYYY HH:mm", async () => {
    authFetch.mockResolvedValue(
      json(
        approval([], {
          state: "REJECTED",
          decidedBy: PERSON,
          decisionReason: "x",
          history: [
            {
              at: "2026-10-01T04:00:00Z",
              action: "SUBMITTED",
              round: 1,
              actor: PERSON,
              actorRole: "DIRECTOR",
              note: null,
              detail: null,
              documentVersion: null,
            },
            {
              at: "2026-10-01T05:00:00Z",
              action: "REJECTED",
              round: 1,
              actor: PERSON,
              actorRole: "DIRECTOR",
              note: "Rate differs",
              detail: null,
              documentVersion: null,
            },
          ],
        }),
      ),
    );
    renderPanel();

    const history = await screen.findByRole("region", { name: "Approval history" });
    const items = within(history).getAllByRole("listitem");
    expect(items[0]).toHaveTextContent("01/10/2026 09:30 - Submitted for approval by Divya Director");
    expect(items[1]).toHaveTextContent("Rejected by Divya Director (director) - the person who submitted it: Rate differs");
  });
});
