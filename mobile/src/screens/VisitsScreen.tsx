import { useState } from "react";
import { ActivityIndicator, ScrollView, StyleSheet, View } from "react-native";
import { Button, Chip, Text } from "react-native-paper";
import { VISITS_ROUTE } from "../access/screenRegistry";
import { useMenuActions } from "../access/useMenuActions";
import { Problem } from "../attendance/MonthPane";
import { MonthPicker } from "../attendance/MonthPicker";
import { currentMonth } from "../attendance/monthRange";
import { businessToday, serverNow } from "../attendance/serverClock";
import { useInnerBack } from "../navigation/InnerBack";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { VisitCard } from "../visits/VisitCard";
import { PlanVisitForm } from "../visits/PlanVisitForm";
import { LogVisitTodayForm } from "../visits/LogVisitTodayForm";
import { useAuth } from "../auth/AuthProvider";
import { useSameDayLog } from "../sameDay/useSameDayLog";
import { VisitDetailPane } from "../visits/VisitDetailPane";
import { filterVisits, groupVisits, useVisits, VISIT_FILTERS, type VisitFilter } from "../visits/useVisits";
import { Screen } from "./Screen";

const GROUPS = [
  { key: "today", title: "Today" },
  { key: "later", title: "Later" },
  { key: "earlier", title: "Earlier" },
] as const;

/**
 * The Manager's visit diary (spec 034 US1): one month at a time, grouped today, later and earlier, filtered by the server's
 * status, with a detail inside the screen (Back closes it). Statuses, including Missed, are the server's.
 */
export function VisitsScreen() {
  const { canCreate, canEdit } = useMenuActions(VISITS_ROUTE);
  const [month, setMonth] = useState(() => currentMonth(serverNow()));
  const [filter, setFilter] = useState<VisitFilter>("ALL");
  const { status, view, message, reload } = useVisits(month);
  const [openId, setOpenId] = useState<string | null>(null);
  const [planning, setPlanning] = useState(false);
  const [logging, setLogging] = useState(false);
  const [saved, setSaved] = useState<string | null>(null);
  const { state: auth } = useAuth();
  const myUserId = auth.status === "signedIn" ? auth.user.id : null;
  const sameDay = useSameDayLog(myUserId);

  // Leaving a detail reloads the list quietly, since the visit may have changed or gone while it was open.
  const closeDetail = () => {
    setOpenId(null);
    reload({ quiet: true });
  };
  useInnerBack(openId !== null || planning || logging, () =>
    logging ? setLogging(false) : planning ? setPlanning(false) : closeDetail(),
  );

  if (logging && myUserId) {
    return (
      <Screen>
        <LogVisitTodayForm
          myUserId={myUserId}
          onCancel={() => setLogging(false)}
          onDone={(result) => {
            setLogging(false);
            setSaved(
              result === "QUEUED"
                ? "Saved on this phone. It will be sent when you are back online."
                : "Visit logged for today.",
            );
            reload({ quiet: true });
          }}
        />
      </Screen>
    );
  }

  if (planning) {
    return (
      <Screen>
        <PlanVisitForm
          onCancel={() => setPlanning(false)}
          onDone={(created) => {
            setPlanning(false);
            setOpenId(created.id);
            reload({ quiet: true });
          }}
        />
      </Screen>
    );
  }

  if (openId) {
    return (
      <Screen>
        <VisitDetailPane
          id={openId}
          canCreate={canCreate}
          canEdit={canEdit}
          onChanged={() => reload({ quiet: true })}
          onBack={closeDetail}
        />
      </Screen>
    );
  }

  const groups = view ? groupVisits(filterVisits(view, filter), businessToday()) : null;
  const none = groups !== null && groups.today.length + groups.later.length + groups.earlier.length === 0;

  return (
    <Screen onRefresh={() => reload({ quiet: true })}>
      {saved ? (
        <Text variant="bodyMedium" accessibilityLiveRegion="polite">
          {saved}
        </Text>
      ) : null}
      {sameDay.waiting.length > 0 ? (
        <Text variant="bodyMedium" accessibilityLiveRegion="polite">
          {sameDay.waiting.length} logged for today and waiting to be sent.
        </Text>
      ) : null}
      {canCreate ? (
        <Button mode="outlined" onPress={() => setLogging(true)} contentStyle={styles.button} accessibilityLabel="Log a visit for today">
          Log a visit for today
        </Button>
      ) : null}
      {canCreate ? (
        <Button mode="contained" onPress={() => setPlanning(true)} contentStyle={styles.button} accessibilityLabel="Plan a visit">
          Plan a visit
        </Button>
      ) : null}
      <MonthPicker value={month} kind="leave" onChange={setMonth} />
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.chips} accessibilityLabel="Filter by status">
        {VISIT_FILTERS.map((option) => (
          <Chip
            key={option.value}
            selected={option.value === filter}
            showSelectedCheck
            onPress={() => setFilter(option.value)}
            accessibilityLabel={option.label}
          >
            {option.label}
          </Chip>
        ))}
      </ScrollView>

      {status === "loading" ? <ActivityIndicator size="large" accessibilityLabel="Loading visits" /> : null}
      {status === "noConnection" ? (
        <Problem title="No connection" text="Check your internet connection and try again." onRetry={() => reload()} />
      ) : null}
      {status === "error" || status === "notFound" ? (
        <Problem title="Something went wrong" text={message ?? "We couldn't load the visits."} onRetry={() => reload()} />
      ) : null}

      {none ? <Text variant="bodyMedium">No visits this month.{canCreate ? " Plan a visit to add one." : ""}</Text> : null}
      {groups
        ? GROUPS.map(({ key, title }) =>
            groups[key].length > 0 ? (
              <View key={key} style={styles.group}>
                <Text variant="titleMedium" accessibilityRole="header">
                  {title}
                </Text>
                {groups[key].map((visit) => (
                  <VisitCard key={visit.id} visit={visit} onPress={() => setOpenId(visit.id)} />
                ))}
              </View>
            ) : null,
          )
        : null}
    </Screen>
  );
}

const styles = StyleSheet.create({
  chips: { gap: spacingUnit, paddingVertical: spacingUnit / 2 },
  group: { gap: spacingUnit },
  button: { minHeight: minTouchTarget },
});
