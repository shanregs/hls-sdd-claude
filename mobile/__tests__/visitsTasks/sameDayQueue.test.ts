import {
  discard,
  enqueue,
  flushQueue,
  type QueuedItem,
  type QueueStorage,
} from "../../src/sameDay/sameDayQueue";

/** Spec 040 FR-017: same-day items logged with no connection wait on the phone and are sent once, in order. */
function memory(initial: QueuedItem[] = []): QueueStorage & { items: QueuedItem[] } {
  const store = { items: initial };
  return {
    get items() {
      return store.items;
    },
    read: async () => store.items,
    write: async (next) => {
      store.items = next;
    },
  };
}

const item = (key: string, extra: Partial<QueuedItem> = {}): QueuedItem => ({
  key,
  kind: "TASK",
  capturedOn: "2026-10-27",
  body: { title: key },
  label: key,
  queuedAt: "2026-10-27T10:00:00Z",
  ...extra,
});

describe("the same-day queue", () => {
  it("keeps an item once, even when it is queued twice with the same key", async () => {
    const storage = memory();
    await enqueue(storage, item("a"));
    await enqueue(storage, item("a"));

    expect(storage.items).toHaveLength(1);
  });

  it("sends the items oldest first and empties the queue", async () => {
    const storage = memory([item("a"), item("b")]);
    const sent: string[] = [];

    const result = await flushQueue(storage, async (i) => {
      sent.push(i.key);
      return { kind: "SENT" };
    });

    expect(sent).toEqual(["a", "b"]);
    expect(result).toEqual({ sent: 2, waiting: 0, refused: 0 });
    expect(storage.items).toHaveLength(0);
  });

  it("stops at the first retry so later items arrive after it", async () => {
    const storage = memory([item("a"), item("b"), item("c")]);
    const sent: string[] = [];

    const result = await flushQueue(storage, async (i) => {
      sent.push(i.key);
      return i.key === "b" ? { kind: "RETRY" } : { kind: "SENT" };
    });

    expect(sent).toEqual(["a", "b"]);
    expect(result).toEqual({ sent: 1, waiting: 2, refused: 0 });
    expect(storage.items.map((i) => i.key)).toEqual(["b", "c"]);
  });

  it("keeps a refused item with the server's reason and goes on with the next one", async () => {
    const storage = memory([item("a"), item("b")]);

    const result = await flushQueue(storage, async (i) =>
      i.key === "a" ? { kind: "REFUSED", reason: "DATE_NOT_ALLOWED" } : { kind: "SENT" },
    );

    expect(result).toEqual({ sent: 1, waiting: 0, refused: 1 });
    expect(storage.items).toHaveLength(1);
    expect(storage.items[0]).toMatchObject({ key: "a", refusal: "DATE_NOT_ALLOWED" });
  });

  it("does not send a refused item again, and discard removes it", async () => {
    const storage = memory([item("a", { refusal: "DATE_NOT_ALLOWED" })]);
    const send = jest.fn();

    await flushQueue(storage, send);
    expect(send).not.toHaveBeenCalled();

    await discard(storage, "a");
    expect(storage.items).toHaveLength(0);
  });

  it("keeps the day the item was logged as its date", async () => {
    const storage = memory([item("a", { capturedOn: "2026-10-26" })]);
    const seen: string[] = [];

    await flushQueue(storage, async (i) => {
      seen.push(i.capturedOn);
      return { kind: "SENT" };
    });

    expect(seen).toEqual(["2026-10-26"]);
  });
});
