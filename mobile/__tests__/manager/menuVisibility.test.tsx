import { readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";
import { fireEvent, screen } from "@testing-library/react-native";
import { canOpenRoute } from "../../src/access/useMenu";
import { openAttendanceScreen } from "../support/attendanceApp";
import { DIRECTOR_MODEL, MANAGER_MODEL, TEACHER_MODEL } from "../support/fixtures";
import { managerDay, managerFieldModel, managerLeave, managerMonth, monthDay, regularization, today } from "../support/managerFixtures";
import { openManagerScreen } from "../support/managerApp";
import { openMenu } from "../support/navigation";

/** Spec 033 T044 (US7): the screens and their buttons exist only because the server's access model says so. */
const MANAGER_ROUTES = ["/manager-attendance/me", "/manager-attendance/me/month", "/manager-attendance/me/leave"];

async function menuLabels(): Promise<string[]> {
  await openMenu();
  await screen.findByLabelText("Log out");
  return ["Check-in", "My Month", "My Leave"].filter((label) => screen.queryByLabelText(label) !== null);
}

describe("the menu", () => {
  it("shows a Manager Check-in, My Month and My Leave under MY ATTENDANCE", async () => {
    await openManagerScreen(null);

    expect(await menuLabels()).toEqual(["Check-in", "My Month", "My Leave"]);
    expect(screen.getByText("MY ATTENDANCE")).toBeTruthy();
  });

  it.each([
    ["a Teacher", TEACHER_MODEL],
    ["a Director", DIRECTOR_MODEL],
    ["a Manager whose menu lacks them", MANAGER_MODEL],
  ])("shows %s none of the three", async (_who, model) => {
    await openAttendanceScreen(null, { model });

    expect(await menuLabels()).toEqual([]);
  });

  it("does not let anyone open a Manager route their menu lacks", () => {
    for (const route of MANAGER_ROUTES) {
      expect(canOpenRoute(TEACHER_MODEL, route)).toBe(false);
      expect(canOpenRoute(DIRECTOR_MODEL, route)).toBe(false);
      expect(canOpenRoute(managerFieldModel(), route)).toBe(true);
    }
    const noMonth = managerFieldModel();
    noMonth.navigation = noMonth.navigation.map((section) => ({
      ...section,
      items: section.items.filter((item) => item.route !== "/manager-attendance/me/month"),
    }));
    expect(canOpenRoute(noMonth, "/manager-attendance/me/month")).toBe(false);
  });
});

describe("without the Create action", () => {
  const model = managerFieldModel({ create: false });

  it("Check-in shows no form and no Check out, Complete check-out or Add photo", async () => {
    await openManagerScreen("Check-in", {
      model,
      manager: { today: today({ day: managerDay({ hasPhoto: false }), missingCheckOutDates: ["2026-10-02"] }) },
    });

    await screen.findByText("Checked in at 09:42, Office");
    for (const label of ["Check in", "Check out", "Complete check-out for 02/10/2026"]) {
      expect(screen.queryByLabelText(label)).toBeNull();
    }
    expect(screen.queryByText("Add photo")).toBeNull();
    expect(await screen.findByText("No check-out on 02/10/2026")).toBeTruthy();
  });

  it("Check-in says the Manager may not check in, rather than showing a form the server would refuse", async () => {
    await openManagerScreen("Check-in", { model });

    expect(await screen.findByText("You are not allowed to check in.")).toBeTruthy();
    expect(screen.queryByLabelText("Check in")).toBeNull();
  });

  it("My Leave shows the list but no request form", async () => {
    await openManagerScreen("My Leave", { model, manager: { leaves: [managerLeave()] } });

    expect(await screen.findByText("Your requests")).toBeTruthy();
    expect(screen.queryByLabelText("Submit")).toBeNull();
  });

  it("My Month offers neither Complete check-out nor Ask to add a check-in", async () => {
    const days = [
      monthDay("2026-10-02", { status: "FIELD", dayType: "FIELD", checkInAt: "2026-10-02T04:12:00Z", flags: ["NO_CHECK_OUT"] }),
      monthDay("2026-10-06", { status: "ABSENT" }),
    ];
    await openManagerScreen("My Month", { model, manager: { months: { "2026-10": managerMonth({ days }) } } });

    for (const label of [
      "Friday 2 October 2026, field day, checked in 09:42, field, no check-out",
      "Tuesday 6 October 2026, absent",
    ]) {
      await fireEvent.press(await screen.findByLabelText(label));
      await screen.findByLabelText("Close");
      expect(screen.queryByText("Complete check-out")).toBeNull();
      expect(screen.queryByText("Ask to add a check-in")).toBeNull();
      await fireEvent.press(screen.getByLabelText("Close"));
    }
  });
});

describe("without the Delete action", () => {
  it("hides every Cancel while the screens still load", async () => {
    const model = managerFieldModel({ remove: false });
    await openManagerScreen("My Leave", { model, manager: { leaves: [managerLeave()] } });
    await screen.findByLabelText(/Casual Leave, 12\/10\/2026 – 14\/10\/2026, Pending/);
    expect(screen.queryByText("Cancel request")).toBeNull();

    await openManagerScreen("My Month", {
      model,
      manager: { months: { "2026-10": managerMonth({ days: [] }) }, regularizations: [regularization()] },
    });
    await screen.findByLabelText(/Check-in for 02\/10\/2026 at 09:15, Pending/);
    expect(screen.queryByText("Cancel request")).toBeNull();
  });
});

describe("no role decides anything in the Manager code", () => {
  function files(dir: string): string[] {
    return readdirSync(dir).flatMap((name) => {
      const path = join(dir, name);
      if (statSync(path).isDirectory()) return files(path);
      return /\.(ts|tsx)$/.test(name) ? [path] : [];
    });
  }
  const src = join(__dirname, "..", "..", "src");
  const mine = [
    ...files(join(src, "manager")),
    join(src, "screens", "ManagerCheckInScreen.tsx"),
    join(src, "screens", "ManagerMonthScreen.tsx"),
    join(src, "screens", "ManagerLeaveScreen.tsx"),
    join(src, "api", "managerAttendanceApi.ts"),
    join(src, "api", "managerLeaveApi.ts"),
    join(src, "attendance", "managerDayPresentation.ts"),
  ];

  it.each(mine.map((f) => [f.replace(/.*[\\/]src[\\/]/, "src/"), f]))("%s never reads the user's roles", (_name, file) => {
    const text = readFileSync(file, "utf8");
    expect(text).not.toMatch(/\.roles\b/);
    expect(text).not.toMatch(/["'`](ADMIN|DIRECTOR|MANAGER|TEACHER|SYSTEM)["'`]/);
  });
});
