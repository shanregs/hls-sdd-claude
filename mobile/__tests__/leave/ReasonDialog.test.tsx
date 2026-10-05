import { act, fireEvent, render, screen } from "@testing-library/react-native";
import { PaperProvider } from "react-native-paper";
import { ReasonDialog } from "../../src/leave/ReasonDialog";

async function show(over: Partial<React.ComponentProps<typeof ReasonDialog>> = {}) {
  const props = {
    title: "Reject this request?",
    fieldLabel: "Reason",
    required: true,
    confirmLabel: "Reject",
    onConfirm: jest.fn(async () => null as string | null),
    onCancel: jest.fn(),
    ...over,
  };
  await render(
    <PaperProvider>
      <ReasonDialog {...props} />
    </PaperProvider>,
  );
  return props;
}

describe("ReasonDialog", () => {
  it("blocks an empty or blank required reason", async () => {
    const props = await show();
    expect(screen.getByLabelText("Reject").props.accessibilityState.disabled).toBe(true);

    await fireEvent.changeText(screen.getByLabelText("Reason"), "   ");
    expect(screen.getByLabelText("Reject").props.accessibilityState.disabled).toBe(true);

    await fireEvent.changeText(screen.getByLabelText("Reason"), " Not enough cover ");
    await fireEvent.press(screen.getByLabelText("Reject"));
    expect(props.onConfirm).toHaveBeenCalledWith("Not enough cover");
  });

  it("allows an empty optional note", async () => {
    const props = await show({ title: "Approve?", fieldLabel: "Note", required: false, confirmLabel: "Approve" });

    await fireEvent.press(screen.getByLabelText("Approve"));
    expect(props.onConfirm).toHaveBeenCalledWith("");
  });

  it("limits the text to 500 characters and shows what is left", async () => {
    await show();
    await fireEvent.changeText(screen.getByLabelText("Reason"), "x".repeat(600));

    expect(screen.getByLabelText("Reason").props.value).toHaveLength(500);
    expect(screen.getByText("0 characters left")).toBeTruthy();
  });

  it("keeps the text and shows the reason when the server refuses, and does not close", async () => {
    const props = await show({ onConfirm: jest.fn(async () => "This request was already approved.") });

    await fireEvent.changeText(screen.getByLabelText("Reason"), "Busy week");
    await fireEvent.press(screen.getByLabelText("Reject"));

    expect(await screen.findByText("This request was already approved.")).toBeTruthy();
    expect(screen.getByLabelText("Reason").props.value).toBe("Busy week");
    expect(props.onCancel).not.toHaveBeenCalled();
  });

  it("shows no text field for a plain confirmation", async () => {
    await show({ fieldLabel: undefined, required: false, confirmLabel: "Cancel request", title: "Cancel this request?" });

    expect(screen.queryByLabelText("Reason")).toBeNull();
    expect(await screen.findByLabelText("Cancel request")).toBeTruthy();
  });

  it("sends once even when Confirm is tapped twice", async () => {
    let release: (value: string | null) => void = () => undefined;
    const onConfirm = jest.fn(() => new Promise<string | null>((resolve) => (release = resolve)));
    await show({ onConfirm, fieldLabel: undefined, required: false, title: "Cancel this request?", confirmLabel: "Cancel request" });

    fireEvent.press(screen.getByLabelText("Cancel request"));
    fireEvent.press(screen.getByLabelText("Cancel request"));
    expect(onConfirm).toHaveBeenCalledTimes(1);
    await act(async () => release(null));
  });
});
