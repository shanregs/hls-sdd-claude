import { Chip } from "@mui/material";
import { RESULT_LABELS, type Result } from "./performanceApi";

const COLORS: Record<Result, "success" | "warning" | "error"> = {
  GOOD: "success",
  NEEDS_IMPROVEMENT: "warning",
  UNSUITABLE: "error",
};

/** An overall result as a coloured chip; the text is the same label the form uses. */
export function ResultChip({ result }: { result: Result | null }) {
  if (!result) return null;
  return (
    <Chip size="small" color={COLORS[result]} label={RESULT_LABELS[result]} />
  );
}
