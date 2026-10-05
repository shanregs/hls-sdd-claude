import { useState, type ReactNode } from "react";
import { Pressable, StyleSheet, View } from "react-native";
import { Text, useTheme } from "react-native-paper";
import type { LeaveRequest } from "../api/leaveApi";
import { formatDateTime, formatIsoDate } from "../formats/dates";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { statusLook } from "./statusPresentation";

interface Props {
  request: LeaveRequest;
  /** Show the Teacher and School (Leave Management). */
  showTeacher?: boolean;
  /** Tapping the card opens something else (the detail); without it an expandable card toggles its details. */
  onPress?: () => void;
  /** Show the reason and decision note after a tap (My Leave History). */
  expandable?: boolean;
  /** Always show the reason and decision note (the detail screen). */
  expanded?: boolean;
  children?: ReactNode;
}

export function halfDayText(request: LeaveRequest): string {
  const parts: string[] = [];
  if (request.halfDayStart) parts.push("half day at the start");
  if (request.halfDayEnd) parts.push("half day at the end");
  return parts.join(", ");
}

/** One leave request: type, dates, working days, status, who decided it and when, and the note or reason. */
export function LeaveRequestCard({ request, showTeacher, onPress, expandable, expanded, children }: Props) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const look = statusLook(request.status, request.cancelledBy, theme.dark ? "dark" : "light");
  const showDetails = expanded || (expandable && open);
  const dates = `${formatIsoDate(request.firstDate)} – ${formatIsoDate(request.lastDate)}`;
  const halves = halfDayText(request);
  const decision = request.decidedByName
    ? `${request.status === "APPROVED" ? "Approved" : request.status === "REJECTED" ? "Rejected" : "Cancelled"} by ${request.decidedByName}${request.decidedAt ? ` on ${formatDateTime(request.decidedAt)}` : ""}`
    : null;

  const spoken = [
    showTeacher ? `${request.teacherName}, ${request.schoolName}` : null,
    request.leaveType,
    dates,
    halves || null,
    `${request.workingDays} working days`,
    look.spoken,
    decision,
  ]
    .filter(Boolean)
    .join(", ");

  const interactive = Boolean(onPress) || Boolean(expandable);
  return (
    <View style={[styles.card, { backgroundColor: theme.colors.surface, borderColor: theme.colors.outline }]}>
      <Pressable
        disabled={!interactive}
        onPress={onPress ?? (() => setOpen((v) => !v))}
        accessibilityRole={interactive ? "button" : "text"}
        accessibilityLabel={spoken}
        accessibilityState={expandable && !onPress ? { expanded: open } : undefined}
        style={styles.head}
      >
        <View style={styles.row}>
          <Text variant="titleMedium">{showTeacher ? request.teacherName : request.leaveType}</Text>
          <View style={[styles.badge, { backgroundColor: look.background }]}>
            <Text variant="labelMedium" style={{ color: look.textColor }}>
              {look.label}
            </Text>
          </View>
        </View>
        {showTeacher ? <Text variant="bodyMedium">{request.schoolName} · {request.leaveType}</Text> : null}
        <Text variant="bodyMedium">{dates}</Text>
        <Text variant="bodySmall">
          {request.workingDays} working days{halves ? ` · ${halves}` : ""}
        </Text>
        {decision ? <Text variant="bodySmall">{decision}</Text> : null}
      </Pressable>
      {showDetails ? (
        <View style={styles.details}>
          {request.reason ? <Text variant="bodyMedium">Reason: {request.reason}</Text> : null}
          {request.decisionNote ? (
            <Text variant="bodyMedium">
              {request.status === "REJECTED" ? "Rejection reason" : request.status === "CANCELLED" ? "Reason" : "Note"}:{" "}
              {request.decisionNote}
            </Text>
          ) : null}
        </View>
      ) : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  card: { borderWidth: StyleSheet.hairlineWidth, borderRadius: 8, overflow: "hidden" },
  head: { padding: spacingUnit * 1.5, gap: spacingUnit / 2, minHeight: minTouchTarget },
  row: { flexDirection: "row", justifyContent: "space-between", alignItems: "center", gap: spacingUnit },
  badge: { paddingHorizontal: spacingUnit, paddingVertical: 2, borderRadius: 12 },
  details: { paddingHorizontal: spacingUnit * 1.5, paddingBottom: spacingUnit * 1.5, gap: spacingUnit / 2 },
});
