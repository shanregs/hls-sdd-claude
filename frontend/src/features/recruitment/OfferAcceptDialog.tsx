import { useState } from "react";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from "@mui/material";
import { useAuth } from "../../auth/useAuth";
import { acceptOffer, type Offer } from "./recruitmentApi";

interface OfferAcceptDialogProps {
  offer: Offer;
  onClose: () => void;
  onAccepted: () => void;
}

/**
 * Records acceptance of an issued offer. The server creates the Teacher in training; if the person is already a
 * Teacher it refuses and names them, and if an earlier record has exited it asks for a confirmation first.
 */
export function OfferAcceptDialog({
  offer,
  onClose,
  onAccepted,
}: OfferAcceptDialogProps) {
  const { authFetch } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [needsConfirm, setNeedsConfirm] = useState(false);
  const [busy, setBusy] = useState(false);

  const accept = async () => {
    setBusy(true);
    const result = await acceptOffer(authFetch, offer.id, needsConfirm);
    setBusy(false);
    if (result.ok) {
      onAccepted();
      return;
    }
    if (
      !needsConfirm &&
      result.status === 409 &&
      result.reason.includes("Confirm")
    ) {
      setNeedsConfirm(true);
      setError(result.reason);
      return;
    }
    setError(result.reason);
  };

  return (
    <Dialog open onClose={onClose} aria-labelledby="accept-title">
      <DialogTitle id="accept-title">Record acceptance</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {offer.candidateName} accepts the offer of Rs. {offer.monthlySalary} a
          month. A Teacher record is created in training from the
          candidate&apos;s details; nothing is typed again.
        </DialogContentText>
        {error && (
          <Alert severity={needsConfirm ? "warning" : "error"} role="alert">
            {error}
          </Alert>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
        <Button
          variant="contained"
          onClick={() => void accept()}
          disabled={busy}
        >
          {needsConfirm ? "Create a new Teacher record" : "Accept offer"}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
