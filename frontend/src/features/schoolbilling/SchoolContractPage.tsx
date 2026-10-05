import { useCallback, useEffect, useState } from "react";
import { Link as RouterLink, useParams } from "react-router-dom";
import {
  Alert,
  Box,
  Button,
  Divider,
  Link,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { useGrantedActions } from "../common/useGrantedActions";
import { ConfirmDialog } from "../common/ConfirmDialog";
import { formatDate, formatRupees, todayIso } from "../teachers/formatters";
import { ContractStatusChip } from "./ContractStatusChip";
import { MapTeachersDialog, type MappableTeacher } from "./MapTeachersDialog";
import { MouFormDialog } from "./MouFormDialog";
import {
  cancelContract,
  endContract,
  getSchoolContracts,
  type ContractRow,
  type SchoolContracts,
} from "./schoolContractsApi";

type State =
  | { kind: "loading" }
  | { kind: "error"; reason: string }
  | { kind: "ready"; data: SchoolContracts };

type Dialog =
  | { kind: "none" }
  | { kind: "new" }
  | { kind: "record"; contract: ContractRow }
  | { kind: "map"; contract: ContractRow }
  | { kind: "end"; contract: ContractRow }
  | { kind: "cancel"; contract: ContractRow };

const ROUTE = "/operations/school-contracts";

/**
 * One School's contracts (spec 012): the MoU in effect with its positions, signatories and mapped Teachers,
 * the Teachers not mapped yet, and the history. Admin and Director record, end and cancel contracts; the
 * Zone Manager reads them (the buttons simply are not shown).
 */
export function SchoolContractPage() {
  const { schoolId = "" } = useParams();
  const { authFetch } = useAuth();
  const granted = useGrantedActions(ROUTE);
  const teacherActions = useGrantedActions("/master-data/teachers");
  const [state, setState] = useState<State>({ kind: "loading" });
  const [dialog, setDialog] = useState<Dialog>({ kind: "none" });
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async () => {
    const result = await getSchoolContracts(authFetch, schoolId);
    setState(
      result.ok
        ? { kind: "ready", data: result.data }
        : { kind: "error", reason: result.reason },
    );
  }, [authFetch, schoolId]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch
    void load();
  }, [load]);

  const canCreate = granted.has("CREATE");
  const canEdit = granted.has("EDIT");
  const canMap = teacherActions.has("EDIT");

  const closeAndReload = () => {
    setDialog({ kind: "none" });
    void load();
  };

  if (state.kind === "loading") {
    return <Typography role="status">Loading contracts...</Typography>;
  }
  if (state.kind === "error") {
    return (
      <Alert severity="error" role="alert">
        {state.reason}
      </Alert>
    );
  }
  const { data } = state;
  const today = todayIso();
  const usable = data.contracts.filter(
    (c) => c.status !== "ENDED" && c.status !== "CANCELLED",
  );
  // the contract in effect today, and the next one when a new MoU starts later
  const live = usable.find(
    (c) => c.startsOn <= today && (c.endsOn === null || c.endsOn >= today),
  );
  const upcoming = [...usable]
    .filter((c) => c.startsOn > today)
    .sort((a, b) => a.startsOn.localeCompare(b.startsOn))[0];
  // new Teachers are mapped to the next MoU when there is one, otherwise to the one in effect
  const mapTarget = upcoming ?? live;
  const history = data.contracts.filter((c) => c !== live && c !== upcoming);

  // Teachers already at the School who could be mapped to the contract in effect
  const mappable: MappableTeacher[] = [
    ...data.unmappedTeachers.map((t) => ({
      teacherId: t.teacherId,
      teacherName: t.teacherName,
      from: "not mapped",
    })),
    ...data.contracts
      .filter((c) => c !== mapTarget)
      .flatMap((c) =>
        c.positions
          .filter((p) => p.teacherId !== null)
          .map((p) => ({
            teacherId: p.teacherId as string,
            teacherName: p.teacherName ?? "?",
            from: `Position ${p.number} of the MoU from ${formatDate(c.startsOn)}`,
          })),
      ),
  ].filter(
    (t, i, all) => all.findIndex((x) => x.teacherId === t.teacherId) === i,
  );

  return (
    <Stack spacing={2}>
      <Box>
        <Link component={RouterLink} to={ROUTE}>
          All School contracts
        </Link>
        <Typography variant="h5" component="h1">
          {data.schoolName}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Zone Manager: {data.zoneManagerName ?? "None assigned"}
        </Typography>
      </Box>

      {message && (
        <Alert
          severity="success"
          role="status"
          onClose={() => setMessage(null)}
        >
          {message}
        </Alert>
      )}

      {data.unmappedTeachers.length > 0 && (
        <Alert severity="warning" role="status">
          {data.unmappedTeachers.length} Teacher
          {data.unmappedTeachers.length === 1 ? " is" : "s are"} at this School
          but not mapped to the MoU in effect:{" "}
          {data.unmappedTeachers.map((t) => t.teacherName).join(", ")}.
        </Alert>
      )}

      <Stack direction="row" spacing={1} sx={{ flexWrap: "wrap" }}>
        {canCreate && (
          <Button
            variant="contained"
            onClick={() => setDialog({ kind: "new" })}
          >
            Record a new MoU
          </Button>
        )}
        {canMap &&
          mapTarget &&
          mapTarget.state === "ACTIVE" &&
          mappable.length > 0 && (
            <Button
              onClick={() => setDialog({ kind: "map", contract: mapTarget })}
            >
              Map Teachers to {upcoming ? "the next MoU" : "this MoU"}
            </Button>
          )}
      </Stack>

      {data.contracts.length === 0 && (
        <Alert severity="info">
          This School has no MoU yet.
          {canCreate ? " Record one to start." : ""}
        </Alert>
      )}

      {live && (
        <ContractCard
          contract={live}
          heading="Contract in effect"
          actions={
            <>
              {canEdit && live.state === "RATE_PENDING" && (
                <Button
                  size="small"
                  variant="contained"
                  onClick={() => setDialog({ kind: "record", contract: live })}
                >
                  Record the MoU
                </Button>
              )}
              {canEdit && live.state === "ACTIVE" && (
                <Button
                  size="small"
                  onClick={() => setDialog({ kind: "end", contract: live })}
                >
                  End this contract
                </Button>
              )}
              {canEdit && (
                <Button
                  size="small"
                  color="error"
                  onClick={() => setDialog({ kind: "cancel", contract: live })}
                >
                  Cancel this contract
                </Button>
              )}
            </>
          }
        />
      )}

      {upcoming && (
        <ContractCard
          contract={upcoming}
          heading={`Upcoming contract (from ${formatDate(upcoming.startsOn)})`}
          actions={
            canEdit ? (
              <Button
                size="small"
                color="error"
                onClick={() =>
                  setDialog({ kind: "cancel", contract: upcoming })
                }
              >
                Cancel this contract
              </Button>
            ) : undefined
          }
        />
      )}

      {history.length > 0 && (
        <>
          <Divider />
          <Typography variant="h6" component="h2">
            History
          </Typography>
          {history.map((c) => (
            <ContractCard key={c.id} contract={c} heading="Earlier contract" />
          ))}
        </>
      )}

      {dialog.kind === "new" && (
        <MouFormDialog
          schoolId={schoolId}
          schoolName={data.schoolName}
          onClose={() => setDialog({ kind: "none" })}
          onSaved={() => {
            setMessage("The MoU was recorded.");
            closeAndReload();
          }}
        />
      )}
      {dialog.kind === "record" && (
        <MouFormDialog
          schoolId={schoolId}
          schoolName={data.schoolName}
          pending={dialog.contract}
          onClose={() => setDialog({ kind: "none" })}
          onSaved={() => {
            setMessage("The MoU was recorded.");
            closeAndReload();
          }}
        />
      )}
      {dialog.kind === "map" && (
        <MapTeachersDialog
          contract={dialog.contract}
          teachers={mappable}
          onClose={() => setDialog({ kind: "none" })}
          onSaved={() => {
            setMessage("The Teachers were mapped.");
            closeAndReload();
          }}
        />
      )}
      {dialog.kind === "end" && (
        <ConfirmDialog
          title="End this contract?"
          message={`The contract will end on ${formatDate(todayIso())}. Teachers stay at the School until their own assignment ends.`}
          confirmLabel="End contract"
          onCancel={() => setDialog({ kind: "none" })}
          onConfirm={() => {
            void (async () => {
              const result = await endContract(
                authFetch,
                dialog.contract.id,
                todayIso(),
              );
              if (result.ok) {
                setMessage("The contract was ended.");
                closeAndReload();
              } else {
                setDialog({ kind: "none" });
                setState({ kind: "error", reason: result.reason });
              }
            })();
          }}
        />
      )}
      {dialog.kind === "cancel" && (
        <ConfirmDialog
          destructive
          title="Cancel this contract?"
          message="Only a contract with no Teachers mapped to it can be cancelled."
          confirmLabel="Cancel contract"
          onCancel={() => setDialog({ kind: "none" })}
          onConfirm={() => {
            void (async () => {
              const result = await cancelContract(
                authFetch,
                dialog.contract.id,
              );
              if (result.ok) {
                setMessage("The contract was cancelled.");
                closeAndReload();
              } else {
                setDialog({ kind: "none" });
                setMessage(null);
                setState({ kind: "error", reason: result.reason });
              }
            })();
          }}
        />
      )}
    </Stack>
  );
}

function ContractCard({
  contract,
  heading,
  actions,
}: {
  contract: ContractRow;
  heading: string;
  actions?: React.ReactNode;
}) {
  const hls = contract.signatories.filter((s) => s.party === "HLS");
  const school = contract.signatories.filter((s) => s.party === "SCHOOL");
  return (
    <Paper sx={{ p: 2 }} component="section" aria-label={heading}>
      <Stack spacing={1.5}>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", flexWrap: "wrap" }}
        >
          <Typography variant="h6" component="h2">
            {heading}
          </Typography>
          <ContractStatusChip status={contract.status} />
        </Stack>
        <Typography variant="body2">
          {formatDate(contract.startsOn)}
          {contract.endsOn ? ` to ${formatDate(contract.endsOn)}` : " onwards"}
          {contract.teacherCount !== null &&
            ` · ${contract.teacherCount} Teacher${contract.teacherCount === 1 ? "" : "s"}`}
          {contract.salaryMode === "SAME_FOR_ALL" &&
            contract.rate !== null &&
            ` · ${formatRupees(Number(contract.rate))} each`}
          {contract.salaryMode === "PER_TEACHER" &&
            " · a different salary for each Teacher"}
        </Typography>
        {contract.state === "RATE_PENDING" ? (
          <Alert severity="info">
            This School has Teachers but no MoU recorded yet. Record the MoU to
            set the positions, salaries and signing details.
          </Alert>
        ) : (
          <>
            <Table size="small" aria-label="Positions">
              <TableHead>
                <TableRow>
                  <TableCell>Position</TableCell>
                  <TableCell align="right">Monthly salary</TableCell>
                  <TableCell>Teacher</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {contract.positions.map((p) => (
                  <TableRow key={p.id}>
                    <TableCell>
                      {p.number}
                      {p.title ? ` (${p.title})` : ""}
                    </TableCell>
                    <TableCell align="right">
                      {formatRupees(Number(p.salary))}
                    </TableCell>
                    <TableCell>{p.teacherName ?? "Vacant"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            <Typography variant="subtitle2">
              Signed on {formatDate(contract.signedOn)}
            </Typography>
            <Typography variant="body2">
              For the School:{" "}
              {school.map((s) => `${s.name} (${s.designation})`).join(", ")}
            </Typography>
            <Typography variant="body2">
              For HLS:{" "}
              {hls.map((s) => `${s.name} (${s.designation})`).join(", ")}
            </Typography>
          </>
        )}
        {actions && (
          <Stack direction="row" spacing={1}>
            {actions}
          </Stack>
        )}
      </Stack>
    </Paper>
  );
}
