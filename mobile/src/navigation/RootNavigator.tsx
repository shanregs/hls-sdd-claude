import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { ActivityIndicator, StyleSheet, View } from "react-native";
import { useAuth } from "../auth/AuthProvider";
import { NoConnectionScreen } from "../screens/NoConnectionScreen";
import { ResetPasswordScreen } from "../screens/ResetPasswordScreen";
import { SignInScreen, type RootStackParamList } from "../screens/SignInScreen";
import { UpdateRequiredScreen } from "../screens/UpdateRequiredScreen";
import { AppShell } from "./AppShell";

const Stack = createNativeStackNavigator<RootStackParamList>();

/**
 * Chooses what the user sees from the authentication state alone: restoring, update required,
 * no connection, signed in, or the signed-out sign-in and reset screens. Signing out resets the
 * whole tree, so going back can never return to a signed-in screen (spec FR-006).
 */
export function RootNavigator() {
  const { state } = useAuth();

  switch (state.status) {
    case "restoring":
      return (
        <View style={styles.center} accessibilityLabel="Loading">
          <ActivityIndicator size="large" />
        </View>
      );
    case "updateRequired":
      return <UpdateRequiredScreen minimumVersion={state.minimumVersion} />;
    case "noConnection":
      return <NoConnectionScreen />;
    case "signedIn":
      return (
        <NavigationContainer>
          <AppShell user={state.user} />
        </NavigationContainer>
      );
    case "signedOut":
      return (
        <NavigationContainer>
          <Stack.Navigator screenOptions={{ headerShown: false }} initialRouteName="SignIn">
            <Stack.Screen name="SignIn" component={SignInScreen} />
            <Stack.Screen name="ResetPassword" component={ResetPasswordScreen} />
          </Stack.Navigator>
        </NavigationContainer>
      );
  }
}

const styles = StyleSheet.create({
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
});
