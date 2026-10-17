import { ApiError } from "../api/errors";
import { cancelVisit, type Visit } from "../api/marketingApi";
import { formatIsoDate } from "../formats/dates";
import { ReasonDialog } from "../leave/ReasonDialog";
import { managerRefusalText } from "../manager/managerMessages";
import { visitTarget } from "./VisitCard";

/** The longest cancel reason the server accepts. */
export const MAX_CANCEL_REASON = 300;

interface Props {
  visit: Visit;
  /** Called once the server has cancelled the visit, with the server's visit. */
  onDone: (visit: Visit) => void;
  /** The server said the visit changed or is gone: the caller reads it again. */
  onStale: () => void;
  onCancel: () => void;
}

/** Cancels a planned visit with a required reason (spec 034 FR-007), keeping the reason on a refusal. */
export function CancelVisitDialog({ visit, onDone, onStale, onCancel }: Props) {
  return (
    <ReasonDialog
      title="Cancel this visit?"
      message={`${visitTarget(visit)}, ${formatIsoDate(visit.date)}`}
      fieldLabel="Reason"
      required
      maxLength={MAX_CANCEL_REASON}
      confirmLabel="Cancel visit"
      dismissLabel="Keep visit"
      onConfirm={async (reason) => {
        try {
          onDone(await cancelVisit(visit.id, { reason, version: visit.version }));
        } catch (error) {
          if (error instanceof ApiError && (error.status === 409 || error.status === 404)) onStale();
          return managerRefusalText(error);
        }
        return null;
      }}
      onCancel={onCancel}
    />
  );
}
