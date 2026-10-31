import { api } from "./httpClient";

/**
 * Same-day logging (spec 040): a task or a visit the Manager logs for today. The server decides the assignee (the caller)
 * and the date (today); only an offline replay may send `capturedOn`, together with the `Idempotency-Key` that makes a
 * repeat harmless.
 */
export interface SameDayTaskBody {
  type: string;
  title: string;
  description?: string;
  place?: string;
  status: "IN_PROGRESS" | "COMPLETED";
  completionNote?: string;
}

export interface SameDayVisitBody {
  prospectId: string;
  type: string;
  outcome: string;
  notes?: string;
  followUpOn?: string;
}

export interface ReplayOptions {
  key?: string;
  capturedOn?: string;
}

const withReplay = (body: object, options?: ReplayOptions) =>
  options?.key && options.capturedOn ? { ...body, capturedOn: options.capturedOn } : body;

const headersOf = (options?: ReplayOptions) => (options?.key ? { headers: { "Idempotency-Key": options.key } } : undefined);

export const logSameDayTask = (body: SameDayTaskBody, options?: ReplayOptions) =>
  api.post<{ id: string; status: string; sameDay: boolean }>(
    "/api/v1/tasks/same-day",
    withReplay(body, options),
    headersOf(options),
  );

export const logSameDayVisit = (body: SameDayVisitBody, options?: ReplayOptions) =>
  api.post<{ id: string; status: string; sameDay: boolean }>(
    "/api/v1/marketing/activities/same-day",
    withReplay(body, options),
    headersOf(options),
  );
