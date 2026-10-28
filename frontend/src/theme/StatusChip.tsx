import { Chip } from "@mui/material";
import { useTheme } from "@mui/material/styles";
import { BUILTIN_CATALOGUE, type ChipKind } from "./tokens";

export type StatusKind = ChipKind | "neutral";

interface Props {
  kind: StatusKind;
  label: string;
  size?: "small" | "medium";
}

/**
 * A status as text on a semantic colour (green, amber or red), independent of the user's preset
 * (spec 039 US5), so a status never changes meaning with the theme. The label is always shown, so
 * the colour is never the only signal (WCAG 2.2 AA). "neutral" is the plain grey chip.
 */
export function StatusChip({ kind, label, size = "small" }: Props) {
  const theme = useTheme();
  if (kind === "neutral") {
    return <Chip size={size} label={label} />;
  }
  const chips =
    theme.hls?.chips ??
    BUILTIN_CATALOGUE.statusChips[
      theme.palette.mode === "dark" ? "dark" : "light"
    ];
  return (
    <Chip
      size={size}
      label={label}
      sx={{
        color: chips[kind].text,
        bgcolor: chips[kind].background,
        fontWeight: 600,
      }}
    />
  );
}
