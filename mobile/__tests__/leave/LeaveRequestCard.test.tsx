import { fireEvent, render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { LeaveRequestCard } from "../../src/leave/LeaveRequestCard";
import { StatusFilter, HISTORY_FILTERS, MANAGEMENT_FILTERS } from "../../src/leave/StatusFilter";
import { leaveRequest } from "../support/leaveFixtures";

const wrap = (node: React.ReactElement) => render(<PaperProvider>{node}</PaperProvider>);

describe("LeaveRequestCard", () => {
  it("shows type, dates as DD/MM/YYYY, working days, half-day marks and status", async () => {
    await wrap(
      <LeaveRequestCard request={leaveRequest({ halfDayEnd: true, workingDays: 2.5, leaveType: "Sick" })} />,
    );

    expect(screen.getByText("Sick")).toBeTruthy();
    expect(screen.getByText("12/10/2026 – 14/10/2026")).toBeTruthy();
    expect(screen.getByText("2.5 working days · half day at the end")).toBeTruthy();
    expect(screen.getByText("Pending")).toBeTruthy();
  });

  it("shows who decided and when, and the rejection reason after a tap", async () => {
    await wrap(
      <LeaveRequestCard
        expandable
        request={leaveRequest({
          status: "REJECTED",
          decidedByName: "Manoj",
          decidedAt: "2026-10-05T05:00:00Z",
          decisionNote: "Exam week",
        })}
      />,
    );

    expect(screen.getByText(/^Rejected by Manoj on 05\/10\/2026/)).toBeTruthy();
    expect(screen.queryByText("Rejection reason: Exam week")).toBeNull();
    await fireEvent.press(screen.getByRole("button"));
    expect(screen.getByText("Reason: Family function")).toBeTruthy();
    expect(screen.getByText("Rejection reason: Exam week")).toBeTruthy();
  });

  it("shows the Teacher and School on Leave Management and opens on a tap", async () => {
    const onPress = jest.fn();
    await wrap(<LeaveRequestCard showTeacher onPress={onPress} request={leaveRequest({ teacherName: "Asha Rao" })} />);

    await fireEvent.press(screen.getByLabelText(/^Asha Rao, Demo School One, Casual/));
    expect(onPress).toHaveBeenCalled();
    expect(screen.getByText("Demo School One · Casual")).toBeTruthy();
  });

  it("says who cancelled in its spoken label", async () => {
    await wrap(<LeaveRequestCard request={leaveRequest({ status: "CANCELLED", cancelledBy: "TEACHER" })} />);

    expect(screen.getByLabelText(/Cancelled by the teacher/)).toBeTruthy();
  });

  it("is not a button when it has no action", async () => {
    await wrap(<LeaveRequestCard request={leaveRequest()} />);

    expect(screen.queryByRole("button")).toBeNull();
  });
});

describe("StatusFilter", () => {
  it("offers All for the history and no All for Leave Management", () => {
    expect(HISTORY_FILTERS.map((f) => f.label)).toEqual(["All", "Pending", "Approved", "Rejected", "Cancelled"]);
    expect(MANAGEMENT_FILTERS.map((f) => f.label)).toEqual(["Pending", "Approved", "Rejected", "Cancelled"]);
  });

  it("marks the chosen option and reports a change", async () => {
    const onChange = jest.fn();
    await wrap(<StatusFilter options={HISTORY_FILTERS} value="PENDING" onChange={onChange} />);

    expect(screen.getByRole("button", { name: "Pending", selected: true })).toBeTruthy();
    await fireEvent.press(screen.getByLabelText("Approved"));
    expect(onChange).toHaveBeenCalledWith("APPROVED");
  });
});
