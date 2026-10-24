import { render, screen } from "@testing-library/react-native";
import * as SecureStore from "expo-secure-store";
import { Text } from "react-native";
import { ExpenseOutboxProvider, useExpenseOutbox } from "../../src/outbox/ExpenseOutboxProvider";
import { saveItem } from "../../src/outbox/outboxStore";
import { setAccessToken } from "../../src/security/memoryToken";
import { installExpenses, type ExpensesFake } from "../support/expensesServer";
import { newServer, type FakeServer } from "../support/fakeServer";
import { makeItem } from "../support/outboxFixtures";

const fsState = () => jest.requireMock("expo-file-system/legacy").__state;
let server: FakeServer;
let fake: ExpensesFake;

beforeEach(async () => {
  await SecureStore.setItemAsync("hls.renewalCredential", "stored");
  setAccessToken("access-1");
  server = newServer();
  fake = installExpenses(server);
  fsState().files.set("file:///cache/photo.jpg", "SEVMTE9fQklMTF9CWVRFUw==");
});

function Probe() {
  const outbox = useExpenseOutbox();
  return (
    <Text>
      {`user=${outbox.userId} loaded=${outbox.loaded} items=${outbox.items.map((i) => i.itemId).join(",")} unsent=${outbox.unsentCount}`}
    </Text>
  );
}

async function openAs(userId: string) {
  return render(
    <ExpenseOutboxProvider userId={userId}>
      <Probe />
    </ExpenseOutboxProvider>,
  );
}

describe("ExpenseOutboxProvider", () => {
  it("loads the signed-in user's items only", async () => {
    server.offline = true;
    await saveItem(makeItem({ itemId: "mine", ownerUserId: "u-1" }), { "bill-a": "file:///cache/photo.jpg" });
    await saveItem(makeItem({ itemId: "theirs", ownerUserId: "u-2", clientRequestId: "r2" }), { "bill-a": "file:///cache/photo.jpg" });
    await openAs("u-1");
    expect(await screen.findByText(/loaded=true items=mine unsent=1/)).toBeTruthy();
    expect(screen.queryByText(/theirs/)).toBeNull();
  });

  it("shows a new user none of another user's items, and sends none of them", async () => {
    await saveItem(makeItem({ itemId: "theirs", ownerUserId: "u-2" }), { "bill-a": "file:///cache/photo.jpg" });
    await openAs("u-3");
    expect(await screen.findByText(/user=u-3 loaded=true items= unsent=0/)).toBeTruthy();
    expect(server.callsTo("POST /api/v1/my-expenses")).toHaveLength(0);
  });

  it("runs the engine on mount, sends the user's waiting item, and removes it", async () => {
    await saveItem(makeItem({ itemId: "mine" }), { "bill-a": "file:///cache/photo.jpg" });
    await openAs("u-1");
    expect(await screen.findByText(/items= unsent=0/)).toBeTruthy();
    expect(fake.claims[0].status).toBe("SUBMITTED");
  });

  it("keeps an item that cannot be sent and counts it as unsent", async () => {
    server.offline = true;
    await saveItem(makeItem({ itemId: "mine" }), { "bill-a": "file:///cache/photo.jpg" });
    await openAs("u-1");
    expect(await screen.findByText(/items=mine unsent=1/)).toBeTruthy();
    expect(fake.claims).toHaveLength(0);
  });
});
