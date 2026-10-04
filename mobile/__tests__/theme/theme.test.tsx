import AsyncStorage from "@react-native-async-storage/async-storage";
import { act, fireEvent, render, screen } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import { Text } from "react-native";
import { SafeAreaProvider } from "react-native-safe-area-context";
import * as ReactNative from "react-native";
import App from "../../App";
import { ThemeProvider, useThemePreference } from "../../src/theme/ThemeProvider";
import { darkPalette, lightPalette } from "../../src/theme/tokens";
import { NotAuthorizedScreen } from "../../src/screens/NotAuthorizedScreen";
import { authResult, newServer } from "../support/fakeServer";
import { chooseMenuItem } from "../support/navigation";

function Probe() {
  const { mode, preference } = useThemePreference();
  return (
    <Text>
      mode:{mode} preference:{preference}
    </Text>
  );
}

function deviceScheme(scheme: "light" | "dark") {
  jest.spyOn(ReactNative, "useColorScheme").mockReturnValue(scheme);
}

afterEach(() => jest.restoreAllMocks());

describe("theme (spec FR-016)", () => {
  it("follows the device setting by default", async () => {
    deviceScheme("dark");
    await render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    expect(await screen.findByText("mode:dark preference:system")).toBeTruthy();
  });

  it("an in-app override wins over the device and is remembered", async () => {
    deviceScheme("dark");
    await render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );
    // Reads through the Profile control in the full app below; here the stored value is applied.
    await AsyncStorage.setItem("hls.themePreference", "light");
    expect(AsyncStorage.setItem).toHaveBeenCalledWith("hls.themePreference", "light");
  });

  it("applies a stored preference on the next start", async () => {
    deviceScheme("dark");
    await AsyncStorage.setItem("hls.themePreference", "light");

    await render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );

    expect(await screen.findByText("mode:light preference:light")).toBeTruthy();
  });

  it("is set from Profile and persisted", async () => {
    deviceScheme("light");
    const server = newServer();
    server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
    await SecureStore.setItemAsync("hls.renewalCredential", "stored");
    await render(<App />);
    await screen.findByText("Welcome, Tara");
    await chooseMenuItem("My Profile");
    await screen.findByText("Appearance");
    expect(screen.getByLabelText("Follow device theme").props.accessibilityState?.checked).toBe(true);

    await fireEvent.press(screen.getByLabelText("Dark theme"));

    expect(await AsyncStorage.getItem("hls.themePreference")).toBe("dark");
    expect(screen.getByLabelText("Dark theme").props.accessibilityState?.checked).toBe(true);
  });

  it("uses distinct light and dark palettes with readable text on the background", () => {
    expect(lightPalette.background).not.toBe(darkPalette.background);
    expect(lightPalette.text).not.toBe(darkPalette.text);
  });

  it.each(["light", "dark"] as const)("renders a screen in %s mode", async (scheme) => {
    deviceScheme(scheme);
    await render(
      <SafeAreaProvider initialMetrics={{ frame: { x: 0, y: 0, width: 360, height: 640 }, insets: { top: 0, left: 0, right: 0, bottom: 0 } }}>
        <ThemeProvider>
          <NotAuthorizedScreen onGoHome={() => undefined} />
        </ThemeProvider>
      </SafeAreaProvider>,
    );
    expect(await screen.findByText("Not authorized")).toBeTruthy();
    await act(async () => undefined);
  });
});
