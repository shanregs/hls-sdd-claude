import type {
  CheckInBody,
  ManagerDay,
  ManagerMonth,
  Position,
  Regularization,
  RegularizationBody,
  Today,
} from "../../src/api/managerAttendanceApi";
import type { ManagerLeave, ManagerLeaveBody, ManagerLeaveType } from "../../src/api/managerLeaveApi";
import type { FakeReply, FakeRequest, FakeServer, MultipartBody } from "./fakeServer";
import { managerDay, managerLeaveTypes, managerMonth, today as todayView } from "./managerFixtures";

const BASE = "/api/v1/manager-attendance/me";

/** The state and the knobs of the fake Manager endpoints (spec 032), which the tests inspect and set. */
export interface ManagerFake {
  today: Today;
  /** Months by "yyyy-MM"; a month not listed is an empty month. */
  months: Record<string, ManagerMonth>;
  types: ManagerLeaveType[];
  leaves: ManagerLeave[];
  regularizations: Regularization[];
  /** The server's clock when it records a time. */
  now: string;
  /** The photo the server holds for the day, with the headers it sends. */
  photo: { bytes: Uint8Array; contentType: string; fileName: string } | null;
  /** Returns a refusal to make the call fail, or undefined to accept it. */
  checkInRefusal?: (body: CheckInBody) => FakeReply | undefined;
  checkOutRefusal?: (body: Position) => FakeReply | undefined;
  completeRefusal?: (date: string, body: { time: string; reason: string }) => FakeReply | undefined;
  photoRefusal?: () => FakeReply | undefined;
  leaveRefusal?: (body: ManagerLeaveBody) => FakeReply | undefined;
  regularizationRefusal?: (body: RegularizationBody) => FakeReply | undefined;
  cancelRefusal?: () => FakeReply | undefined;
  /** What the tests look at. */
  checkIns: CheckInBody[];
  checkOuts: Position[];
  completions: { date: string; time: string; reason: string }[];
  photoUploads: { date: string; part: { fieldName: string; name?: string; type?: string } | undefined }[];
  leaveRequests: ManagerLeaveBody[];
  regularizationRequests: RegularizationBody[];
  cancelledLeaves: string[];
  cancelledRegularizations: string[];
}

const refuse = (status: number, reason: string): FakeReply => ({ status, body: { reason } });

const JPEG = new Uint8Array([0xff, 0xd8, 0xff, 0xe0, 0, 16, 0x4a, 0x46, 0x49, 0x46, 0, 1]);

export function installManager(server: FakeServer, overrides: Partial<ManagerFake> = {}): ManagerFake {
  const fake: ManagerFake = {
    today: todayView(),
    months: {},
    types: managerLeaveTypes(),
    leaves: [],
    regularizations: [],
    now: "2026-10-05T04:12:00Z",
    photo: null,
    checkIns: [],
    checkOuts: [],
    completions: [],
    photoUploads: [],
    leaveRequests: [],
    regularizationRequests: [],
    cancelledLeaves: [],
    cancelledRegularizations: [],
    ...overrides,
  };

  server.on(`GET ${BASE}/today`, () => ({ status: 200, body: fake.today }));

  server.on(`POST ${BASE}/check-in`, (request) => {
    const body = request.body as CheckInBody;
    fake.checkIns.push(body);
    if (body.latitude === undefined || body.longitude === undefined) {
      return refuse(400, "Check-in needs a location.");
    }
    const refusal = fake.checkInRefusal?.(body);
    if (refusal) return refusal;
    if (fake.today.day?.checkInAt) return refuse(409, "You have already checked in today.");
    const day = managerDay({
      date: fake.today.date,
      dayType: body.dayType,
      purpose: body.purpose ?? null,
      checkInAt: fake.now,
      activities: body.activities,
    });
    fake.today = { ...fake.today, day };
    return { status: 201, body: day };
  });

  server.on(`POST ${BASE}/check-out`, (request) => {
    const body = request.body as Position;
    fake.checkOuts.push(body);
    if (body.latitude === undefined || body.longitude === undefined) {
      return refuse(400, "Check-out needs a location.");
    }
    const refusal = fake.checkOutRefusal?.(body);
    if (refusal) return refusal;
    const day = fake.today.day;
    if (!day?.checkInAt) return refuse(400, "You have not checked in today.");
    if (day.checkOutAt) return refuse(409, "You have already checked out today.");
    const out: ManagerDay = { ...day, checkOutAt: fake.now };
    fake.today = { ...fake.today, day: out };
    return { status: 200, body: out };
  });

  server.on(`POST ${BASE}/days/{date}/complete-check-out`, (request) => {
    const body = request.body as { time: string; reason: string };
    const date = request.params.date;
    fake.completions.push({ date, ...body });
    const refusal = fake.completeRefusal?.(date, body);
    if (refusal) return refusal;
    if (!body.reason?.trim()) return refuse(400, "Give a reason.");
    fake.today = {
      ...fake.today,
      missingCheckOutDates: fake.today.missingCheckOutDates.filter((d) => d !== date),
    };
    return { status: 200, body: managerDay({ date, checkOutAt: `${date}T${body.time}:00+05:30`, flags: [] }) };
  });

  server.on(`POST ${BASE}/days/{date}/photo`, (request) => {
    const multipart = (request.body as MultipartBody | undefined)?.multipart ?? [];
    fake.photoUploads.push({ date: request.params.date, part: multipart[0] });
    const refusal = fake.photoRefusal?.();
    if (refusal) return refusal;
    const day = fake.today.day;
    if (!day?.checkInAt) return refuse(404, `There is no check-in on ${request.params.date} to attach a photo to.`);
    if (day.hasPhoto) return refuse(409, "This check-in already has a photo.");
    const type = multipart[0]?.type ?? "";
    if (!["image/jpeg", "image/png"].includes(type)) return refuse(400, "A photo must be a jpg or png file.");
    const withPhoto: ManagerDay = { ...day, hasPhoto: true };
    fake.today = { ...fake.today, day: withPhoto };
    fake.photo = { bytes: JPEG, contentType: type, fileName: multipart[0]?.name ?? "photo.jpg" };
    return { status: 201, body: withPhoto };
  });

  server.on(`GET ${BASE}/days/{date}/photo`, () => {
    if (!fake.photo) return refuse(404, "There is no photo for this day.");
    return {
      status: 200,
      rawBody: fake.photo.bytes,
      headers: {
        "Content-Type": fake.photo.contentType,
        "Content-Length": String(fake.photo.bytes.length),
        "Content-Disposition": `attachment; filename="${fake.photo.fileName}"`,
      },
    };
  });

  server.on(`GET ${BASE}/months/{month}`, (request: FakeRequest) => ({
    status: 200,
    body: fake.months[request.params.month] ?? managerMonth({ month: request.params.month }),
  }));

  server.on(`GET ${BASE}/regularizations`, () => ({ status: 200, body: fake.regularizations }));
  server.on(`POST ${BASE}/regularizations`, (request) => {
    const body = request.body as RegularizationBody;
    fake.regularizationRequests.push(body);
    const refusal = fake.regularizationRefusal?.(body);
    if (refusal) return refusal;
    if (!body.reason?.trim()) return refuse(400, "Give a reason.");
    const created: Regularization = {
      id: `rg-${fake.regularizations.length + 1}`,
      date: body.date,
      proposedTime: `${body.proposedTime}:00`,
      dayType: body.dayType,
      purpose: body.purpose ?? null,
      reason: body.reason,
      status: "PENDING",
      requestedAt: fake.now,
      decidedAt: null,
      decisionReason: null,
    };
    fake.regularizations = [created, ...fake.regularizations];
    return { status: 201, body: created };
  });
  server.on(`DELETE ${BASE}/regularizations/{id}`, (request) => {
    const refusal = fake.cancelRefusal?.();
    if (refusal) return refusal;
    fake.cancelledRegularizations.push(request.params.id);
    fake.regularizations = fake.regularizations.map((r) =>
      r.id === request.params.id ? { ...r, status: "CANCELLED" } : r,
    );
    return { status: 204 };
  });

  server.on(`GET ${BASE}/leave/types`, () => ({ status: 200, body: fake.types }));
  server.on(`GET ${BASE}/leave`, () => ({ status: 200, body: fake.leaves }));
  server.on(`POST ${BASE}/leave`, (request) => {
    const body = request.body as ManagerLeaveBody;
    fake.leaveRequests.push(body);
    const refusal = fake.leaveRefusal?.(body);
    if (refusal) return refusal;
    if (!body.reason?.trim()) return refuse(400, "Give a reason.");
    const type = fake.types.find((t) => t.id === body.leaveTypeId);
    const created: ManagerLeave = {
      id: `ml-${fake.leaves.length + 1}`,
      leaveTypeId: body.leaveTypeId,
      leaveTypeName: type?.name ?? "Leave",
      lossOfPay: type?.lossOfPay ?? false,
      firstDate: body.firstDate,
      lastDate: body.lastDate,
      firstHalf: body.firstHalf,
      lastHalf: body.lastHalf,
      reason: body.reason,
      status: "PENDING",
      requestedAt: fake.now,
      decidedAt: null,
      decisionReason: null,
    };
    fake.leaves = [created, ...fake.leaves];
    return { status: 201, body: created };
  });
  server.on(`DELETE ${BASE}/leave/{id}`, (request) => {
    const refusal = fake.cancelRefusal?.();
    if (refusal) return refusal;
    fake.cancelledLeaves.push(request.params.id);
    fake.leaves = fake.leaves.map((l) => (l.id === request.params.id ? { ...l, status: "CANCELLED" } : l));
    return { status: 204 };
  });

  return fake;
}
