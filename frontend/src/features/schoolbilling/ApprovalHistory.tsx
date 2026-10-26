import { Stack, Typography } from "@mui/material";
import type { Approval } from "./mouApprovalApi";
import { formatInstant, historyLabel } from "./mouFormat";

/** The approval and document history, oldest first, with who did it, in which role, and any reason or term change. */
export function ApprovalHistory({ approval }: { approval: Approval }) {
  if (approval.history.length === 0) {
    return null;
  }
  // a decision by the person who submitted the round is marked
  const submitters = new Map<number, string | undefined>();
  approval.history.forEach((h) => {
    if (
      h.action === "SUBMITTED" ||
      h.action === "RESUBMITTED" ||
      h.action === "RESET_TO_PENDING"
    ) {
      submitters.set(h.round, h.actor?.id);
    }
  });
  return (
    <Stack spacing={0.5} component="section" aria-label="Approval history">
      <Typography variant="subtitle2" component="h3">
        History
      </Typography>
      <ol style={{ margin: 0, paddingLeft: 20 }}>
        {approval.history.map((h, i) => {
          const self =
            (h.action === "APPROVED" || h.action === "REJECTED") &&
            h.actor !== null &&
            submitters.get(h.round) === h.actor.id;
          return (
            <li key={`${h.at}-${i}`}>
              <Typography variant="body2">
                {formatInstant(h.at)} - {historyLabel(h.action)}
                {h.documentVersion !== null &&
                  ` (version ${h.documentVersion})`}
                {h.actor && ` by ${h.actor.name}`}
                {h.actorRole && ` (${h.actorRole.toLowerCase()})`}
                {self && " - the person who submitted it"}
                {h.note && `: ${h.note}`}
              </Typography>
              {h.detail && (
                <Typography
                  variant="caption"
                  color="text.secondary"
                  sx={{ whiteSpace: "pre-line" }}
                >
                  {h.detail}
                </Typography>
              )}
            </li>
          );
        })}
      </ol>
    </Stack>
  );
}
