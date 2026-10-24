import { Link as RouterLink } from "react-router-dom";
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Link,
  Stack,
  Typography,
} from "@mui/material";
import { formatDate } from "../teachers/formatters";
import {
  KIND_LABELS,
  STATUS_LABELS,
  type CalendarItem,
} from "./activityCalendarApi";

/** Read-only detail of one calendar item. No complete, move or cancel control exists here (FR-019). */
export function ItemDetail({
  item,
  management,
  onClose,
}: {
  item: CalendarItem;
  management: boolean;
  onClose: () => void;
}) {
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" aria-label="Item detail">
      <DialogTitle>
        {KIND_LABELS[item.kind]}
        {item.subKind ? ` (${item.subKind.replaceAll("_", " ").toLowerCase()})` : ""}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={1}>
          <Typography>
            <strong>Manager:</strong>{" "}
            {item.people.map((p) => p.name).join(", ") || "None"}
          </Typography>
          <Typography>
            <strong>Date:</strong> {formatDate(item.date)}
          </Typography>
          <Typography>
            <strong>Place:</strong> {item.place ?? "No place recorded"}
          </Typography>
          {management && (
            <Typography>
              <strong>Zone:</strong> {item.zoneName ?? "No Zone"}
            </Typography>
          )}
          <Typography>
            <strong>Status:</strong> {STATUS_LABELS[item.status]}
            {item.doneLate && item.completedOn
              ? ` (done late, on ${formatDate(item.completedOn)})`
              : ""}
            {item.status === "MISSED" && item.daysLate !== null
              ? ` (${item.daysLate} ${item.daysLate === 1 ? "day" : "days"} late)`
              : ""}
          </Typography>
          {item.rescheduled && item.originalDate && (
            <Typography>
              <strong>Rescheduled:</strong> first planned{" "}
              {formatDate(item.originalDate)}, moved {item.daysMoved}{" "}
              {item.daysMoved === 1 ? "day" : "days"} to {formatDate(item.date)}
            </Typography>
          )}
          {item.link && (
            <Link component={RouterLink} to={item.link.path}>
              Open in {item.link.screen.toLowerCase()}
            </Link>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
