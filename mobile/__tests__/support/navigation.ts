import { fireEvent, screen } from "@testing-library/react-native";

/** Opens the drawer menu. */
export async function openMenu() {
  await fireEvent.press(screen.getByLabelText("Open menu"));
}

/** Opens the menu and chooses an item by its label. */
export async function chooseMenuItem(label: string) {
  await openMenu();
  await fireEvent.press(await screen.findByLabelText(label));
}

export async function logOutViaMenu() {
  await chooseMenuItem("Log out");
}

/** Home → menu → Profile → Signed-in devices. */
export async function openDevices(profileLabel = "My Profile") {
  await chooseMenuItem(profileLabel);
  await screen.findByText("Appearance");
  await fireEvent.press(screen.getByLabelText("Signed-in devices"));
}
