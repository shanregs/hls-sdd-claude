import { render, screen, within } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import App from "../../App";
import { buildMenu } from "../../src/access/useMenu";
import {
  ADMIN_TEACHER_MODEL,
  DIRECTOR_MODEL,
  MANAGER_MODEL,
  TEACHER_MODEL,
  WITH_UNKNOWN_ITEMS,
} from "../support/fixtures";
import { authResult, newServer, type FakeServer } from "../support/fakeServer";
import { openMenu } from "../support/navigation";
import type { AccessModel } from "../../src/api/accessModelApi";

const labels = (model: AccessModel) => buildMenu(model).flatMap((s) => s.items.map((i) => i.label));
const sections = (model: AccessModel) => buildMenu(model).map((s) => s.section);

describe("buildMenu (spec FR-009 to FR-011)", () => {
  it("shows only items the app has a screen for, for a Teacher, Manager and Director", () => {
    expect(labels(TEACHER_MODEL)).toEqual([
      "Dashboard",
      "My Attendance",
      "Attendance History",
      "Holiday Calendar",
      "Apply Leave",
      "My Leave History",
      "My Profile",
    ]);
    expect(labels(MANAGER_MODEL)).toEqual([
      "Dashboard",
      "Holiday Calendar",
      "Teacher Attendance",
      "Leave Management",
      "Profile",
    ]);
    expect(labels(DIRECTOR_MODEL)).toEqual(["Dashboard", "Holiday Calendar", "Leave Management", "Profile"]);
  });

  it("drops sections left empty by the filter", () => {
    expect(sections(TEACHER_MODEL)).toEqual(["Dashboard", "MY ATTENDANCE", "MASTER DATA", "LEAVE", "ACCOUNT"]);
    expect(sections(DIRECTOR_MODEL)).toEqual(["Dashboard", "MASTER DATA", "OPERATIONS", "ACCOUNT"]);
  });

  it("is the union of both roles with no duplicate entries", () => {
    const menu = buildMenu(ADMIN_TEACHER_MODEL);
    const routes = menu.flatMap((s) => s.items.map((i) => i.route));

    expect(routes).toEqual([
      "/dashboard",
      "/my-attendance",
      "/master-data/holiday-calendar",
      "/leave/apply",
      "/leave/history",
      "/account/profile",
    ]);
    expect(new Set(routes).size).toBe(routes.length);
  });

  it("ignores items and sections the app does not know yet, without error", () => {
    expect(labels(WITH_UNKNOWN_ITEMS)).toEqual(["Dashboard", "My Profile"]);
    expect(sections(WITH_UNKNOWN_ITEMS)).toEqual(["Dashboard", "ACCOUNT"]);
  });

  it("returns nothing for a missing model and keeps the server order", () => {
    expect(buildMenu(null)).toEqual([]);
    expect(buildMenu(MANAGER_MODEL).map((s) => s.section)).toEqual([
      "Dashboard",
      "MASTER DATA",
      "OPERATIONS",
      "ACCOUNT",
    ]);
  });
});

let server: FakeServer;

async function signedInAs(model: AccessModel) {
  server = newServer();
  server.on("GET /api/v1/me/access-model", { status: 200, body: model });
  server.on("POST /api/v1/auth/renew", { status: 200, body: authResult() });
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  await render(<App />);
  await screen.findByText("Welcome, Tara");
  await openMenu();
  await screen.findByText("ACCOUNT");
}

describe("the drawer shows exactly the server navigation", () => {
  it.each([
    [
      "Teacher",
      TEACHER_MODEL,
      [
        "Dashboard",
        "My Attendance",
        "Attendance History",
        "Holiday Calendar",
        "Apply Leave",
        "My Leave History",
        "My Profile",
        "Log out",
      ],
    ],
    [
      "Manager",
      MANAGER_MODEL,
      ["Dashboard", "Holiday Calendar", "Teacher Attendance", "Leave Management", "Profile", "Log out"],
    ],
    ["Director", DIRECTOR_MODEL, ["Dashboard", "Holiday Calendar", "Leave Management", "Profile", "Log out"]],
    [
      "Admin plus Teacher",
      ADMIN_TEACHER_MODEL,
      ["Dashboard", "My Attendance", "Holiday Calendar", "Apply Leave", "My Leave History", "Profile", "Log out"],
    ],
  ])("for a %s", async (_name, model, expected) => {
    await signedInAs(model as AccessModel);

    const items = screen
      .getAllByLabelText(
        /^(Dashboard|Profile|My Profile|Log out|My Attendance|Attendance History|Teacher Attendance|Holiday Calendar|Apply Leave|My Leave History|Leave Management)$/,
      )
      .map((n) => n.props.accessibilityLabel);
    expect([...new Set(items)]).toEqual(expected);
    // Nothing the server offered but the app has no screen for is shown, and nothing is disabled.
    for (const hidden of [
      "Schools",
      "Teachers",
      "Zones",
      "User Management",
      "Audit Logs",
      "Settings",
    ]) {
      expect(screen.queryByText(hidden)).toBeNull();
    }
  });

  it("never lists an item twice for a user with several roles", async () => {
    await signedInAs(ADMIN_TEACHER_MODEL);

    expect(screen.getAllByLabelText("Profile")).toHaveLength(1);
    expect(screen.queryByLabelText("My Profile")).toBeNull();
    expect(screen.getAllByLabelText("Dashboard")).toHaveLength(1);
  });

  it("always offers Log out under ACCOUNT", async () => {
    await signedInAs(TEACHER_MODEL);

    expect(screen.getByText("ACCOUNT")).toBeTruthy();
    expect(screen.getByLabelText("Log out")).toBeTruthy();
    expect(within(screen.getByLabelText("Log out").parent!.parent!.parent!).queryByText("ACCOUNT")).toBeTruthy();
  });
});
