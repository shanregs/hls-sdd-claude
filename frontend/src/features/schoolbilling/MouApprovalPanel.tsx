import { useCallback, useEffect, useState } from "react";
import { Alert, Button, Divider, Stack, Typography } from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { AmendTermsDialog } from "./AmendTermsDialog";
import { ApprovalHistory } from "./ApprovalHistory";
import { ApprovalStatusChip } from "./ApprovalStatusChip";
import { ApproveDialog, RejectDialog, SubmitDialog } from "./DecisionDialogs";
import {
  approveMou,
  getApproval,
  rejectMou,
  submitMou,
  type AmendResult,
  type Approval,
  type MouAction,
} from "./mouApprovalApi";
import { MouDocumentList } from "./MouDocumentList";
import { formatInstant } from "./mouFormat";
import type { ApiResult } from "../common/masterDataApi";
import type { ContractRow } from "./schoolContractsApi";
import { RemoveDocumentDialog, UploadMouDocument } from "./UploadMouDocument";

type Dialog =
  | { kind: "none" }
  | { kind: "submit" }
  | { kind: "approve" }
  | { kind: "reject" }
  | { kind: "amend" }
  | { kind: "remove"; documentId: string };

/**
 * The documents and approval of one MoU (spec 031): its state, the versions of the signed document, the buttons the server
 * allows for this caller, and the history. Buttons come from the server list of actions, never from the role.
 */
export function MouApprovalPanel({
  contract,
  onTermsChanged,
}: {
  contract: ContractRow;
  /** The approver corrected terms: the page reloads the contract. */
  onTermsChanged: () => void;
}) {
  const { authFetch } = useAuth();
  const [approval, setApproval] = useState<Approval | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog>({ kind: "none" });
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getApproval(authFetch, contract.id);
    if (result.ok) {
      setApproval(result.data);
      setLoadError(null);
    } else {
      setLoadError(result.reason);
    }
  }, [authFetch, contract.id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  if (loadError) {
    return (
      <Alert severity="error" role="alert">
        {loadError}
      </Alert>
    );
  }
  if (!approval) {
    return <Typography role="status">Loading approval...</Typography>;
  }

  const can = (action: MouAction) => approval.actions.includes(action);
  const hasDocument = approval.document.state === "ATTACHED";

  /** Takes the new approval of a successful call; on a refusal shows why and reloads (a 409 means someone else acted). */
  const apply = (result: ApiResult<Approval>, done?: string): string | null => {
    if (result.ok) {
      setApproval(result.data);
      setError(null);
      setMessage(done ?? null);
      setDialog({ kind: "none" });
      return null;
    }
    if (result.status === 409) {
      void load();
    }
    return result.reason;
  };

  const run = async (call: () => Promise<ApiResult<Approval>>, done: string) => {
    const reason = apply(await call(), done);
    if (reason) {
      setDialog({ kind: "none" });
      setMessage(null);
      setError(reason);
    }
  };

  const amended = (result: AmendResult) => {
    setApproval(result.approval);
    setDialog({ kind: "none" });
    setError(null);
    setMessage("The terms were corrected.");
    onTermsChanged();
  };

  return (
    <Stack
      spacing={1.5}
      component="section"
      aria-label="Documents and approval"
    >
      <Divider />
      <Stack
        direction="row"
        spacing={1}
        sx={{ alignItems: "center", flexWrap: "wrap" }}
      >
        <Typography variant="subtitle1" component="h3">
          Signed document and approval
        </Typography>
        <ApprovalStatusChip state={approval.state} />
        {approval.round > 0 && (
          <Typography variant="caption">Round {approval.round}</Typography>
        )}
      </Stack>

      {approval.state === "REJECTED" && approval.decisionReason && (
        <Alert severity="error" role="status">
          Rejected by {approval.decidedBy?.name} on{" "}
          {formatInstant(approval.decidedAt)}: {approval.decisionReason}
        </Alert>
      )}
      {approval.state === "PENDING" && (
        <Typography variant="body2" role="status">
          Waiting for approval since {formatInstant(approval.submittedAt)},
          submitted by {approval.submittedBy?.name}.
        </Typography>
      )}
      {(approval.state === "APPROVED" ||
        approval.state === "APPROVED_WITHOUT_DOCUMENT") && (
        <Typography variant="body2" role="status">
          Approved by {approval.decidedBy?.name} on{" "}
          {formatInstant(approval.decidedAt)}
          {approval.selfDecided && " (the person who submitted it)"}.
        </Typography>
      )}
      {approval.state === "APPROVED_WITHOUT_DOCUMENT" && (
        <Alert severity="info">
          This MoU is approved but has no signed document attached.
        </Alert>
      )}
      {message && (
        <Alert severity="success" role="status" onClose={() => setMessage(null)}>
          {message}
        </Alert>
      )}
      {error && (
        <Alert severity="error" role="alert" onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      <MouDocumentList
        approval={approval}
        onRemove={
          can("REMOVE_DOCUMENT")
            ? (documentId) => setDialog({ kind: "remove", documentId })
            : undefined
        }
      />
      {can("UPLOAD") && (
        <UploadMouDocument
          contractId={contract.id}
          replacing={hasDocument}
          warnsReset={
            approval.state === "APPROVED" ||
            approval.state === "APPROVED_WITHOUT_DOCUMENT"
          }
          onUploaded={(next) => {
            setApproval(next);
            setError(null);
            setMessage("The document was uploaded.");
          }}
        />
      )}

      <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
        {can("SUBMIT") && (
          <Button
            variant="contained"
            onClick={() => setDialog({ kind: "submit" })}
          >
            Send for approval
          </Button>
        )}
        {can("APPROVE") && (
          <Button
            variant="contained"
            color="success"
            onClick={() => setDialog({ kind: "approve" })}
          >
            Approve
          </Button>
        )}
        {can("REJECT") && (
          <Button
            variant="outlined"
            color="error"
            onClick={() => setDialog({ kind: "reject" })}
          >
            Reject
          </Button>
        )}
        {can("AMEND_TERMS") && (
          <Button onClick={() => setDialog({ kind: "amend" })}>
            Correct the terms
          </Button>
        )}
      </Stack>

      <ApprovalHistory approval={approval} />

      {dialog.kind === "submit" && (
        <SubmitDialog
          hasDocument={hasDocument}
          onCancel={() => setDialog({ kind: "none" })}
          onConfirm={() =>
            void run(
              () => submitMou(authFetch, contract.id, approval.version),
              "The MoU was sent for approval.",
            )
          }
        />
      )}
      {dialog.kind === "approve" && (
        <ApproveDialog
          hasDocument={hasDocument}
          onCancel={() => setDialog({ kind: "none" })}
          onConfirm={() =>
            void run(
              () => approveMou(authFetch, contract.id, approval.version),
              "The MoU was approved.",
            )
          }
        />
      )}
      {dialog.kind === "reject" && (
        <RejectDialog
          onCancel={() => setDialog({ kind: "none" })}
          onConfirm={async (reason) =>
            apply(
              await rejectMou(authFetch, contract.id, approval.version, reason),
              "The MoU was rejected.",
            )
          }
        />
      )}
      {dialog.kind === "amend" && (
        <AmendTermsDialog
          contract={contract}
          approvalVersion={approval.version}
          onClose={() => setDialog({ kind: "none" })}
          onSaved={amended}
        />
      )}
      {dialog.kind === "remove" && (
        <RemoveDocumentDialog
          contractId={contract.id}
          documentId={dialog.documentId}
          onClose={() => setDialog({ kind: "none" })}
          onRemoved={(next) => {
            setApproval(next);
            setDialog({ kind: "none" });
            setMessage("The version was removed.");
          }}
        />
      )}
    </Stack>
  );
}
