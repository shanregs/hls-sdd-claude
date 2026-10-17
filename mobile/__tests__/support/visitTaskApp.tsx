import { openAttendanceScreen, type AppSetup, type OpenedApp } from "./attendanceApp";
import { installMarketing, type MarketingFake } from "./marketingServer";
import { installTasks, type TasksFake } from "./tasksServer";
import { taskDetail, visitTaskModel } from "./visitTaskFixtures";

export interface VisitTaskSetup extends Omit<AppSetup, "attendance"> {
  marketing?: Partial<MarketingFake>;
  tasks?: Partial<TasksFake>;
}

export interface OpenedVisitTaskApp extends OpenedApp {
  marketing: MarketingFake;
  tasks: TasksFake;
}

/**
 * Signs in as a Manager with a stored session, installs the marketing visit and task routes, and opens a screen from the
 * drawer by its menu label (null stays on Home). The server's date is 2026-10-05 unless set. A visit completed with a
 * follow-up date makes the fake task server add the follow-up task, the way the real server does.
 */
export async function openVisitTaskScreen(menuLabel: string | null, setup: VisitTaskSetup = {}): Promise<OpenedVisitTaskApp> {
  let marketing: MarketingFake | undefined;
  let tasks: TasksFake | undefined;
  const opened = await openAttendanceScreen(menuLabel, {
    ...setup,
    model: setup.model ?? visitTaskModel(),
    prepare: (server, attendance) => {
      tasks = installTasks(server, setup.tasks);
      marketing = installMarketing(server, {
        onFollowUp: (visit) => {
          tasks!.details = [
            ...tasks!.details,
            taskDetail({
              task: {
                id: `t-follow-${tasks!.details.length + 1}`,
                type: "FOLLOW_UP",
                typeLabel: "Follow-up",
                title: `Follow up: ${visit.prospect?.name ?? "visit"}`,
                dueOn: visit.followUpOn ?? "2026-10-12",
              },
            }),
          ];
        },
        ...setup.marketing,
      });
      setup.prepare?.(server, attendance);
    },
  });
  return { ...opened, marketing: marketing as MarketingFake, tasks: tasks as TasksFake };
}
