import { fireEvent, render, screen } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import type { AccessModel } from "../../src/api/accessModelApi";
import { resetServerClock } from "../../src/attendance/serverClock";
import { installAttendance, type AttendanceFake } from "./attendanceServer";
import { authResult, newServer, type FakeServer } from "./fakeServer";
import { TEACHER_MODEL } from "./fixtures";
import { chooseMenuItem } from "./navigation";

export interface AppSetup {
  model?: AccessModel;
  /** The server's date ("YYYY-MM-DD"); the server clock is 09:30 India time that day. */
  today?: string;
  /** How far the phone clock is from the server clock, in hours (a wrong phone clock). */
  phoneOffsetHours?: number;
  attendance?: Partial<AttendanceFake>;
  /** Runs after the attendance routes are installed, to replace or add a route. */
  prepare?: (server: FakeServer, attendance: AttendanceFake) => void;
}

export interface OpenedApp {
  server: FakeServer;
  attendance: AttendanceFake;
}

/** Signs in with a stored session, then opens a screen from the drawer by its menu label. */
export async function openAttendanceScreen(menuLabel: string, setup: AppSetup = {}): Promise<OpenedApp> {
  resetServerClock();
  const today = setup.today ?? "2026-10-05";
  const serverTime = new Date(`${today}T04:00:00Z`);
  const phoneTime = new Date(serverTime.getTime() + (setup.phoneOffsetHours ?? 0) * 3_600_000);
  jest.spyOn(Date, "now").mockReturnValue(phoneTime.getTime());

  const server = newServer();
  server.on("GET /api/v1/me/access-model", {
    status: 200,
    body: setup.model ?? TEACHER_MODEL,
    headers: { Date: serverTime.toUTCString() },
  });
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  const attendance = installAttendance(server, setup.attendance);
  setup.prepare?.(server, attendance);

  await render(<App />);
  await screen.findByText("Welcome, Tara");
  await chooseMenuItem(menuLabel);
  return { server, attendance };
}

/** Opens a day by its spoken label, for example "Monday 5 October 2026, not marked". */
export async function openDay(label: string) {
  await fireEvent.press(await screen.findByLabelText(label));
}

export async function closeSheet() {
  await fireEvent.press(screen.getByLabelText("Close"));
}
