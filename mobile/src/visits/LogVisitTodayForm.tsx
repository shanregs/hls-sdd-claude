import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import { ApiError, NoConnectionError } from "../api/errors";
import { listProspects, type Prospect, type VisitKind } from "../api/marketingApi";
import { cacheProspects, readListCache } from "../outbox/listCache";
import { useAction } from "../manager/useAction";
import { useSameDayLog, type LogResult } from "../sameDay/useSameDayLog";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_VISIT_TEXT } from "./CompleteVisitForm";
import { VISIT_KINDS, visitKindText } from "./visitMessages";

const PAGE_SIZE = 25;

export const NO_CACHED_SCHOOLS =
  "There is no connection and no School list is saved on this phone yet. Connect once to load the list, then you can log visits offline.";
export const OFFLINE_SCHOOLS =
  "No connection. Showing the Schools saved on this phone. The visit will be sent when you are back online.";

interface Props {
  myUserId: string;
  /** Called once the visit is on the server (SENT) or kept on the phone (QUEUED). */
  onDone: (result: LogResult) => void;
  onCancel: () => void;
}

/**
 * Logs a visit that happened today with no plan (spec 040 US3): a prospect or School, the kind and the outcome. It is saved
 * already Completed, for today. With no connection the School is chosen from the list saved on this phone, and the visit waits
 * on the phone, flagged same-day, and is sent later.
 */
export function LogVisitTodayForm({ myUserId, onDone, onCancel }: Props) {
  const log = useSameDayLog(myUserId);
  const [query, setQuery] = useState("");
  const [prospects, setProspects] = useState<Prospect[] | null>(null);
  const [failed, setFailed] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [chosen, setChosen] = useState<Prospect | null>(null);
  const [kind, setKind] = useState<VisitKind>("VISIT");
  const [outcome, setOutcome] = useState("");
  const [notes, setNotes] = useState("");

  useEffect(() => {
    let cancelled = false;
    listProspects({ query, page: 0, size: PAGE_SIZE })
      .then((page) => {
        if (!cancelled) {
          setProspects(page.content);
          setFailed(null);
          setOffline(false);
          void cacheProspects(myUserId, page.content);
        }
      })
      .catch(async (error: unknown) => {
        if (error instanceof NoConnectionError) {
          const saved = (await readListCache(myUserId))?.prospects ?? [];
          if (cancelled) return;
          const needle = query.trim().toLowerCase();
          const shown = saved.filter((p) => needle === "" || p.name.toLowerCase().includes(needle));
          setProspects(shown.slice(0, PAGE_SIZE));
          setOffline(true);
          setFailed(saved.length === 0 ? NO_CACHED_SCHOOLS : null);
          return;
        }
        if (!cancelled) {
          setProspects([]);
          setOffline(false);
          setFailed(error instanceof ApiError ? error.message : "The list could not be loaded.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [query, myUserId]);

  const send = useAction(async () => {
    const result = await log.logVisit(
      {
        prospectId: chosen?.id ?? "",
        type: kind,
        outcome: outcome.trim(),
        notes: notes.trim() !== "" ? notes.trim() : undefined,
      },
      chosen?.name ?? "Visit",
    );
    onDone(result);
    return true;
  });

  const ready = chosen !== null && outcome.trim() !== "";

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        Log a visit for today
      </Text>
      <Text variant="bodyMedium">It is saved as completed, for today. It does not wait for anyone to approve it.</Text>
      <TextInput
        label="Search a School or prospect"
        value={query}
        onChangeText={setQuery}
        accessibilityLabel="Search a School or prospect"
      />
      {chosen ? <Text variant="titleSmall">Chosen: {chosen.name}</Text> : null}
      {offline && !failed ? <Text variant="bodySmall">{OFFLINE_SCHOOLS}</Text> : null}
      {failed ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {failed}
        </HelperText>
      ) : null}
      {prospects?.map((prospect) => (
        <Button
          key={prospect.id}
          mode={chosen?.id === prospect.id ? "contained" : "outlined"}
          onPress={() => setChosen(prospect)}
          contentStyle={styles.pick}
          accessibilityLabel={`Choose ${prospect.name}`}
        >
          {prospect.name}
          {prospect.won ? " (School)" : ""}
        </Button>
      ))}
      <Text variant="titleSmall">Kind</Text>
      <RadioButton.Group onValueChange={(value) => setKind(value as VisitKind)} value={kind}>
        {VISIT_KINDS.map((option) => (
          <RadioButton.Item
            key={option}
            label={visitKindText(option)}
            value={option}
            accessibilityLabel={visitKindText(option)}
            style={styles.button}
          />
        ))}
      </RadioButton.Group>
      <TextInput
        label="Outcome (required)"
        value={outcome}
        onChangeText={(t) => setOutcome(t.slice(0, MAX_VISIT_TEXT))}
        multiline
        maxLength={MAX_VISIT_TEXT}
        accessibilityLabel="Outcome"
      />
      <TextInput
        label="Notes"
        value={notes}
        onChangeText={(t) => setNotes(t.slice(0, MAX_VISIT_TEXT))}
        multiline
        maxLength={MAX_VISIT_TEXT}
        accessibilityLabel="Notes"
      />
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}
      <Button
        mode="contained"
        onPress={() => void send.run()}
        disabled={!ready || send.sending}
        loading={send.sending}
        contentStyle={styles.button}
        accessibilityLabel="Log visit"
      >
        Log visit
      </Button>
      <Button onPress={onCancel} disabled={send.sending} contentStyle={styles.button} accessibilityLabel="Cancel">
        Cancel
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  form: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
  pick: { minHeight: minTouchTarget, justifyContent: "flex-start" },
});
