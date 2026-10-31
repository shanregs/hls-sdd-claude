/**
 * The same-day items waiting on the phone (spec 040 FR-017): a task or a visit logged for today with no connection. Each
 * item carries the key the server uses to ignore a repeat and the day it was logged, which stays the date of the
 * activity when it is sent later. This file is the plain logic; where the items are kept is a `QueueStorage`.
 */

export type SameDayKind = "TASK" | "VISIT";

export interface QueuedItem {
  /** A UUID made when the form is saved and reused on every retry; sent as the Idempotency-Key. */
  key: string;
  kind: SameDayKind;
  /** The day it was logged, YYYY-MM-DD: the date of the activity. */
  capturedOn: string;
  /** What the form collected (task fields, or visit fields with the prospect). Sent unchanged. */
  body: Record<string, unknown>;
  /** A short name shown in the waiting list. */
  label: string;
  queuedAt: string;
  /** The server's reason when it refused the item; a refused item is kept for the person to see and discard. */
  refusal?: string;
}

export interface QueueStorage {
  read(): Promise<QueuedItem[]>;
  write(items: QueuedItem[]): Promise<void>;
}

/** What went wrong while sending: a connection or server problem is tried again later, anything else is a refusal. */
export type Outcome = { kind: "SENT" } | { kind: "RETRY" } | { kind: "REFUSED"; reason: string };

export type Sender = (item: QueuedItem) => Promise<Outcome>;

export interface FlushResult {
  sent: number;
  waiting: number;
  refused: number;
}

export async function enqueue(storage: QueueStorage, item: QueuedItem): Promise<void> {
  const items = await storage.read();
  if (items.some((i) => i.key === item.key)) return;
  await storage.write([...items, item]);
}

export async function discard(storage: QueueStorage, key: string): Promise<void> {
  const items = await storage.read();
  await storage.write(items.filter((i) => i.key !== key));
}

/**
 * Sends the waiting items oldest first. A sent item leaves the queue. A retry stops the run (later items wait behind it,
 * so they arrive in order). A refusal is kept, marked, and the run goes on with the next item.
 */
export async function flushQueue(storage: QueueStorage, send: Sender): Promise<FlushResult> {
  const items = await storage.read();
  const kept: QueuedItem[] = [];
  let sent = 0;
  let stopped = false;
  for (const item of items) {
    if (stopped || item.refusal) {
      kept.push(item);
      continue;
    }
    const outcome = await send(item);
    if (outcome.kind === "SENT") {
      sent += 1;
    } else if (outcome.kind === "RETRY") {
      kept.push(item);
      stopped = true;
    } else {
      kept.push({ ...item, refusal: outcome.reason });
    }
  }
  if (sent > 0 || kept.length !== items.length || kept.some((k, i) => k !== items[i])) {
    await storage.write(kept);
  }
  return {
    sent,
    waiting: kept.filter((k) => !k.refusal).length,
    refused: kept.filter((k) => k.refusal).length,
  };
}
