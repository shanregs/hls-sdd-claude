import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { MouApprovalPanel } from "./MouApprovalPanel";
import { CONTRACT, WITH_DOCUMENTS, approval, json } from "./mouTestData";
import type { MouAction } from "./mouApprovalApi";

const authFetch = vi.fn();

vi.mock("../../auth/useAuth", () => ({
  useAuth: () => ({ authFetch }),
}));

function renderPanel() {
  return render(
    <MouApprovalPanel contract={CONTRACT} onTermsChanged={() => undefined} />,
  );
}

describe("MouApprovalPanel documents (spec 031 US1)", () => {
  beforeEach(() => {
    authFetch.mockReset();
  });

  it("shows upload and remove to a caller who may edit", async () => {
    const actions: MouAction[] = ["UPLOAD", "REMOVE_DOCUMENT", "SUBMIT"];
    authFetch.mockResolvedValue(json(approval(actions, WITH_DOCUMENTS)));
    renderPanel();

    expect(
      await screen.findByText(/Version 2: mou-corrected.pdf/),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Signed MoU document")).toBeInTheDocument();
    expect(screen.getByText("Upload a new version")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove version 2" })).toBeInTheDocument();
    // the removed version has no download or remove, only its record
    expect(screen.queryByRole("button", { name: "Download version 1" })).toBeNull();
    expect(screen.getByText(/Removed by Divya Director/)).toBeInTheDocument();
    expect(screen.getByText(/wrong file/)).toBeInTheDocument();
    expect(screen.getByText("Current")).toBeInTheDocument();
  });

  it("shows only the download to a Zone Manager", async () => {
    authFetch.mockResolvedValue(
      json(approval(["APPROVE", "REJECT", "AMEND_TERMS"], WITH_DOCUMENTS)),
    );
    renderPanel();

    await screen.findByText(/Version 2/);
    expect(screen.getByRole("button", { name: "Download version 2" })).toBeInTheDocument();
    expect(screen.queryByLabelText("Signed MoU document")).toBeNull();
    expect(screen.queryByRole("button", { name: /Remove version/ })).toBeNull();
  });

  it("shows nothing to change for Teacher or System (no actions, no documents)", async () => {
    authFetch.mockResolvedValue(json(approval([])));
    renderPanel();

    expect(await screen.findByText("No signed document attached yet.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Signed MoU document")).toBeNull();
    expect(screen.queryByRole("button", { name: "Send for approval" })).toBeNull();
  });

  it("refuses a wrong type before anything is sent", async () => {
    authFetch.mockResolvedValue(json(approval(["UPLOAD"])));
    renderPanel();
    const user = userEvent.setup({ applyAccept: false });
    const input = await screen.findByLabelText("Signed MoU document");

    await user.upload(input, new File(["x"], "mou.docx"));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Only PDF, JPG or PNG files can be uploaded.",
    );
    expect(authFetch).toHaveBeenCalledTimes(1);
  });

  it("uploads a PDF and shows the new version", async () => {
    authFetch.mockResolvedValueOnce(json(approval(["UPLOAD"])));
    authFetch.mockResolvedValueOnce(
      json(approval(["UPLOAD", "REMOVE_DOCUMENT"], WITH_DOCUMENTS), 201),
    );
    renderPanel();
    const user = userEvent.setup();
    const input = await screen.findByLabelText("Signed MoU document");

    await user.upload(input, new File(["%PDF-1.4"], "mou.pdf", { type: "application/pdf" }));

    expect(await screen.findByText(/Version 2: mou-corrected.pdf/)).toBeInTheDocument();
    const call = authFetch.mock.calls[1];
    expect(String(call[0])).toContain("/contracts/c1/documents");
    expect(call[1].method).toBe("POST");
    expect(screen.getByText("The document was uploaded.")).toBeInTheDocument();
  });

  it("warns that a new version resets an approved MoU", async () => {
    authFetch.mockResolvedValue(
      json(approval(["UPLOAD"], { ...WITH_DOCUMENTS, state: "APPROVED" })),
    );
    renderPanel();

    expect(
      await screen.findByText(/a new version sends it back for approval/),
    ).toBeInTheDocument();
  });

  it("removes a version only with a reason", async () => {
    authFetch.mockResolvedValueOnce(
      json(approval(["UPLOAD", "REMOVE_DOCUMENT"], WITH_DOCUMENTS)),
    );
    authFetch.mockResolvedValueOnce(json(approval(["UPLOAD"])));
    renderPanel();
    const user = userEvent.setup();

    await user.click(await screen.findByRole("button", { name: "Remove version 2" }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Remove version" }));
    expect(within(dialog).getByText("Give a reason.")).toBeInTheDocument();
    expect(authFetch).toHaveBeenCalledTimes(1);

    await user.type(within(dialog).getByLabelText("Reason"), "blurred scan");
    await user.click(within(dialog).getByRole("button", { name: "Remove version" }));

    expect(await screen.findByText("The version was removed.")).toBeInTheDocument();
    const call = authFetch.mock.calls[1];
    expect(String(call[0])).toContain("/documents/d2/remove");
    expect(JSON.parse(call[1].body)).toEqual({ reason: "blurred scan" });
  });

  it("shows a load error", async () => {
    authFetch.mockResolvedValue(json({ reason: "nope" }, 500));
    renderPanel();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
