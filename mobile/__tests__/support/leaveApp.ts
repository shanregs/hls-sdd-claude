import { openAttendanceScreen, type AppSetup, type OpenedApp } from "./attendanceApp";
import { installLeave, type LeaveFake } from "./leaveServer";

export interface LeaveSetup extends AppSetup {
  leave?: Partial<LeaveFake>;
}

export interface OpenedLeaveApp extends OpenedApp {
  leave: LeaveFake;
}

/** Signs in with a stored session, installs the attendance and leave routes, and opens a screen from the drawer. */
export async function openLeaveScreen(menuLabel: string, setup: LeaveSetup = {}): Promise<OpenedLeaveApp> {
  let leave: LeaveFake | undefined;
  const opened = await openAttendanceScreen(menuLabel, {
    ...setup,
    prepare: (server, attendance) => {
      leave = installLeave(server, setup.leave);
      setup.prepare?.(server, attendance);
    },
  });
  return { ...opened, leave: leave as LeaveFake };
}
