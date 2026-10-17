import { openAttendanceScreen, type AppSetup, type OpenedApp } from "./attendanceApp";
import { managerFieldModel } from "./managerFixtures";
import { installManager, type ManagerFake } from "./managerServer";

export interface ManagerSetup extends Omit<AppSetup, "attendance"> {
  manager?: Partial<ManagerFake>;
}

export interface OpenedManagerApp extends OpenedApp {
  manager: ManagerFake;
}

/**
 * Signs in as a Manager with a stored session, installs the Manager attendance and leave routes, and opens a screen
 * from the drawer by its menu label (null stays on Home). The server's date is 2026-10-05 unless set.
 */
export async function openManagerScreen(menuLabel: string | null, setup: ManagerSetup = {}): Promise<OpenedManagerApp> {
  let manager: ManagerFake | undefined;
  const opened = await openAttendanceScreen(menuLabel, {
    ...setup,
    model: setup.model ?? managerFieldModel(),
    prepare: (server, attendance) => {
      manager = installManager(server, setup.manager);
      setup.prepare?.(server, attendance);
    },
  });
  return { ...opened, manager: manager as ManagerFake };
}
