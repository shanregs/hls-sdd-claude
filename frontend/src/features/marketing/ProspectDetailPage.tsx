import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useNavigate, useParams } from "react-router-dom";
import {
  Alert,
  Button,
  Chip,
  Divider,
  Link,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { formatDate, formatRupees } from "../teachers/formatters";
import { listInterviewers } from "../recruitment/recruitmentApi";
import { ReasonDialog } from "../recruitment/ReasonDialog";
import { ActivityDialog, type ActivityMode } from "./ActivityDialog";
import { AttachmentList } from "./AttachmentList";
import { ProposalForm } from "./ProposalForm";
import { ProspectDialog } from "./ProspectDialog";
import { WinDialog } from "./WinDialog";
import {
  ACTIVE_STAGES,
  ACTIVITY_LABEL,
  STAGE_LABEL,
  changeOwner,
  getProspect,
  listActivities,
  listProposals,
  moveStage,
  reviewProspect,
  type Activity,
  type PersonRef,
  type Proposal,
  type ProspectDetail,
  type StoredStage,
} from "./marketingApi";

const ROUTE = "/marketing/prospects";

type Dialog =
  | { kind: "edit" }
  | { kind: "proposal" }
  | { kind: "win" }
  | { kind: "lost" }
  | { kind: "reject" }
  | { kind: "activity"; mode: ActivityMode; activity?: Activity };

function statusText(a: Activity) {
  const base =
    a.effectiveStatus === "MISSED"
      ? "Missed"
      : a.effectiveStatus.charAt(0) + a.effectiveStatus.slice(1).toLowerCase();
  return `${base}${a.rescheduled ? ", rescheduled" : ""}${a.followUpOverdue ? ", follow-up overdue" : ""}`;
}

function money(value: string) {
  return formatRupees(Number(value));
}

/**
 * One prospect (spec 023): its details, stage and review, the proposal revisions, the MoU status read from spec 012,
 * its visits with their files, and the history of stage and owner changes. Buttons show only for what the caller may do.
 */
export function ProspectDetailPage() {
  const { id = "" } = useParams();
  const { authFetch, user } = useAuth();
  const navigate = useNavigate();
  const actions = useGrantedActions(ROUTE);
  const [detail, setDetail] = useState<ProspectDetail | null>(null);
  const [revisions, setRevisions] = useState<Proposal[]>([]);
  const [activities, setActivities] = useState<Activity[]>([]);
  const [people, setPeople] = useState<PersonRef[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [dialog, setDialog] = useState<Dialog | null>(null);

  const load = useCallback(async () => {
    const [d, p, a] = await Promise.all([
      getProspect(authFetch, id),
      listProposals(authFetch, id),
      listActivities(authFetch, { prospect: id }),
    ]);
    if (!d.ok) {
      setError(d.reason);
      return;
    }
    setError(null);
    setDetail(d.data);
    if (p.ok) setRevisions(p.data);
    if (a.ok) setActivities(a.data.content);
  }, [authFetch, id]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  const orgWide = !!user?.roles.some((r) => r === "ADMIN" || r === "DIRECTOR");
  useEffect(() => {
    if (!orgWide) return;
    void (async () => {
      const result = await listInterviewers(authFetch);
      if (result.ok) setPeople(result.data);
    })();
  }, [authFetch, orgWide]);

  if (error)
    return (
      <Alert severity="error" role="alert">
        {error}
      </Alert>
    );
  if (!detail)
    return <Typography role="status">Loading prospect...</Typography>;

  const row = detail.row;
  const canEdit = actions.has("EDIT");
  const canApprove = actions.has("APPROVE");
  const inPipeline = !row.won && ACTIVE_STAGES.includes(row.stage);
  const done = (text?: string) => {
    setDialog(null);
    if (text) setMessage(text);
    void load();
  };
  const run = async (
    promise: Promise<{ ok: boolean; reason?: string }>,
    text: string,
  ) => {
    const result = await promise;
    if (!result.ok) setMessage(result.reason ?? "That did not work.");
    else done(text);
  };

  const status = detail.contractStatus;
  const latest = detail.proposal;

  return (
    <Paper sx={{ p: 2 }}>
      <Stack spacing={2}>
        <Link component={RouterLink} to={ROUTE}>
          All prospects
        </Link>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", flexWrap: "wrap" }}
        >
          <Typography variant="h5" component="h1">
            {row.name}
          </Typography>
          <Chip label={STAGE_LABEL[row.effectiveStage]} />
          {row.followUpOverdue && (
            <Chip color="warning" label="Follow-up overdue" />
          )}
          {row.mouOverdue && <Chip color="error" label="MoU not recorded" />}
        </Stack>
        <Typography variant="body2">
          {row.zoneName} Zone. Owner: {row.owner.name}.
          {row.board ? ` Board: ${row.board}.` : ""}
          {row.expectedTeachers
            ? ` Expects ${row.expectedTeachers} Teachers.`
            : ""}
        </Typography>
        <Typography variant="body2">
          {[
            detail.address,
            row.contactPerson &&
              `${row.contactPerson}${detail.designation ? ` (${detail.designation})` : ""}`,
            detail.phone,
            detail.email,
          ]
            .filter(Boolean)
            .join(" - ")}
        </Typography>
        {detail.lostReason && (
          <Alert severity="warning">Lost: {detail.lostReason}</Alert>
        )}
        {message && (
          <Alert severity="info" role="status" onClose={() => setMessage(null)}>
            {message}
          </Alert>
        )}

        <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap", gap: 1 }}>
          {canEdit && (
            <Button onClick={() => setDialog({ kind: "edit" })}>
              Change details
            </Button>
          )}
          {canEdit && inPipeline && (
            <TextField
              select
              size="small"
              label="Move to stage"
              value=""
              onChange={(e) =>
                void run(
                  moveStage(
                    authFetch,
                    row.id,
                    e.target.value as StoredStage,
                    undefined,
                    row.version,
                  ),
                  "Stage changed.",
                )
              }
              sx={{ minWidth: 190 }}
            >
              {ACTIVE_STAGES.filter((s) => s !== row.stage).map((s) => (
                <MenuItem key={s} value={s}>
                  {STAGE_LABEL[s]}
                </MenuItem>
              ))}
            </TextField>
          )}
          {canEdit && inPipeline && (
            <Button
              onClick={() =>
                void run(
                  moveStage(authFetch, row.id, "ON_HOLD"),
                  "Put on hold.",
                )
              }
            >
              Put on hold
            </Button>
          )}
          {canEdit && row.stage === "ON_HOLD" && (
            <Button
              onClick={() =>
                void run(moveStage(authFetch, row.id, "RESUME"), "Resumed.")
              }
            >
              Resume
            </Button>
          )}
          {canEdit && (inPipeline || row.stage === "ON_HOLD") && !row.won && (
            <Button color="error" onClick={() => setDialog({ kind: "lost" })}>
              Mark lost
            </Button>
          )}
          {canEdit && row.stage === "LOST" && (
            <Button
              onClick={() =>
                void run(moveStage(authFetch, row.id, "REOPEN"), "Reopened.")
              }
            >
              Reopen
            </Button>
          )}
          {canApprove && row.stage === "FINAL_STAGE" && !row.won && (
            <>
              <Button
                variant="contained"
                onClick={() =>
                  void run(
                    reviewProspect(authFetch, row.id, "APPROVE"),
                    "Approved: the prospect is won.",
                  )
                }
              >
                Approve (won)
              </Button>
              <Button
                color="error"
                onClick={() => setDialog({ kind: "reject" })}
              >
                Reject
              </Button>
            </>
          )}
          {orgWide && canEdit && row.won && !row.schoolId && (
            <Button
              variant="contained"
              onClick={() => setDialog({ kind: "win" })}
            >
              Create the School
            </Button>
          )}
          {row.schoolId &&
            status.available &&
            status.status === "NOT_RECORDED" && (
              <Button
                variant="contained"
                onClick={() =>
                  navigate(
                    `/operations/school-contracts/schools/${row.schoolId}`,
                    { state: { proposal: latest } },
                  )
                }
              >
                Record the MoU
              </Button>
            )}
        </Stack>
        {orgWide && canEdit && people.length > 0 && (
          <TextField
            select
            size="small"
            label="Owner"
            value={row.owner.userId}
            onChange={(e) =>
              void run(
                changeOwner(authFetch, row.id, e.target.value),
                "Owner changed.",
              )
            }
            sx={{ maxWidth: 260 }}
          >
            {people.map((p) => (
              <MenuItem key={p.userId} value={p.userId}>
                {p.name}
              </MenuItem>
            ))}
          </TextField>
        )}

        {row.won && (
          <>
            <Divider />
            <Typography variant="h6" component="h2">
              MoU status
            </Typography>
            {!status.available && (
              <Typography>Contract status is not available.</Typography>
            )}
            {status.available && status.status === "NOT_RECORDED" && (
              <Typography>
                {row.schoolId
                  ? "Won. The MoU is not recorded yet."
                  : "Won. The School and the MoU are to be recorded."}
              </Typography>
            )}
            {status.contract && (
              <Stack spacing={0.5}>
                <Typography>
                  {status.status === "ACTIVE" ? "Active" : "MoU recorded"}:{" "}
                  {formatDate(status.contract.startsOn)}
                  {status.contract.endsOn
                    ? ` to ${formatDate(status.contract.endsOn)}`
                    : " onwards"}
                  ; {status.contract.teacherCount} Teachers,{" "}
                  {status.contract.filled} filled, {status.contract.vacant}{" "}
                  vacant.
                </Typography>
                {status.differences.length > 0 && (
                  <Alert severity="info">
                    The signed MoU differs from the proposal:{" "}
                    {status.differences.join("; ")}.
                  </Alert>
                )}
              </Stack>
            )}
          </>
        )}

        <Divider />
        <Stack
          direction="row"
          sx={{ justifyContent: "space-between", alignItems: "center" }}
        >
          <Typography variant="h6" component="h2">
            Proposal (not a contract)
          </Typography>
          {actions.has("CREATE") && (
            <Button onClick={() => setDialog({ kind: "proposal" })}>
              {latest ? "New revision" : "Add proposal"}
            </Button>
          )}
        </Stack>
        {revisions.length === 0 ? (
          <Typography color="text.secondary">
            No proposal yet. A proposal is needed before Final Stage.
          </Typography>
        ) : (
          revisions.map((r, index) => (
            <Typography key={r.id} variant="body2">
              {index === 0 ? "Current: " : ""}Revision {r.revision}:{" "}
              {r.teacherCount} Teachers from {formatDate(r.startMonth)},{" "}
              {r.salaryMode === "SAME_FOR_ALL" && r.rate
                ? `${money(r.rate)} each`
                : `${r.positions.map((p) => money(p.salary)).join(", ")}`}
              ; monthly total {money(r.monthlyTotal)}.
              {r.notes ? ` ${r.notes}` : ""}
            </Typography>
          ))
        )}

        <Divider />
        <Stack
          direction="row"
          sx={{ justifyContent: "space-between", alignItems: "center" }}
        >
          <Typography variant="h6" component="h2">
            Visits and meetings
          </Typography>
          {actions.has("CREATE") && (
            <Button
              onClick={() => setDialog({ kind: "activity", mode: "plan" })}
            >
              Plan an activity
            </Button>
          )}
        </Stack>
        {activities.length === 0 && (
          <Typography color="text.secondary">
            Nothing planned or done yet.
          </Typography>
        )}
        {activities.map((a) => (
          <Paper key={a.id} variant="outlined" sx={{ p: 1.5 }}>
            <Stack spacing={0.5}>
              <Typography variant="subtitle2">
                {formatDate(a.date)}: {ACTIVITY_LABEL[a.type]} ({statusText(a)})
              </Typography>
              {a.attendees.length > 0 && (
                <Typography variant="body2">
                  With: {a.attendees.map((p) => p.name).join(", ")}
                </Typography>
              )}
              {a.notes && <Typography variant="body2">{a.notes}</Typography>}
              {a.outcome && (
                <Typography variant="body2">Outcome: {a.outcome}</Typography>
              )}
              {a.followUpOn && (
                <Typography variant="body2">
                  Follow up on {formatDate(a.followUpOn)}
                </Typography>
              )}
              {a.cancelReason && (
                <Typography variant="body2">
                  Cancelled: {a.cancelReason}
                </Typography>
              )}
              {a.dateHistory.length > 0 && (
                <Typography variant="caption">
                  Earlier dates:{" "}
                  {a.dateHistory.map((h) => formatDate(h.from)).join(", ")}
                </Typography>
              )}
              <AttachmentList
                activity={a}
                canAdd={actions.has("CREATE")}
                canRemove={orgWide && canEdit}
                onChanged={() => void load()}
              />
              {canEdit && a.status === "PLANNED" && (
                <Stack direction="row" spacing={0.5}>
                  <Button
                    size="small"
                    onClick={() =>
                      setDialog({
                        kind: "activity",
                        mode: "complete",
                        activity: a,
                      })
                    }
                  >
                    Complete
                  </Button>
                  <Button
                    size="small"
                    onClick={() =>
                      setDialog({
                        kind: "activity",
                        mode: "reschedule",
                        activity: a,
                      })
                    }
                  >
                    Reschedule
                  </Button>
                  <Button
                    size="small"
                    color="error"
                    onClick={() =>
                      setDialog({
                        kind: "activity",
                        mode: "cancel",
                        activity: a,
                      })
                    }
                  >
                    Cancel
                  </Button>
                </Stack>
              )}
            </Stack>
          </Paper>
        ))}

        <Divider />
        <Typography variant="h6" component="h2">
          History
        </Typography>
        {detail.stageHistory.length === 0 &&
          detail.ownerHistory.length === 0 && (
            <Typography color="text.secondary">No changes yet.</Typography>
          )}
        {detail.stageHistory.map((h, i) => (
          <Typography key={`s${i}`} variant="body2">
            {new Date(h.at).toLocaleString("en-IN")}:{" "}
            {h.kind === "STAGE"
              ? `${h.from ?? "-"} to ${h.to}`
              : h.kind === "REVIEW_APPROVED"
                ? "Final Stage approved"
                : "Final Stage rejected"}
            {h.reason ? ` (${h.reason})` : ""} by {h.by.name}
          </Typography>
        ))}
        {detail.ownerHistory.map((h, i) => (
          <Typography key={`o${i}`} variant="body2">
            {new Date(h.at).toLocaleString("en-IN")}: owner{" "}
            {h.from ? `${h.from.name} to ` : "set to "}
            {h.to.name} by {h.by.name}
          </Typography>
        ))}
      </Stack>

      {dialog?.kind === "edit" && (
        <ProspectDialog
          prospect={detail}
          onClose={() => setDialog(null)}
          onSaved={() => done("Details saved.")}
        />
      )}
      {dialog?.kind === "proposal" && (
        <ProposalForm
          prospectId={row.id}
          latest={latest}
          onClose={() => setDialog(null)}
          onSaved={() => done("Proposal revision saved.")}
        />
      )}
      {dialog?.kind === "win" && (
        <WinDialog
          prospect={detail}
          onClose={() => setDialog(null)}
          onDone={() => done("The School was created.")}
        />
      )}
      {dialog?.kind === "activity" && (
        <ActivityDialog
          mode={dialog.mode}
          activity={dialog.activity}
          prospect={{ id: row.id, name: row.name }}
          onClose={() => setDialog(null)}
          onSaved={() => done()}
        />
      )}
      {dialog?.kind === "lost" && (
        <ReasonDialog
          title="Mark this prospect lost?"
          label="Reason"
          confirmLabel="Mark lost"
          destructive
          onConfirm={async (reason) => {
            const result = await moveStage(authFetch, row.id, "LOST", reason);
            if (!result.ok) return result.reason;
            done("Marked lost.");
            return null;
          }}
          onCancel={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "reject" && (
        <ReasonDialog
          title="Reject the Final Stage review?"
          message="The prospect goes back to Negotiation with your reason."
          label="Reason"
          confirmLabel="Reject"
          destructive
          onConfirm={async (reason) => {
            const result = await reviewProspect(
              authFetch,
              row.id,
              "REJECT",
              reason,
            );
            if (!result.ok) return result.reason;
            done("Rejected; back in Negotiation.");
            return null;
          }}
          onCancel={() => setDialog(null)}
        />
      )}
    </Paper>
  );
}
