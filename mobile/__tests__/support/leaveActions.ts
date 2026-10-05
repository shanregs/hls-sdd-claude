import { act, fireEvent, screen } from "@testing-library/react-native";
import { BackHandler } from "react-native";

/** Chooses a date in the in-app date chooser by the spoken label of the field and of the day. */
export async function chooseDate(field: "First date" | "Last date", currentLabel: string, dayLabel: string) {
  await fireEvent.press(await screen.findByLabelText(`${field}, ${currentLabel}`));
  await fireEvent.press(await screen.findByLabelText(dayLabel));
}

export interface DraftInput {
  type?: string;
  first?: string;
  last?: string;
  reason?: string;
  halfStart?: boolean;
  halfEnd?: boolean;
}

/** Fills Apply Leave: a type, the two dates (as spoken labels such as "Monday 12 October 2026"), half days and a reason. */
export async function fillDraft({ type = "Casual", first, last, reason, halfStart, halfEnd }: DraftInput) {
  await fireEvent.press(await screen.findByLabelText(type));
  if (first) await chooseDate("First date", "not chosen", first);
  if (last) {
    // Choosing the first date may already have set the last date to it.
    const current = screen.queryByLabelText("Last date, not chosen") ? "not chosen" : (first as string);
    await chooseDate("Last date", current, last);
  }
  if (halfStart) await fireEvent.press(screen.getByLabelText("Half day on the first day"));
  if (halfEnd) await fireEvent.press(screen.getByLabelText("Half day on the last day"));
  if (reason !== undefined) await fireEvent.changeText(screen.getByLabelText("Reason"), reason);
}

/** Pulls the open screen down, as a finger would, asking it to load again. */
export async function pullToRefresh() {
  const scroll = screen.getByTestId("screen-scroll");
  await act(async () => {
    scroll.props.refreshControl.props.onRefresh();
  });
}

/**
 * Records the Android back handlers the app registers, so a test can press the hardware Back button as the
 * system does: the most recently added handler first, stopping at the first that handles it. Call before the
 * app renders.
 */
export function captureHardwareBack(): () => Promise<boolean> {
  const handlers: (() => boolean | null | undefined)[] = [];
  jest.spyOn(BackHandler, "addEventListener").mockImplementation(((_event: string, handler: () => boolean) => {
    handlers.push(handler);
    return { remove: () => handlers.splice(handlers.indexOf(handler), 1) };
  }) as never);
  return async () => {
    let handled = false;
    await act(async () => {
      for (const handler of [...handlers].reverse()) {
        if (handler()) {
          handled = true;
          break;
        }
      }
    });
    return handled;
  };
}
