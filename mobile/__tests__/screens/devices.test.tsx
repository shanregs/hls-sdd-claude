import { fireEvent, render, screen, waitFor, within } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";
import { openDevices as goToDevices } from "../support/navigation";

let server: FakeServer;

const sessions = [
  {
    id: "s-android",
    deviceDescription: "HLS-Android/1.0.0 (Android 14; Pixel 7)",
    signedInAt: "2026-10-04T08:00:00Z",
    lastActivityAt: "2026-10-04T09:30:00Z",
    current: true,
    clientType: "ANDROID",
    appVersion: "1.0.0",
  },
  {
    id: "s-web",
    deviceDescription: "Mozilla/5.0",
    signedInAt: "2026-10-03T06:00:00Z",
    lastActivityAt: "2026-10-03T07:00:00Z",
    current: false,
    clientType: "WEB",
    appVersion: null,
  },
];

async function openDevices(list = sessions) {
  server = newServer();
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  server.on("GET /api/v1/me/sessions", { status: 200, body: list });
  await render(<App />);
  await screen.findByText("Welcome, Tara");
  await goToDevices();
  await screen.findByText("These are the places you are signed in to HLS.");
}

describe("Signed-in devices", () => {
  it("lists app and web sessions, marks the current one, and offers to end only the others", async () => {
    await openDevices();

    expect(await screen.findByText("HLS Android app 1.0.0")).toBeTruthy();
    expect(screen.getByText("Web browser")).toBeTruthy();
    expect(screen.getByText("This device")).toBeTruthy();
    expect(screen.getAllByText("End session")).toHaveLength(1);
    expect(screen.getByLabelText("End session on Web browser")).toBeTruthy();
    expect(screen.queryByLabelText("End session on HLS Android app 1.0.0")).toBeNull();
  });

  it("shows dates as DD/MM/YYYY HH:mm", async () => {
    await openDevices();

    await screen.findByText("HLS Android app 1.0.0");
    expect(screen.getAllByText(/Signed in \d{2}\/\d{2}\/\d{4} \d{2}:\d{2}/).length).toBe(2);
  });

  it("ends another session after confirmation and refreshes the list", async () => {
    await openDevices();
    server.on("DELETE /api/v1/me/sessions/s-web", { status: 204 });
    let afterEnd = false;
    server.on("GET /api/v1/me/sessions", () => ({ status: 200, body: afterEnd ? [sessions[0]] : sessions }));

    await fireEvent.press(await screen.findByLabelText("End session on Web browser"));
    const dialog = await screen.findByText("End this session?");
    expect(dialog).toBeTruthy();
    afterEnd = true;
    const confirm = within(screen.getByText("End this session?").parent!.parent!.parent!).getAllByText("End session");
    await fireEvent.press(confirm[confirm.length - 1]);

    await waitFor(() => expect(server.callsTo("DELETE /api/v1/me/sessions/s-web")).toHaveLength(1));
    await waitFor(() => expect(screen.queryByText("Web browser")).toBeNull());
  });

  it("does nothing when the confirmation is cancelled", async () => {
    await openDevices();

    await fireEvent.press(await screen.findByLabelText("End session on Web browser"));
    await screen.findByText("End this session?");
    await fireEvent.press(screen.getByText("Cancel"));

    expect(server.callsTo("DELETE /api/v1/me/sessions/s-web")).toHaveLength(0);
  });

  it("shows a no-connection message and a way to try again when the list cannot be loaded", async () => {
    server = newServer();
    await SecureStore.setItemAsync("hls.renewalCredential", "stored");
    server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
    await render(<App />);
    await screen.findByText("Welcome, Tara");
    server.offline = true;

    await goToDevices();

    await screen.findByText("No connection. Check your internet and try again.");
    expect(screen.getByText("Try again")).toBeTruthy();
  });
});
