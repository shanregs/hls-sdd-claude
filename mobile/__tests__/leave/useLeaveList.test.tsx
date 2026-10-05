import { act, renderHook, waitFor } from "@testing-library/react-native";
import type { LeaveStatus } from "../../src/api/leaveApi";
import { NoConnectionError } from "../../src/api/errors";
import { useLeaveList, type LeavePage } from "../../src/leave/useLeaveList";
import { leaveRequest } from "../support/leaveFixtures";

const rows = (n: number, prefix = "r") => Array.from({ length: n }, (_, i) => leaveRequest({ id: `${prefix}${i}` }));

describe("useLeaveList", () => {
  it("loads the first page for a status and exposes the pending count", async () => {
    const loader = jest.fn(async (): Promise<LeavePage> => ({ content: rows(2), totalElements: 2, pendingCount: 7 }));
    const { result } = await renderHook(() => useLeaveList(loader, "PENDING"));

    await waitFor(() => expect(result.current.state).toBe("ready"));
    expect(result.current.items).toHaveLength(2);
    expect(result.current.pendingCount).toBe(7);
    expect(loader).toHaveBeenCalledWith("PENDING", 0);
  });

  it("shows loading, never the old rows, when the status changes", async () => {
    const loader = jest.fn(async (status: LeaveStatus | undefined): Promise<LeavePage> => ({
      content: status === "APPROVED" ? rows(1, "a") : rows(3, "p"),
      totalElements: status === "APPROVED" ? 1 : 3,
    }));
    const { result, rerender } = await renderHook((p: { status: LeaveStatus }) => useLeaveList(loader, p.status), {
      initialProps: { status: "PENDING" as LeaveStatus },
    });
    await waitFor(() => expect(result.current.items).toHaveLength(3));

    await rerender({ status: "APPROVED" });
    await waitFor(() => expect(result.current.items.map((r) => r.id)).toEqual(["a0"]));
  });

  it("appends the next page on loadMore", async () => {
    const loader = jest.fn(async (_s: LeaveStatus | undefined, page: number): Promise<LeavePage> => ({
      content: page === 0 ? rows(25, "p0-") : rows(5, "p1-"),
      totalElements: 30,
    }));
    const { result } = await renderHook(() => useLeaveList(loader, undefined));
    await waitFor(() => expect(result.current.items).toHaveLength(25));

    await act(async () => result.current.loadMore());
    await waitFor(() => expect(result.current.items).toHaveLength(30));
    expect(loader).toHaveBeenLastCalledWith(undefined, 1);
  });

  it("keeps the earlier pages and shows the error when an extra page fails", async () => {
    const loader = jest.fn(async (_s: LeaveStatus | undefined, page: number): Promise<LeavePage> => {
      if (page === 1) throw new NoConnectionError();
      return { content: rows(25), totalElements: 40 };
    });
    const { result } = await renderHook(() => useLeaveList(loader, undefined));
    await waitFor(() => expect(result.current.state).toBe("ready"));

    await act(async () => result.current.loadMore());
    await waitFor(() => expect(result.current.state).toBe("noConnection"));
    expect(result.current.items).toHaveLength(25);
  });

  it("drops the rows when a load fails", async () => {
    let fail = false;
    const loader = jest.fn(async (): Promise<LeavePage> => {
      if (fail) throw new Error("boom");
      return { content: rows(2), totalElements: 2 };
    });
    const { result } = await renderHook(() => useLeaveList(loader, undefined));
    await waitFor(() => expect(result.current.items).toHaveLength(2));

    fail = true;
    await act(async () => result.current.reload());
    await waitFor(() => expect(result.current.state).toBe("error"));
    expect(result.current.items).toHaveLength(0);
  });

  it("a quiet reload keeps the rows until the new ones arrive", async () => {
    let batch = rows(1, "old");
    const loader = jest.fn(async (): Promise<LeavePage> => ({ content: batch, totalElements: batch.length }));
    const { result } = await renderHook(() => useLeaveList(loader, undefined));
    await waitFor(() => expect(result.current.items[0]?.id).toBe("old0"));

    batch = rows(1, "new");
    await act(async () => result.current.reload({ quiet: true }));
    await waitFor(() => expect(result.current.items[0]?.id).toBe("new0"));
  });
});
