import { useEffect, useState } from "react";
import { StyleSheet, View } from "react-native";
import { Button, Checkbox, HelperText, RadioButton, Text, TextInput } from "react-native-paper";
import { ApiError } from "../api/errors";
import {
  listColleagues,
  listProspects,
  planVisit,
  type Person,
  type Prospect,
  type Visit,
  type VisitKind,
} from "../api/marketingApi";
import { formatIsoDate } from "../formats/dates";
import { DateChooser } from "../leave/DateChooser";
import { useAction } from "../manager/useAction";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { MAX_VISIT_TEXT } from "./CompleteVisitForm";
import { VISIT_KINDS, visitKindText } from "./visitMessages";

const PAGE_SIZE = 25;

interface Props {
  /** Called once the server has planned the visit, with the server's visit. */
  onDone: (visit: Visit) => void;
  onCancel: () => void;
}

interface Found {
  query: string;
  prospects: Prospect[];
  total: number;
  page: number;
  failed: string | null;
}

/**
 * Plans a visit (spec 034 US4): one prospect from the server's search (a won prospect is the School), the kind, a date, optional
 * colleagues and notes. Send needs a prospect and a date; every other rule is the server's, shown with the entries kept.
 */
export function PlanVisitForm({ onDone, onCancel }: Props) {
  const [query, setQuery] = useState("");
  const [found, setFound] = useState<Found | null>(null);
  const [chosen, setChosen] = useState<Prospect | null>(null);
  const [kind, setKind] = useState<VisitKind>("VISIT");
  const [date, setDate] = useState<string | null>(null);
  const [colleagues, setColleagues] = useState<Person[]>([]);
  const [picked, setPicked] = useState<ReadonlySet<string>>(new Set());
  const [notes, setNotes] = useState("");

  useEffect(() => {
    let cancelled = false;
    listProspects({ query, page: 0, size: PAGE_SIZE })
      .then((page) => {
        if (!cancelled) setFound({ query, prospects: page.content, total: page.totalElements, page: 0, failed: null });
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFound({ query, prospects: [], total: 0, page: 0, failed: error instanceof ApiError ? error.message : "The list could not be loaded." });
        }
      });
    return () => {
      cancelled = true;
    };
  }, [query]);

  useEffect(() => {
    let cancelled = false;
    listColleagues()
      .then((people) => {
        if (!cancelled) setColleagues(people);
      })
      .catch(() => {
        // The colleagues are optional; without the list the visit is planned for the Manager alone.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadMore = async () => {
    if (!found) return;
    try {
      const next = await listProspects({ query: found.query, page: found.page + 1, size: PAGE_SIZE });
      setFound({ ...found, prospects: [...found.prospects, ...next.content], total: next.totalElements, page: found.page + 1, failed: null });
    } catch (error) {
      setFound({ ...found, failed: error instanceof ApiError ? error.message : "More could not be loaded." });
    }
  };

  const send = useAction(async () => {
    const created = await planVisit({
      prospectId: chosen?.id ?? "",
      type: kind,
      date: date ?? "",
      attendeeUserIds: picked.size > 0 ? [...picked] : undefined,
      notes: notes.trim() !== "" ? notes.trim() : undefined,
    });
    onDone(created);
    return true;
  });

  const missing = [chosen ? null : "a School or prospect", date ? null : "a date"].filter((m): m is string => m !== null);
  const toggle = (id: string) => {
    const next = new Set(picked);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    setPicked(next);
  };

  return (
    <View style={styles.form}>
      <Text variant="titleLarge" accessibilityRole="header">
        Plan a visit
      </Text>

      <TextInput
        label="Search a School or prospect"
        value={query}
        onChangeText={setQuery}
        accessibilityLabel="Search a School or prospect"
      />
      {chosen ? <Text variant="titleSmall">Chosen: {chosen.name}</Text> : null}
      {found?.failed ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {found.failed}
        </HelperText>
      ) : null}
      {found && found.prospects.length === 0 && !found.failed ? <Text variant="bodyMedium">No School or prospect matches.</Text> : null}
      {found?.prospects.map((prospect) => (
        <Button
          key={prospect.id}
          mode={chosen?.id === prospect.id ? "contained" : "outlined"}
          onPress={() => setChosen(prospect)}
          contentStyle={styles.pick}
          accessibilityLabel={`Choose ${prospect.name}`}
        >
          {prospect.name}
          {prospect.won ? " (School)" : ""}
          {prospect.zoneName ? `, ${prospect.zoneName}` : ""}
          {prospect.contactPerson ? `, ${prospect.contactPerson}` : ""}
        </Button>
      ))}
      {found && found.prospects.length < found.total ? (
        <Button onPress={() => void loadMore()} contentStyle={styles.button}>
          Load more
        </Button>
      ) : null}

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

      <DateChooser label="Date" value={date} onChange={setDate} />
      {date ? <Text variant="bodySmall">Planned for {formatIsoDate(date)}</Text> : null}

      {colleagues.length > 0 ? (
        <View>
          <Text variant="titleSmall">Colleagues attending (optional)</Text>
          {colleagues.map((person) => (
            <Checkbox.Item
              key={person.userId}
              label={person.name}
              status={picked.has(person.userId) ? "checked" : "unchecked"}
              onPress={() => toggle(person.userId)}
              accessibilityLabel={`${person.name}, ${picked.has(person.userId) ? "attending" : "not attending"}`}
              style={styles.button}
            />
          ))}
        </View>
      ) : null}

      <TextInput
        label="Notes"
        value={notes}
        onChangeText={(t) => setNotes(t.slice(0, MAX_VISIT_TEXT))}
        multiline
        maxLength={MAX_VISIT_TEXT}
        accessibilityLabel="Notes"
      />
      <HelperText type="info" visible accessibilityLabel={`${MAX_VISIT_TEXT - notes.length} characters left in the notes`}>
        {MAX_VISIT_TEXT - notes.length} characters left.
      </HelperText>

      {missing.length > 0 ? (
        <HelperText type="info" visible accessibilityLabel={`Still needed: ${missing.join(" and ")}`}>
          Still needed: {missing.join(" and ")}.
        </HelperText>
      ) : null}
      {send.error ? (
        <HelperText type="error" visible accessibilityRole="alert">
          {send.error}
        </HelperText>
      ) : null}
      <Button
        mode="contained"
        onPress={() => void send.run()}
        disabled={missing.length > 0 || send.sending}
        loading={send.sending}
        contentStyle={styles.button}
        accessibilityLabel="Send"
      >
        Send
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
