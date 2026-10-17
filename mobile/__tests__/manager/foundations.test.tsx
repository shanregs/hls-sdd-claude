import { act, renderHook, waitFor } from "@testing-library/react-native";
import { ApiError, NoConnectionError } from "../../src/api/errors";
import { setAccessToken } from "../../src/security/memoryToken";
import {
  fieldDayProblem,
  flagText,
  kindText,
  locationMessage,
  managerRefusalText,
  NO_CONNECTION_TEXT,
  statusText,
} from "../../src/manager/managerMessages";
import { useAction } from "../../src/manager/useAction";
import { useToday } from "../../src/manager/useToday";
import { newServer } from "../support/fakeServer";
import { managerDay, today } from "../support/managerFixtures";
import { installManager } from "../support/managerServer";

/** Spec 033 T009 to T011: the messages, the one-request-at-a-time button and the today hook. */

describe("managerMessages", () => {
  it("explains each way a position can be missing, with Open settings only where settings can fix it", () => {
    expect(locationMessage("PERMISSION_DENIED")).toEqual({
      text: "Check-in needs your location. Allow location for HLS in your phone settings, then try again.",
      canOpenSettings: true,
    });
    expect(locationMessage("SERVICES_OFF")).toEqual({
      text: "Turn on location (GPS) on your phone, then try again.",
      canOpenSettings: false,
    });
    for (const reason of ["NO_FIX", "OTHER"] as const) {
      expect(locationMessage(reason)).toEqual({
        text: "Your phone could not find its position. Move to an open area and try again.",
        canOpenSettings: false,
      });
    }
  });

  it("shows the server's reason as it is, never a code or a generic line", () => {
    expect(managerRefusalText(new ApiError("You have already checked in today.", 409))).toBe(
      "You have already checked in today.",
    );
    expect(managerRefusalText(new ApiError("Give a reason.", 400))).toBe("Give a reason.");
    expect(managerRefusalText(new NoConnectionError())).toBe(NO_CONNECTION_TEXT);
    expect(managerRefusalText(new Error("boom"))).toBe("Something went wrong. Nothing was saved. Please try again.");
  });

  it("words every flag and every month status", () => {
    expect(["LATE", "NO_CHECK_OUT", "ADDED_LATER", "CORRECTED", "UNLINKED"].map((f) => flagText(f as never))).toEqual([
      "Late",
      "No check-out",
      "Added later",
      "Corrected by your Director",
      "No planned activity linked",
    ]);
    expect(
      ["WORKED", "FIELD", "LEAVE", "HOLIDAY", "WEEKLY_OFF", "NOT_EMPLOYED", "PENDING", "MISSED", "ABSENT", "NO_CHECK_OUT"].map((s) =>
        statusText(s as never),
      ),
    ).toEqual([
      "Worked",
      "Field day",
      "Leave",
      "Holiday",
      "Weekly off",
      "Not employed",
      "Not yet",
      "No check-in",
      "Absent",
      "No check-out",
    ]);
    expect(kindText("COLLEGE_DRIVE")).toBe("College drive");
  });

  it("asks a Field day for an activity or a purpose, and nothing else", () => {
    expect(fieldDayProblem(0, "")).toMatch(/needs a planned activity or a purpose/);
    expect(fieldDayProblem(0, "   ")).toMatch(/needs a planned activity or a purpose/);
    expect(fieldDayProblem(1, "")).toBeNull();
    expect(fieldDayProblem(0, "Visit to St Mary's")).toBeNull();
  });
});

describe("useAction", () => {
  it("sends one request for a double tap and returns the result", async () => {
    let release: (value: string) => void = () => undefined;
    const action = jest.fn(() => new Promise<string>((resolve) => (release = resolve)));
    const { result } = await renderHook(() => useAction(action));

    let first: Promise<string | undefined> = Promise.resolve(undefined);
    let second: Promise<string | undefined> = Promise.resolve(undefined);
    await act(async () => {
      first = result.current.run();
      second = result.current.run();
    });
    expect(result.current.sending).toBe(true);
    expect(action).toHaveBeenCalledTimes(1);

    await act(async () => release("done"));

    expect(await first).toBe("done");
    expect(await second).toBe("done");
    expect(result.current.sending).toBe(false);
    expect(result.current.error).toBeNull();
  });

  it("keeps the server's reason until the next run, and returns undefined for a failure", async () => {
    const action = jest
      .fn<Promise<string>, []>()
      .mockRejectedValueOnce(new ApiError("Give a reason.", 400))
      .mockResolvedValueOnce("ok");
    const { result } = await renderHook(() => useAction(action));

    let outcome: string | undefined = "unset";
    await act(async () => {
      outcome = await result.current.run();
    });
    expect(outcome).toBeUndefined();
    expect(result.current.error).toBe("Give a reason.");

    await act(async () => {
      outcome = await result.current.run();
    });
    expect(outcome).toBe("ok");
    expect(result.current.error).toBeNull();
  });

  it("says there is no connection when the server cannot be reached", async () => {
    const { result } = await renderHook(() => useAction(async () => Promise.reject(new NoConnectionError())));

    await act(async () => {
      await result.current.run();
    });

    expect(result.current.error).toBe("No connection. Nothing was sent.");
  });
});

describe("useToday", () => {
  beforeEach(() => setAccessToken("access-1"));

  it("loads today, and reloads after the day changes", async () => {
    const server = newServer();
    const fake = installManager(server);
    const { result } = await renderHook(() => useToday());

    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.today?.day).toBeNull();

    fake.today = today({ day: managerDay() });
    await act(async () => result.current.reload({ quiet: true }));

    await waitFor(() => expect(result.current.today?.day?.checkInAt).toBe("2026-10-05T04:12:00Z"));
    expect(result.current.status).toBe("ready");
  });

  it("drops the data and says so when a load fails, never keeping the old day as current", async () => {
    const server = newServer();
    installManager(server);
    const { result } = await renderHook(() => useToday());
    await waitFor(() => expect(result.current.status).toBe("ready"));

    server.on("GET /api/v1/manager-attendance/me/today", { status: 500, body: { reason: "Could not load today." } });
    await act(async () => result.current.reload());

    await waitFor(() => expect(result.current.status).toBe("error"));
    expect(result.current.today).toBeNull();
    expect(result.current.message).toBe("Could not load today.");
  });

  it("reports no connection separately", async () => {
    const server = newServer();
    installManager(server);
    server.offline = true;
    const { result } = await renderHook(() => useToday());

    await waitFor(() => expect(result.current.status).toBe("noConnection"));
    expect(result.current.today).toBeNull();
  });

  it("loads nothing while it is not enabled", async () => {
    const server = newServer();
    installManager(server);
    const { result } = await renderHook(() => useToday(false));

    expect(result.current.status).toBe("loading");
    expect(server.calls.filter((c) => c.path.endsWith("/today"))).toHaveLength(0);
  });
});
