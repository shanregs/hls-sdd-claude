import { ApiError, NoConnectionError } from "../../src/api/errors";
import { logSameDayTask, logSameDayVisit } from "../../src/api/sameDayApi";
import { loggedDay, sendQueued } from "../../src/sameDay/useSameDayLog";
import type { QueuedItem } from "../../src/sameDay/sameDayQueue";

jest.mock("../../src/api/sameDayApi");

const task = (): QueuedItem => ({
  key: "11111111-1111-1111-1111-111111111111",
  kind: "TASK",
  capturedOn: "2026-10-26",
  body: { type: "SCHOOL_VISIT", title: "Visit", status: "IN_PROGRESS" },
  label: "Visit",
  queuedAt: "2026-10-26T10:00:00Z",
});

describe("sending a queued same-day item", () => {
  beforeEach(() => jest.resetAllMocks());

  it("sends a task with its key and the day it was logged", async () => {
    (logSameDayTask as jest.Mock).mockResolvedValue({ id: "t1" });

    const outcome = await sendQueued(task());

    expect(outcome).toEqual({ kind: "SENT" });
    expect(logSameDayTask).toHaveBeenCalledWith(expect.objectContaining({ title: "Visit" }), {
      key: "11111111-1111-1111-1111-111111111111",
      capturedOn: "2026-10-26",
    });
  });

  it("sends a visit to the visit call", async () => {
    (logSameDayVisit as jest.Mock).mockResolvedValue({ id: "v1" });

    const outcome = await sendQueued({ ...task(), kind: "VISIT" });

    expect(outcome).toEqual({ kind: "SENT" });
    expect(logSameDayVisit).toHaveBeenCalled();
  });

  it("tries again later on no connection and on a server fault", async () => {
    (logSameDayTask as jest.Mock).mockRejectedValueOnce(new NoConnectionError());
    expect(await sendQueued(task())).toEqual({ kind: "RETRY" });

    (logSameDayTask as jest.Mock).mockRejectedValueOnce(new ApiError("Server error", 503));
    expect(await sendQueued(task())).toEqual({ kind: "RETRY" });
  });

  it("keeps the server's reason when it refuses", async () => {
    (logSameDayTask as jest.Mock).mockRejectedValue(new ApiError("DATE_NOT_ALLOWED: too old", 409));

    expect(await sendQueued(task())).toEqual({ kind: "REFUSED", reason: "DATE_NOT_ALLOWED: too old" });
  });
});

describe("the day an item is logged", () => {
  it("is the phone's calendar day as YYYY-MM-DD", () => {
    expect(loggedDay(new Date(2026, 9, 28, 23, 59))).toBe("2026-10-28");
    expect(loggedDay(new Date(2026, 0, 5, 0, 1))).toBe("2026-01-05");
  });
});
