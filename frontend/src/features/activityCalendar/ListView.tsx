import {
  Chip,
  Link,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
} from "@mui/material";
import { formatDate } from "../teachers/formatters";
import {
  KIND_LABELS,
  STATUS_LABELS,
  type CalendarItem,
} from "./activityCalendarApi";

/** The same items as the grid, one row each, oldest date first. */
export function ListView({
  items,
  management,
  onOpen,
}: {
  items: CalendarItem[];
  management: boolean;
  onOpen: (item: CalendarItem) => void;
}) {
  const sorted = [...items].sort(
    (a, b) => a.date.localeCompare(b.date) || a.key.localeCompare(b.key),
  );
  return (
    <Table size="small" aria-label="Activity list">
      <TableHead>
        <TableRow>
          <TableCell>Date</TableCell>
          <TableCell>Kind</TableCell>
          <TableCell>Manager</TableCell>
          <TableCell>Zone</TableCell>
          <TableCell>Place</TableCell>
          <TableCell>Status</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {sorted.map((item) => (
          <TableRow key={item.key} hover>
            <TableCell>
              <Link
                component="button"
                type="button"
                onClick={() => onOpen(item)}
                aria-label={`${KIND_LABELS[item.kind]} ${formatDate(item.date)} ${STATUS_LABELS[item.status]}`}
              >
                {formatDate(item.date)}
              </Link>
            </TableCell>
            <TableCell>{KIND_LABELS[item.kind]}</TableCell>
            <TableCell>{item.people.map((p) => p.name).join(", ")}</TableCell>
            <TableCell>
              {item.zoneName ?? (management ? "No Zone" : "")}
            </TableCell>
            <TableCell>{item.place ?? "No place recorded"}</TableCell>
            <TableCell>
              <Chip
                size="small"
                label={`${STATUS_LABELS[item.status]}${item.doneLate ? " (late)" : ""}`}
              />
            </TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
