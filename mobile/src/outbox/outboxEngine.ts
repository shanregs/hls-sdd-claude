import { ApiError, NoConnectionError, RateLimitedError, UnauthorizedError, UpdateRequiredError } from "../api/errors";
import { getClaim } from "../api/expensesApi";
import { listItems, removeItem, updateItem } from "./outboxStore";
import { runNextStep } from "./syncSteps";
import type { OutboxItem } from "./types";

/** Seconds to wait after a failed try: 30 s, 60 s, then 5 minutes (spec 036 research §7). */
export const BACKOFF_SECONDS = [30, 60, 300] as const;

export interface EngineHooks {
  /** Called whenever an item or the engine's own state changed, so the screen can read the items again. */
  onChange?: () => void;
  /** The session ended (401 after renewal failed): the run stops and the items are left as they are. */
  onSessionEnded?: () => void;
  /** An item is being sent now (in memory only). */
  onSending?: (itemId: string | null) => void;
  now?: () => number;
}

export interface RunOptions {
  /** Try items that are still waiting out their backoff ("Send now"). */
  force?: boolean;
}

/**
 * The single-flight runner over one signed-in user's items (spec 036 research §7). It sends them one at a time in the
 * order they were captured and applies the outcome table: success deletes the item, a lost connection or a server error
 * leaves it waiting with a backoff, and a refusal keeps it with the server's reason. A refused item never blocks the next.
 */
export class OutboxEngine {
  private running: Promise<void> | null = null;
  private stopped = false;

  constructor(
    private readonly owner: string,
    private readonly hooks: EngineHooks = {},
  ) {}

  private now = (): number => (this.hooks.now ? this.hooks.now() : Date.now());

  /** Starts a run unless one is in progress, in which case the running one is returned. */
  run(options: RunOptions = {}): Promise<void> {
    if (this.running) return this.running;
    if (this.stopped) return Promise.resolve();
    const started = this.runAll(options).finally(() => {
      this.running = null;
    });
    this.running = started;
    return started;
  }

  get isRunning(): boolean {
    return this.running !== null;
  }

  private async runAll(options: RunOptions): Promise<void> {
    const { items } = await listItems(this.owner);
    for (const item of items) {
      if (item.state !== "WAITING") continue;
      if (!options.force && item.nextTryAt !== null && Date.parse(item.nextTryAt) > this.now()) continue;
      const proceed = await this.sendOne(item);
      if (!proceed) break;
    }
    this.hooks.onChange?.();
  }

  /** Sends one item to the end or to its failure. Returns false when the run should stop (no connection, session ended). */
  private async sendOne(start: OutboxItem): Promise<boolean> {
    let latest = start;
    const env = {
      persist: async (next: OutboxItem) => {
        await updateItem(next);
        latest = next;
      },
    };
    this.hooks.onSending?.(start.itemId);
    this.hooks.onChange?.();
    try {
      for (let guard = 0; guard < 10; guard += 1) {
        const result = await runNextStep(latest, env);
        latest = result.item;
        if (result.finished) {
          await removeItem(this.owner, latest.itemId);
          return true;
        }
      }
      return true;
    } catch (error) {
      return await this.classify(latest, error);
    } finally {
      this.hooks.onSending?.(null);
      this.hooks.onChange?.();
    }
  }

  private async keep(item: OutboxItem, change: Partial<OutboxItem>): Promise<void> {
    await updateItem({ ...item, ...change, updatedAt: new Date(this.now()).toISOString() });
  }

  private waitLater(item: OutboxItem, seconds?: number): Promise<void> {
    const wait = seconds ?? BACKOFF_SECONDS[Math.min(item.attempts, BACKOFF_SECONDS.length - 1)];
    return this.keep(item, { state: "WAITING", attempts: item.attempts + 1, nextTryAt: new Date(this.now() + wait * 1000).toISOString() });
  }

  private refuse(item: OutboxItem, status: number, reason: string, state: "REFUSED" | "NEEDS_CONFIRMATION", extra: Partial<OutboxItem> = {}) {
    return this.keep(item, { state, refusal: { status, reason, at: new Date(this.now()).toISOString() }, nextTryAt: null, ...extra });
  }

  /** The outcome table of research §7. */
  private async classify(item: OutboxItem, error: unknown): Promise<boolean> {
    if (error instanceof UnauthorizedError) {
      this.stopped = true;
      this.hooks.onSessionEnded?.();
      return false;
    }
    if (error instanceof UpdateRequiredError) return false;
    if (error instanceof RateLimitedError) {
      await this.waitLater(item, error.retryAfterSeconds ?? 60);
      return false;
    }
    if (error instanceof NoConnectionError) {
      await this.waitLater(item);
      return false;
    }
    if (error instanceof ApiError && error.status !== undefined && error.status >= 500) {
      await this.waitLater(item);
      return false;
    }
    if (error instanceof ApiError && error.status !== undefined) {
      if (error.status === 409 && error.matches.length > 0) {
        await this.refuse(item, 409, error.message, "NEEDS_CONFIRMATION");
      } else if (error.status === 409 && /changed by someone else/i.test(error.message)) {
        await this.refuse(item, 409, error.message, "REFUSED", { serverCurrent: await this.currentOnServer(item) });
      } else {
        await this.refuse(item, error.status, error.message, "REFUSED");
      }
      return true;
    }
    // Something unexpected (not a server answer): try again later rather than lose the item.
    await this.waitLater(item);
    return false;
  }

  /** The claim as the server holds it now, for the "Current claim on the server" comparison. */
  private async currentOnServer(item: OutboxItem): Promise<OutboxItem["serverCurrent"]> {
    if (!item.serverClaimId) return null;
    try {
      const claim = await getClaim(item.serverClaimId);
      return { expenseDate: claim.expenseDate, amount: claim.amount, categoryLabel: claim.categoryLabel, note: claim.note, version: claim.version };
    } catch {
      return null;
    }
  }
}
