import { IconButton, Tooltip } from "@mui/material";
import DeleteOutlined from "@mui/icons-material/DeleteOutlined";
import EditOutlined from "@mui/icons-material/EditOutlined";
import VisibilityOutlined from "@mui/icons-material/VisibilityOutlined";

export type RowAction = "Edit" | "Delete" | "View";

const ICONS = {
  Edit: EditOutlined,
  Delete: DeleteOutlined,
  View: VisibilityOutlined,
} as const;

interface RowActionButtonProps {
  action: RowAction;
  /** What the action applies to, so each button has a distinct accessible name ("Edit Demo Zone"). */
  subject?: string;
  onClick: () => void;
}

/** An icon button for a row's Edit, Delete or View action; the tooltip names the action. */
export function RowActionButton({
  action,
  subject,
  onClick,
}: RowActionButtonProps) {
  const Icon = ICONS[action];
  return (
    <Tooltip title={action}>
      <IconButton
        size="small"
        color={action === "Delete" ? "error" : "primary"}
        aria-label={subject ? `${action} ${subject}` : action}
        onClick={onClick}
      >
        <Icon fontSize="small" />
      </IconButton>
    </Tooltip>
  );
}
