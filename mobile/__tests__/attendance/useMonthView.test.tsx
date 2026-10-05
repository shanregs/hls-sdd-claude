import { act, renderHook, waitFor } from "@testing-library/react-native";
import { ApiError, NoConnectionError } from "../../src/api/errors";
import { useMonthView } from "../../src/attendance/useMonthView";

describe("useMonthView", () => {
  it("loads a month and exposes it", async () => {
    const loader = jest.fn(async (month: string) => ({ month }));
    const { result } = await renderHook(() => useMonthView(loader, "2026-10"));

    await waitFor(() => expect(result.current.status).toBe("ready"));
    expect(result.current.view).toEqual({ month: "2026-10" });
  });

  it("drops the old data and loads again when the month changes", async () => {
    const loader = jest.fn(async (month: string) => ({ month }));
    const { result, rerender } = await renderHook((props: { month: string }) => useMonthView(loader, props.month), {
      initialProps: { month: "2026-10" },
    });
    await waitFor(() => expect(result.current.view).toEqual({ month: "2026-10" }));

    await rerender({ month: "2026-09" });
    await waitFor(() => expect(result.current.view).toEqual({ month: "2026-09" }));
    expect(loader).toHaveBeenCalledTimes(2);
  });

  it("never keeps a failed load's old data as current", async () => {
    let fail = false;
    const loader = jest.fn(async (month: string) => {
      if (fail) throw new NoConnectionError();
      return { month };
    });
    const { result } = await renderHook(() => useMonthView(loader, "2026-10"));
    await waitFor(() => expect(result.current.status).toBe("ready"));

    fail = true;
    await act(async () => result.current.reload({ quiet: true }));

    expect(result.current.status).toBe("noConnection");
    expect(result.current.view).toBeNull();
  });

  it("maps a 404 to notFound with the server's text, and other failures to error", async () => {
    const notFound = await renderHook(() =>
      useMonthView(async () => Promise.reject(new ApiError("Your profile has not been set up yet.", 404)), "2026-10"),
    );
    await waitFor(() => expect(notFound.result.current.status).toBe("notFound"));
    expect(notFound.result.current.message).toBe("Your profile has not been set up yet.");

    const broken = await renderHook(() =>
      useMonthView(async () => Promise.reject(new ApiError("Request failed (500).", 500)), "2026-10"),
    );
    await waitFor(() => expect(broken.result.current.status).toBe("error"));
  });

  it("reload fetches again", async () => {
    const loader = jest.fn(async (month: string) => ({ month }));
    const { result } = await renderHook(() => useMonthView(loader, "2026-10"));
    await waitFor(() => expect(result.current.status).toBe("ready"));

    await act(async () => result.current.reload());
    expect(loader).toHaveBeenCalledTimes(2);
  });
});
