import { StyleSheet, View } from "react-native";
import { Card, Chip, Text, useTheme } from "react-native-paper";
import { screenFor } from "../access/screenRegistry";
import type { AccessModel } from "../api/accessModelApi";
import type { SignedInUser } from "../api/authApi";
import { usePendingCount } from "../leave/usePendingCount";
import type { OpenRoute } from "../navigation/AppShell";
import { spacingUnit } from "../theme/tokens";
import { Screen } from "./Screen";

/** Sections that are the home and the account area themselves, not business areas to preview. */
const NON_BUSINESS_SECTIONS = new Set(["Dashboard", "ACCOUNT"]);

/** Display label only: the code the server sent, with the first letter capital and the rest lower case. */
const roleLabel = (role: string) => role.charAt(0) + role.slice(1).toLowerCase();

/**
 * Placeholder home (spec FR-015): the user's name, their roles, and one skeleton card for each
 * business section of their server-provided menu. The content is the same for every role; nothing
 * here is chosen by role name. Later mobile specs replace the skeletons with real widgets.
 */
/** The route the server's menu uses for Leave Management; the widget exists only when the menu offers it. */
const LEAVE_MANAGEMENT_ROUTE = "/operations/leave";

export function HomeScreen({ user, model, openRoute }: { user: SignedInUser; model: AccessModel; openRoute: OpenRoute }) {
  const theme = useTheme();
  // A section that already has a screen in the app is reached from the menu, so it is not previewed.
  const sections = model.navigation
    .filter((s) => !NON_BUSINESS_SECTIONS.has(s.section) && !s.items.some((item) => screenFor(item.route)))
    .map((s) => s.section);
  const bar = { backgroundColor: theme.colors.surfaceVariant };
  const offersLeave = model.navigation.some((section) => section.items.some((item) => item.route === LEAVE_MANAGEMENT_ROUTE));
  const pending = usePendingCount(offersLeave);

  return (
    <Screen onRefresh={pending.reload}>
      <Text variant="headlineMedium" accessibilityRole="header">
        Welcome, {user.displayName}
      </Text>
      <View style={styles.roles} accessibilityLabel={`Roles: ${model.roles.join(", ")}`}>
        {model.roles.map((role) => (
          <Chip key={role} compact accessibilityLabel={`Role ${roleLabel(role)}`}>
            {roleLabel(role)}
          </Chip>
        ))}
      </View>

      {offersLeave ? (
        <Card
          onPress={() => openRoute(LEAVE_MANAGEMENT_ROUTE, { status: "PENDING" })}
          accessibilityRole="button"
          accessibilityLabel={pendingText(pending)}
        >
          <Card.Title title="Leave" subtitle={pendingText(pending)} />
        </Card>
      ) : null}

      {sections.map((name) => (
        <Card key={name} accessibilityLabel={`${name}, coming to the app soon`}>
          <Card.Title title={name} subtitle="Coming to the app soon" />
          <Card.Content style={styles.skeleton}>
            <View style={[styles.line, styles.wide, bar]} />
            <View style={[styles.line, styles.narrow, bar]} />
          </Card.Content>
        </Card>
      ))}
    </Screen>
  );
}

function pendingText(pending: ReturnType<typeof usePendingCount>): string {
  if (pending.state === "ready") {
    return pending.count === 0 ? "No pending leave requests" : `Pending leave requests: ${pending.count}`;
  }
  if (pending.state === "error") return "Could not load pending leave requests";
  return "Loading pending leave requests";
}

const styles = StyleSheet.create({
  roles: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  skeleton: { gap: spacingUnit, paddingBottom: spacingUnit },
  line: { height: 12, borderRadius: 6 },
  wide: { width: "80%" },
  narrow: { width: "50%" },
});
