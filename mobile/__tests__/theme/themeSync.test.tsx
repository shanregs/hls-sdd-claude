import AsyncStorage from "@react-native-async-storage/async-storage";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import { Text } from "react-native";
import * as ReactNative from "react-native";
import App from "../../App";
import { ThemeProvider, useThemePreference } from "../../src/theme/ThemeProvider";
import { authResult, newServer } from "../support/fakeServer";
import type { FakeServer } from "../support/fakeServer";
import { chooseMenuItem } from "../support/navigation";
import { CATALOGUE, savedTheme, serveTheme, type ThemeRow } from "../support/themeServer";

function Probe() {
  const { mode, saved } = useThemePreference();
  return (
    <Text>
      mode:{mode} preset:{saved.preset} accent:{saved.customAccent ?? "none"}
    </Text>
  );
}

function deviceScheme(scheme: "light" | "dark") {
  jest.spyOn(ReactNative, "useColorScheme").mockReturnValue(scheme);
}

const netinfo = () => jest.requireMock("@react-native-community/netinfo");

let server: FakeServer;
let row: ThemeRow;

async function openProfile() {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  await render(<App />);
  await screen.findByText("Welcome, Tara");
  await chooseMenuItem("My Profile");
  await screen.findByText("Appearance");
}

const checked = (label: string) => screen.getByLabelText(label).props.accessibilityState?.checked;

beforeEach(() => {
  deviceScheme("light");
  server = newServer();
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  row = serveTheme(server);
});

afterEach(() => jest.restoreAllMocks());

describe("the saved theme on the phone (spec 039 US1, US4)", () => {
  it("applies the cached theme before the server is asked, also on the sign-in screen", async () => {
    await AsyncStorage.setItem(
      "hls.theme",
      JSON.stringify({ saved: savedTheme({ preset: "teal-coral", mode: "dark" }), catalogue: CATALOGUE }),
    );
    server.offline = true;

    await render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );

    expect(await screen.findByText("mode:dark preset:teal-coral accent:none")).toBeTruthy();
    expect(server.calls).toHaveLength(0);
  });

  it("starts in the device theme with no cache and no connection", async () => {
    server.offline = true;
    deviceScheme("dark");

    await render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    );

    expect(await screen.findByText("mode:dark preset:classic accent:none")).toBeTruthy();
  });

  it("loads the saved theme after sign-in", async () => {
    row.current = savedTheme({ preset: "teal-coral", mode: "dark", changedAt: "2026-10-27T10:00:00.000Z" });

    await openProfile();

    await waitFor(() => expect(checked("Teal and Coral theme")).toBe(true));
    expect(checked("Dark theme")).toBe(true);
    expect(checked("Classic theme")).toBe(false);
  });

  it("saves a choice made on the phone, with the variant in use", async () => {
    await openProfile();

    await fireEvent.press(screen.getByLabelText("Teal and Coral theme"));

    await waitFor(() => expect(row.current?.preset).toBe("teal-coral"));
    const put = server.callsTo("PUT /api/v1/me/theme").at(-1)!;
    expect(put.body).toMatchObject({ preset: "teal-coral", mode: "device", variant: "light" });
    expect(await AsyncStorage.getItem("hls.theme")).toContain("teal-coral");
    expect(await AsyncStorage.getItem("hls.theme.pending")).toBeNull();
  });

  it("keeps a change made offline and sends it when the connection is back", async () => {
    await openProfile();
    server.offline = true;

    await fireEvent.press(screen.getByLabelText("Dark theme"));

    expect(await screen.findByText("Your change will be saved when you are back online.")).toBeTruthy();
    expect(checked("Dark theme")).toBe(true);
    expect(await AsyncStorage.getItem("hls.theme.pending")).toContain("dark");

    server.offline = false;
    await act(async () => netinfo().__emit({ isConnected: true }));

    await waitFor(() => expect(row.current?.mode).toBe("dark"));
    await waitFor(() => expect(screen.queryByText("Your change will be saved when you are back online.")).toBeNull());
    expect(await AsyncStorage.getItem("hls.theme.pending")).toBeNull();
  });

  it("sends a change made before the app was closed once the user signs in again", async () => {
    await AsyncStorage.setItem(
      "hls.theme.pending",
      JSON.stringify({
        saved: savedTheme({ preset: "teal-coral", mode: "dark", changedAt: "2026-10-27T09:00:00.000Z" }),
        userId: "u-1",
        variant: "light",
      }),
    );

    await openProfile();

    await waitFor(() => expect(row.current?.preset).toBe("teal-coral"));
    expect(checked("Teal and Coral theme")).toBe(true);
  });

  it("lets a newer saved row win over a waiting older change", async () => {
    row.current = savedTheme({ preset: "teal-coral", mode: "light", changedAt: "2026-10-27T12:00:00.000Z" });
    await AsyncStorage.setItem(
      "hls.theme.pending",
      JSON.stringify({
        saved: savedTheme({ preset: "classic", mode: "dark", changedAt: "2026-10-27T09:00:00.000Z" }),
        userId: "u-1",
        variant: "light",
      }),
    );

    await openProfile();

    await waitFor(() => expect(checked("Teal and Coral theme")).toBe(true));
    expect(checked("Light theme")).toBe(true);
    expect(row.current?.preset).toBe("teal-coral");
    expect(await AsyncStorage.getItem("hls.theme.pending")).toBeNull();
  });

  it("drops a waiting change that belongs to another user", async () => {
    await AsyncStorage.setItem(
      "hls.theme.pending",
      JSON.stringify({
        saved: savedTheme({ preset: "teal-coral", changedAt: "2026-10-27T09:00:00.000Z" }),
        userId: "someone-else",
        variant: "light",
      }),
    );

    await openProfile();

    await waitFor(() => expect(checked("Classic theme")).toBe(true));
    expect(server.callsTo("PUT /api/v1/me/theme")).toHaveLength(0);
  });

  it("uploads the old light or dark choice once for a user with no saved theme", async () => {
    await AsyncStorage.setItem("hls.themePreference", "dark");

    await openProfile();

    await waitFor(() => expect(row.current?.mode).toBe("dark"));
    expect(await AsyncStorage.getItem("hls.themePreference")).toBeNull();
  });
});

describe("own accent colour on the phone (spec 039 US2, US3)", () => {
  it("flags an unreadable colour, offers an adjusted one, and saves it once accepted", async () => {
    await openProfile();

    await fireEvent.changeText(screen.getByLabelText("Accent colour"), "#FFEB3B");

    expect(await screen.findByText(/#FFEB3B is hard to read in light mode/)).toBeTruthy();
    expect(screen.getByLabelText("Use this colour").props.accessibilityState?.disabled).toBe(true);
    await fireEvent.press(screen.getByLabelText("Use #786C00"));
    expect(screen.getByLabelText("Accent colour").props.value).toBe("#786C00");
    await fireEvent.press(screen.getByLabelText("Use this colour"));

    await waitFor(() => expect(row.current?.customAccent).toBe("#786C00"));
  });

  it("takes a swatch into the field and shows the ratios for a readable colour", async () => {
    await openProfile();

    await fireEvent.press(screen.getByLabelText("Teal #00796B"));

    expect(screen.getByLabelText("Accent colour").props.value).toBe("#00796B");
    expect(await screen.findByText(/Easy to read in light mode/)).toBeTruthy();
  });

  it("flags text that is not a colour", async () => {
    await openProfile();

    await fireEvent.changeText(screen.getByLabelText("Accent colour"), "blue");

    expect(await screen.findByText("Enter a colour like #1E5AA8.")).toBeTruthy();
    expect(screen.getByLabelText("Use this colour").props.accessibilityState?.disabled).toBe(true);
  });

  it("reverts and reports a colour the server refuses", async () => {
    server.on("PUT /api/v1/me/theme", {
      status: 422,
      body: { code: "ACCENT_UNREADABLE", reason: "That colour is hard to read.", suggested: "#123456" },
    });
    await openProfile();

    await fireEvent.changeText(screen.getByLabelText("Accent colour"), "#1E5AA8");
    await fireEvent.press(screen.getByLabelText("Use this colour"));

    expect(await screen.findByText("That colour is hard to read. Try #123456.")).toBeTruthy();
    expect(row.current).toBeNull();
  });

  it("clears a saved accent", async () => {
    row.current = savedTheme({ customAccent: "#1E5AA8", changedAt: "2026-10-27T10:00:00.000Z" });
    await openProfile();
    await waitFor(() => expect(screen.getByLabelText("Accent colour").props.value).not.toBeUndefined());

    await fireEvent.press(screen.getByLabelText("Clear accent colour"));

    await waitFor(() => expect(row.current?.customAccent).toBeNull());
  });

  it("previews the theme with its contrast ratios and status chips", async () => {
    await openProfile();

    expect(screen.getByLabelText("Theme preview")).toBeTruthy();
    expect(screen.getByText("Primary button")).toBeTruthy();
    expect(screen.getByText("Approved")).toBeTruthy();
    expect(screen.getByText("Pending")).toBeTruthy();
    expect(screen.getByText("Rejected")).toBeTruthy();
    expect(screen.getAllByText(/\(passes\)/)).toHaveLength(3);
  });

  it("gives every control a 48 dp touch target", async () => {
    await openProfile();

    for (const label of ["Use this colour", "Clear accent colour", "Reset to default"]) {
      const style = ReactNative.StyleSheet.flatten(screen.getByLabelText(label).props.style);
      expect(style?.minHeight ?? 48).toBeGreaterThanOrEqual(48);
    }
    const swatch = ReactNative.StyleSheet.flatten(screen.getByLabelText("Blue #1E5AA8").props.style);
    expect(swatch?.width).toBeGreaterThanOrEqual(48);
    expect(swatch?.height).toBeGreaterThanOrEqual(48);
  });

  it("resets only after a confirmation", async () => {
    row.current = savedTheme({ preset: "teal-coral", mode: "dark", changedAt: "2026-10-27T10:00:00.000Z" });
    await openProfile();
    await waitFor(() => expect(checked("Teal and Coral theme")).toBe(true));

    await fireEvent.press(screen.getByLabelText("Reset to default"));
    await fireEvent.press(await screen.findByLabelText("Cancel reset"));
    expect(server.callsTo("DELETE /api/v1/me/theme")).toHaveLength(0);

    await fireEvent.press(screen.getByLabelText("Reset to default"));
    await fireEvent.press(await screen.findByLabelText("Confirm reset"));

    await waitFor(() => expect(row.current).toBeNull());
    await waitFor(() => expect(checked("Classic theme")).toBe(true));
    expect(checked("Follow device theme")).toBe(true);
  });
});
