import { StatusChip, type StatusKind } from "../../theme/StatusChip";
import { RESULT_LABELS, type Result } from "./performanceApi";

const KINDS: Record<Result, StatusKind> = {
  GOOD: "approved",
  NEEDS_IMPROVEMENT: "pending",
  UNSUITABLE: "rejected",
};

/** An overall result as a coloured chip; the text is the same label the form uses. */
export function ResultChip({ result }: { result: Result | null }) {
  if (!result) return null;
  return <StatusChip kind={KINDS[result]} label={RESULT_LABELS[result]} />;
}
