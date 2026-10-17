import { useEffect, useState } from "react";
import { listAssignees } from "../api/tasksApi";

/**
 * Whether "Add a task for me" is offered (spec 034 research §7): the menu grants Create and the server lists the caller
 * themselves as an assignee. A Manager's list is only themselves; for anyone else it is other people or nothing, so the control
 * stays hidden and no one can assign a task to someone else from the phone. No role is read, and without Create no request is made.
 */
export function useCanAddTask(canCreate: boolean, myUserId: string | null): boolean {
  const [offered, setOffered] = useState(false);

  useEffect(() => {
    if (!canCreate || !myUserId) return;
    let cancelled = false;
    listAssignees()
      .then((people) => {
        if (!cancelled) setOffered(people.some((p) => p.userId === myUserId));
      })
      .catch(() => {
        if (!cancelled) setOffered(false);
      });
    return () => {
      cancelled = true;
    };
  }, [canCreate, myUserId]);

  return canCreate && myUserId !== null && offered;
}
