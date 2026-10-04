import { StyleSheet, View } from "react-native";
import { Card, Chip, Text, useTheme } from "react-native-paper";
import type { AccessModel } from "../api/accessModelApi";
import type { SignedInUser } from "../api/authApi";
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
export function HomeScreen({ user, model }: { user: SignedInUser; model: AccessModel }) {
  const theme = useTheme();
  const sections = model.navigation.map((s) => s.section).filter((name) => !NON_BUSINESS_SECTIONS.has(name));
  const bar = { backgroundColor: theme.colors.surfaceVariant };

  return (
    <Screen>
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

const styles = StyleSheet.create({
  roles: { flexDirection: "row", flexWrap: "wrap", gap: spacingUnit },
  skeleton: { gap: spacingUnit, paddingBottom: spacingUnit },
  line: { height: 12, borderRadius: 6 },
  wide: { width: "80%" },
  narrow: { width: "50%" },
});
