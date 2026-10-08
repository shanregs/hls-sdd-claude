import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, BackHandler, StyleSheet, View } from "react-native";
import { Appbar, Button, Drawer, Modal, Portal, Text, useTheme } from "react-native-paper";
import { AccessModelProvider, useAccessModel } from "../access/AccessModelProvider";
import { HOME_ROUTE, NOTIFICATIONS_ROUTE, screenFor } from "../access/screenRegistry";
import { buildMenu } from "../access/useMenu";
import type { SignedInUser } from "../api/authApi";
import type { LeaveStatus } from "../api/leaveApi";
import { useAuth } from "../auth/AuthProvider";
import { ApplyLeaveScreen } from "../screens/ApplyLeaveScreen";
import { AttendanceHistoryScreen } from "../screens/AttendanceHistoryScreen";
import { DevicesScreen } from "../screens/DevicesScreen";
import { HolidayCalendarScreen } from "../screens/HolidayCalendarScreen";
import { LeaveManagementScreen } from "../screens/LeaveManagementScreen";
import { MyLeaveHistoryScreen } from "../screens/MyLeaveHistoryScreen";
import { MyAttendanceScreen } from "../screens/MyAttendanceScreen";
import { TeacherAttendanceScreen } from "../screens/TeacherAttendanceScreen";
import { LocationPrivacyScreen } from "../screens/LocationPrivacyScreen";
import { HomeScreen } from "../screens/HomeScreen";
import { NoConnectionScreen } from "../screens/NoConnectionScreen";
import { NotAuthorizedScreen } from "../screens/NotAuthorizedScreen";
import { NotificationBell } from "../notifications/NotificationBell";
import { NotificationsProvider } from "../notifications/NotificationsProvider";
import { NotificationsScreen } from "../screens/NotificationsScreen";
import { ProfileScreen } from "../screens/ProfileScreen";
import { Screen } from "../screens/Screen";
import { minTouchTarget, spacingUnit } from "../theme/tokens";
import { InnerBackContext, type InnerBackHandler, type InnerBackRegistry } from "./InnerBack";

/**
 * The signed-in frame: a header with a menu drawer built only from the server-provided access
 * model (spec FR-009 to FR-013). Items the app has no screen for are not shown, and a destination
 * that is not in the user's navigation shows "Not authorized". On a phone the navigation is a
 * drawer (Constitution Principle IV).
 */
export function AppShell({ user }: { user: SignedInUser }) {
  return (
    <AccessModelProvider>
      <NotificationsProvider>
        <ShellContent user={user} />
      </NotificationsProvider>
    </AccessModelProvider>
  );
}

/** What a screen is opened with: a notice to show (after a submit) or the status to filter by (from Home). */
export interface RouteState {
  notice?: string;
  status?: LeaveStatus;
  /** The month ("YYYY-MM") My Attendance opens on, from a notification link. */
  month?: string;
}

/** Opens a screen by its route, through the same menu check as the drawer. */
export type OpenRoute = (route: string, state?: RouteState) => void;

type Overlay = "none" | "devices" | "privacy" | "notAuthorized";

function ShellContent({ user }: { user: SignedInUser }) {
  const { model, status, refresh, canOpen } = useAccessModel();
  const { logout } = useAuth();
  const theme = useTheme();
  const [route, setRoute] = useState(HOME_ROUTE);
  const [overlay, setOverlay] = useState<Overlay>("none");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const innerBack = useRef<InnerBackHandler | null>(null);
  const innerBackApi = useMemo<InnerBackRegistry>(
    () => ({
      set: (handler) => {
        innerBack.current = handler;
      },
      clear: (handler) => {
        if (innerBack.current === handler) innerBack.current = null;
      },
    }),
    [],
  );

  const menu = useMemo(() => buildMenu(model), [model]);

  const [routeState, setRouteState] = useState<RouteState | undefined>(undefined);

  const openRoute = useCallback<OpenRoute>(
    (target, state) => {
      setDrawerOpen(false);
      if (canOpen(target)) {
        setOverlay("none");
        setRoute(target);
        setRouteState(state);
      } else {
        setOverlay("notAuthorized");
      }
    },
    [canOpen],
  );
  const goTo = openRoute;

  const goHome = useCallback(() => {
    setOverlay("none");
    setRoute(HOME_ROUTE);
    setRouteState(undefined);
  }, []);

  // Android back: close the drawer, then a sub-screen, then go home, otherwise leave the app.
  useEffect(() => {
    const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
      if (drawerOpen) {
        setDrawerOpen(false);
        return true;
      }
      if (overlay !== "none") {
        setOverlay("none");
        return true;
      }
      // A screen with its own sub-screen (a Teacher's month, the year's holidays) closes it first.
      if (innerBack.current?.()) {
        return true;
      }
      if (route !== HOME_ROUTE) {
        setRoute(HOME_ROUTE);
        return true;
      }
      return false;
    });
    return () => subscription.remove();
  }, [drawerOpen, overlay, route]);

  if (status === "loading") {
    return (
      <View style={styles.center} accessibilityLabel="Loading your menu">
        <ActivityIndicator size="large" />
      </View>
    );
  }
  if (status === "noConnection") {
    return <NoConnectionScreen onRetry={refresh} />;
  }
  if (status === "error" || !model) {
    return (
      <Screen>
        <Text variant="headlineMedium" accessibilityRole="header">
          Something went wrong
        </Text>
        <Text variant="bodyLarge">We couldn't load your menu.</Text>
        <Button mode="contained" onPress={refresh} contentStyle={styles.buttonContent} accessibilityLabel="Retry">
          Retry
        </Button>
      </Screen>
    );
  }

  if (overlay === "devices") {
    return <DevicesScreen onBack={() => setOverlay("none")} />;
  }
  if (overlay === "privacy") {
    return <LocationPrivacyScreen onBack={() => setOverlay("none")} />;
  }

  const current = screenFor(route);
  // A destination the user no longer has (for example after their permissions changed on the web
  // while they were on that screen) shows "Not authorized", never the screen (spec FR-013).
  const unauthorized = overlay === "notAuthorized" || (overlay === "none" && !canOpen(route));
  let body;
  let title: string;
  if (unauthorized) {
    body = <NotAuthorizedScreen onGoHome={goHome} />;
    title = "Not authorized";
  } else if (current === "profile") {
    body = <ProfileScreen onOpenDevices={() => setOverlay("devices")} onOpenPrivacy={() => setOverlay("privacy")} />;
    title = "Profile";
  } else if (current === "myAttendance") {
    body = <MyAttendanceScreen initialMonth={routeState?.month} />;
    title = "My Attendance";
  } else if (current === "attendanceHistory") {
    body = <AttendanceHistoryScreen />;
    title = "Attendance History";
  } else if (current === "teacherAttendance") {
    body = <TeacherAttendanceScreen />;
    title = "Teacher Attendance";
  } else if (current === "holidayCalendar") {
    body = <HolidayCalendarScreen />;
    title = "Holiday Calendar";
  } else if (current === "applyLeave") {
    body = <ApplyLeaveScreen openRoute={openRoute} />;
    title = "Apply Leave";
  } else if (current === "myLeaveHistory") {
    body = <MyLeaveHistoryScreen notice={routeState?.notice} />;
    title = "My Leave History";
  } else if (current === "leaveManagement") {
    body = <LeaveManagementScreen initialStatus={routeState?.status} />;
    title = "Leave Management";
  } else if (current === "notifications") {
    body = <NotificationsScreen openRoute={openRoute} canOpen={canOpen} />;
    title = "Notifications";
  } else {
    body = <HomeScreen user={user} model={model} openRoute={openRoute} />;
    title = "Home";
  }

  const labelOf = (item: { label: string }) => item.label;

  return (
    <InnerBackContext.Provider value={innerBackApi}>
    <View style={styles.fill}>
      <Appbar.Header>
        <Appbar.Action icon="menu" onPress={() => setDrawerOpen(true)} accessibilityLabel="Open menu" />
        <Appbar.Content title={title} />
        <NotificationBell onOpen={() => openRoute(NOTIFICATIONS_ROUTE)} />
      </Appbar.Header>
      {body}

      <Portal>
        <Modal
          visible={drawerOpen}
          onDismiss={() => setDrawerOpen(false)}
          contentContainerStyle={[styles.drawer, { backgroundColor: theme.colors.surface }]}
        >
          <Text variant="titleMedium" style={styles.drawerTitle} accessibilityRole="header">
            {user.displayName}
          </Text>
          {menu.map((section) => (
            <Drawer.Section key={section.section} title={section.section}>
              {section.items.map((item) => (
                <Drawer.Item
                  key={item.route}
                  label={labelOf(item)}
                  active={overlay === "none" && item.route === route}
                  onPress={() => goTo(item.route)}
                  accessibilityLabel={item.label}
                />
              ))}
              {section.section === "ACCOUNT" ? (
                <Drawer.Item
                  label="Log out"
                  onPress={() => {
                    setDrawerOpen(false);
                    void logout();
                  }}
                  accessibilityLabel="Log out"
                />
              ) : null}
            </Drawer.Section>
          ))}
          {menu.every((s) => s.section !== "ACCOUNT") ? (
            <Drawer.Section title="ACCOUNT">
              <Drawer.Item
                label="Log out"
                onPress={() => {
                  setDrawerOpen(false);
                  void logout();
                }}
                accessibilityLabel="Log out"
              />
            </Drawer.Section>
          ) : null}
        </Modal>
      </Portal>
    </View>
    </InnerBackContext.Provider>
  );
}

const styles = StyleSheet.create({
  fill: { flex: 1 },
  center: { flex: 1, alignItems: "center", justifyContent: "center" },
  drawer: { margin: 0, padding: spacingUnit * 2, height: "100%", width: "80%", alignSelf: "flex-start" },
  drawerTitle: { marginBottom: spacingUnit * 2 },
  buttonContent: { minHeight: minTouchTarget },
});
