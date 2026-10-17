import { useState } from "react";
import { Linking, StyleSheet, View } from "react-native";
import { Button, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import {
  attachPhoto,
  checkIn,
  type ActivityRef,
  type DayType,
  type ManagerDay,
  type PlannedActivity,
} from "../api/managerAttendanceApi";
import { captureFresh } from "../location/LocationGate";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { ActivityList } from "./ActivityList";
import { fieldDayProblem, locationMessage, managerRefusalText, type LocationProblem } from "./managerMessages";
import { PhotoPicker, type PickedPhoto } from "./PhotoPicker";
import { useAction } from "./useAction";

/** The longest purpose the server accepts. */
export const MAX_PURPOSE = 500;

interface Props {
  activities: PlannedActivity[];
  /** Called once the server has confirmed the check-in. `photoProblem` is set when the photo was refused or failed. */
  onDone: (day: ManagerDay, photoProblem: string | null) => void;
}

/**
 * Today's check-in (spec 033 FR-001 to FR-005): Office or Field, the planned activities or a purpose for a Field day,
 * an optional photo, and a Check in button that takes a fresh position at the tap. Nothing is sent without a position,
 * and every entry is kept until the server confirms the check-in. The photo is uploaded after the check-in, so a refused
 * photo never undoes it.
 */
export function CheckInForm({ activities, onDone }: Props) {
  const [dayType, setDayType] = useState<DayType>("OFFICE");
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [purpose, setPurpose] = useState("");
  const [photo, setPhoto] = useState<PickedPhoto | null>(null);
  const [problem, setProblem] = useState<string | null>(null);
  const [location, setLocation] = useState<LocationProblem | null>(null);

  const submit = useAction(async (): Promise<boolean> => {
    setProblem(null);
    setLocation(null);
    const refs: ActivityRef[] =
      dayType === "FIELD"
        ? activities.filter((a) => selected.has(a.id)).map((a) => ({ kind: a.kind, id: a.id }))
        : [];
    if (dayType === "FIELD") {
      const missing = fieldDayProblem(refs.length, purpose);
      if (missing) {
        setProblem(missing);
        return false;
      }
    }
    const reading = await captureFresh({ askIfNeeded: true });
    if (reading.kind === "none") {
      setLocation(locationMessage(reading.reason));
      return false;
    }
    const day = await checkIn({
      dayType,
      purpose: dayType === "FIELD" && purpose.trim() !== "" ? purpose.trim() : undefined,
      activities: refs,
      latitude: reading.reading.latitude,
      longitude: reading.reading.longitude,
      accuracyMeters: reading.reading.accuracyMeters,
    });
    let photoProblem: string | null = null;
    if (photo) {
      try {
        await attachPhoto(day.date, { uri: photo.uri, name: photo.name, type: photo.type });
      } catch (failure) {
        photoProblem = managerRefusalText(failure);
      }
    }
    onDone(day, photoProblem);
    return true;
  });

  const toggle = (activity: PlannedActivity) => {
    const next = new Set(selected);
    if (next.has(activity.id)) next.delete(activity.id);
    else next.add(activity.id);
    setSelected(next);
    setProblem(null);
  };

  return (
    <View style={styles.form}>
      <Text variant="titleMedium" accessibilityRole="header">
        Check in
      </Text>
      <RadioButton.Group onValueChange={(value) => setDayType(value as DayType)} value={dayType}>
        <RadioButton.Item label="Office" value="OFFICE" accessibilityLabel="Office day" style={styles.radio} />
        <RadioButton.Item label="Field" value="FIELD" accessibilityLabel="Field day" style={styles.radio} />
      </RadioButton.Group>

      {dayType === "FIELD" ? (
        <View style={styles.form}>
          <Text variant="titleSmall">Today's planned activities</Text>
          <ActivityList activities={activities} selected={selected} onToggle={toggle} />
          <TextInput
            label="Purpose (if there is no planned activity)"
            value={purpose}
            onChangeText={(text) => {
              setPurpose(text);
              setProblem(null);
            }}
            maxLength={MAX_PURPOSE}
            multiline
            accessibilityLabel="Purpose"
          />
          <HelperText type="info" visible accessibilityLabel={`${MAX_PURPOSE - purpose.length} characters left`}>
            {MAX_PURPOSE - purpose.length} characters left
          </HelperText>
        </View>
      ) : null}

      <PhotoPicker value={photo} onChange={setPhoto} disabled={submit.sending} />

      {problem ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {problem}
        </HelperText>
      ) : null}
      {location ? (
        <View style={styles.form}>
          <HelperText type="error" visible accessibilityRole="alert">
            {location.text}
          </HelperText>
          {location.canOpenSettings ? (
            <Button onPress={() => void Linking.openSettings()} contentStyle={styles.button}>
              Open settings
            </Button>
          ) : null}
        </View>
      ) : null}
      {submit.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {submit.error}
        </HelperText>
      ) : null}

      <Button
        mode="contained"
        onPress={() => void submit.run()}
        loading={submit.sending}
        disabled={submit.sending}
        contentStyle={styles.button}
        accessibilityLabel="Check in"
      >
        Check in
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  radio: { minHeight: minTouchTarget },
  button: { minHeight: minTouchTarget },
});
