import * as SecureStore from "expo-secure-store";
import {
  attachPhoto,
  cancelRegularization,
  checkIn,
  checkOut,
  completeCheckOut,
  getMonth,
  getToday,
  listRegularizations,
  openPhoto,
  requestRegularization,
} from "../../src/api/managerAttendanceApi";
import {
  cancelManagerLeave,
  listManagerLeave,
  listManagerLeaveTypes,
  requestManagerLeave,
} from "../../src/api/managerLeaveApi";
import { setAccessToken } from "../../src/security/memoryToken";
import { newServer } from "../support/fakeServer";
import { managerDay, plannedActivity, today } from "../support/managerFixtures";
import { installManager, type ManagerFake } from "../support/managerServer";

/**
 * Spec 033 T007 and T008: the request shapes of contracts/server-calls.md, against a fake that copies the real
 * answers of spec 032.
 */
const BASE = "/api/v1/manager-attendance/me";
const POSITION = { latitude: 12.971599, longitude: 77.594566, accuracyMeters: 18.5 };

let server: ReturnType<typeof newServer>;
let fake: ManagerFake;

beforeEach(async () => {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  setAccessToken("access-1");
  server = newServer();
  fake = installManager(server);
});

describe("today and the month", () => {
  it("reads today with its planned activities and the days still missing a check-out", async () => {
    fake.today = today({ plannedActivities: [plannedActivity()], missingCheckOutDates: ["2026-10-02"] });

    const view = await getToday();

    expect(server.callsTo(`GET ${BASE}/today`)).toHaveLength(1);
    expect(view.plannedActivities[0]).toMatchObject({ kind: "COLLEGE_DRIVE", place: "Anna Nagar College", status: "PLANNED" });
    expect(view.missingCheckOutDates).toEqual(["2026-10-02"]);
  });

  it("reads a month by its yyyy-MM", async () => {
    await getMonth("2026-09");

    expect(server.callsTo(`GET ${BASE}/months/{month}`)[0].path).toBe(`${BASE}/months/2026-09`);
  });
});

describe("check-in and check-out", () => {
  it("sends the day type, the purpose, the linked activities and the position exactly as read", async () => {
    const day = await checkIn({
      dayType: "FIELD",
      purpose: "Visit to St Mary's",
      activities: [{ kind: "COLLEGE_DRIVE", id: "act-1" }],
      ...POSITION,
    });

    expect(server.callsTo(`POST ${BASE}/check-in`)[0].body).toEqual({
      dayType: "FIELD",
      purpose: "Visit to St Mary's",
      activities: [{ kind: "COLLEGE_DRIVE", id: "act-1" }],
      latitude: 12.971599,
      longitude: 77.594566,
      accuracyMeters: 18.5,
    });
    expect(day.dayType).toBe("FIELD");
    expect(day.checkInAt).toBe(fake.now);
  });

  it("sends only the position to check out", async () => {
    fake.today = today({ day: managerDay() });

    await checkOut(POSITION);

    expect(server.callsTo(`POST ${BASE}/check-out`)[0].body).toEqual(POSITION);
  });

  it("completes a missing check-out with a time and a reason for that date", async () => {
    fake.today = today({ missingCheckOutDates: ["2026-10-02"] });

    await completeCheckOut("2026-10-02", { time: "18:05", reason: "Forgot to tap out" });

    expect(server.callsTo(`POST ${BASE}/days/{date}/complete-check-out`)[0]).toMatchObject({
      path: `${BASE}/days/2026-10-02/complete-check-out`,
      body: { time: "18:05", reason: "Forgot to tap out" },
    });
  });
});

describe("the photo", () => {
  it("uploads one file under the field name file to the date of the check-in", async () => {
    fake.today = today({ day: managerDay() });

    const day = await attachPhoto("2026-10-05", { uri: "file:///cache/a.jpg", name: "a.jpg", type: "image/jpeg" });

    expect(fake.photoUploads[0]).toMatchObject({ date: "2026-10-05", part: { fieldName: "file", name: "a.jpg", type: "image/jpeg" } });
    expect(day.hasPhoto).toBe(true);
  });

  it("opens the photo as a data address with the file name, type and size the server sent", async () => {
    fake.photo = { bytes: new Uint8Array([77, 97, 110]), contentType: "image/png", fileName: "gate.png" };

    const photo = await openPhoto("2026-10-05");

    expect(photo).toEqual({ dataUri: "data:image/png;base64,TWFu", contentType: "image/png", sizeBytes: 3, fileName: "gate.png" });
  });
});

describe("forgotten check-in requests", () => {
  it("lists, requests and cancels", async () => {
    await requestRegularization({ date: "2026-10-02", proposedTime: "09:15", dayType: "OFFICE", reason: "Phone was off" });
    const list = await listRegularizations();
    await cancelRegularization(list[0].id);

    expect(fake.regularizationRequests[0]).toEqual({
      date: "2026-10-02",
      proposedTime: "09:15",
      dayType: "OFFICE",
      reason: "Phone was off",
    });
    expect(list[0].status).toBe("PENDING");
    expect(fake.cancelledRegularizations).toEqual([list[0].id]);
    expect(server.callsTo(`DELETE ${BASE}/regularizations/{id}`)).toHaveLength(1);
  });
});

describe("Manager leave", () => {
  it("lists the types, requests leave and cancels a request", async () => {
    const types = await listManagerLeaveTypes();
    const created = await requestManagerLeave({
      leaveTypeId: types[0].id,
      firstDate: "2026-10-12",
      lastDate: "2026-10-14",
      firstHalf: false,
      lastHalf: true,
      reason: "Family function",
    });
    const list = await listManagerLeave();
    await cancelManagerLeave(created.id);

    expect(types.map((t) => t.name)).toEqual(["Casual Leave", "Loss of Pay"]);
    expect(types[1].lossOfPay).toBe(true);
    expect(fake.leaveRequests[0]).toEqual({
      leaveTypeId: "lt-1",
      firstDate: "2026-10-12",
      lastDate: "2026-10-14",
      firstHalf: false,
      lastHalf: true,
      reason: "Family function",
    });
    expect(list[0]).toMatchObject({ status: "PENDING", leaveTypeName: "Casual Leave" });
    expect(fake.cancelledLeaves).toEqual([created.id]);
  });
});
