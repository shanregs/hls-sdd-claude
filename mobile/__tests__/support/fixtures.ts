import type { AccessModel } from "../../src/api/accessModelApi";

const item = (label: string, route: string, actions = ["VIEW"]) => ({ label, route, actions });

/** Access models shaped like the server's real navigation catalog (spec 002) for each role. */
export const TEACHER_MODEL: AccessModel = {
  roles: ["TEACHER"],
  navigation: [
    { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
    {
      section: "MY ATTENDANCE",
      items: [item("My Attendance", "/my-attendance"), item("Attendance History", "/my-attendance/history")],
    },
    {
      section: "ACCOUNT",
      items: [item("My Profile", "/account/profile", ["VIEW", "EDIT"]), item("Settings", "/account/settings")],
    },
  ],
  dataScope: { DASHBOARD: "OWN" },
};

export const MANAGER_MODEL: AccessModel = {
  roles: ["MANAGER"],
  navigation: [
    { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
    {
      section: "MASTER DATA",
      items: [item("Schools", "/master-data/schools"), item("Teachers", "/master-data/teachers")],
    },
    { section: "OPERATIONS", items: [item("Teacher Attendance", "/operations/teacher-attendance")] },
    {
      section: "ACCOUNT",
      items: [item("Profile", "/account/profile", ["VIEW", "EDIT"]), item("Settings", "/account/settings")],
    },
  ],
  dataScope: { DASHBOARD: "ASSIGNED" },
};

export const DIRECTOR_MODEL: AccessModel = {
  roles: ["DIRECTOR"],
  navigation: [
    { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
    {
      section: "MASTER DATA",
      items: [
        item("Zones", "/master-data/zones"),
        item("Schools", "/master-data/schools"),
        item("Managers", "/master-data/managers"),
        item("Teachers", "/master-data/teachers"),
      ],
    },
    { section: "OPERATIONS", items: [item("Attendance", "/operations/attendance")] },
    { section: "SYSTEM", items: [item("Role & Permissions", "/identity/permissions")] },
    {
      section: "ACCOUNT",
      items: [item("Profile", "/account/profile", ["VIEW", "EDIT"]), item("Settings", "/account/settings")],
    },
  ],
  dataScope: { DASHBOARD: "ORG" },
};

/**
 * A user holding Admin and Teacher: the union of both, deliberately with the same route twice
 * (Profile and My Profile share /account/profile) and Dashboard once per role, to prove the app
 * removes duplicates.
 */
export const ADMIN_TEACHER_MODEL: AccessModel = {
  roles: ["ADMIN", "TEACHER"],
  navigation: [
    { section: "Dashboard", items: [item("Dashboard", "/dashboard"), item("Dashboard", "/dashboard")] },
    { section: "SYSTEM", items: [item("User Management", "/identity/users")] },
    { section: "AUDIT", items: [item("Audit Logs", "/audit/logs")] },
    { section: "MY ATTENDANCE", items: [item("My Attendance", "/my-attendance")] },
    {
      section: "ACCOUNT",
      items: [item("Profile", "/account/profile"), item("My Profile", "/account/profile"), item("Settings", "/account/settings")],
    },
  ],
  dataScope: { DASHBOARD: "ORG" },
};

/** A model the server might add later: a section and routes this app has no screen for. */
export const WITH_UNKNOWN_ITEMS: AccessModel = {
  roles: ["TEACHER"],
  navigation: [
    { section: "Dashboard", items: [item("Dashboard", "/dashboard")] },
    { section: "FUTURE", items: [item("Something New", "/future/thing")] },
    {
      section: "ACCOUNT",
      items: [item("My Profile", "/account/profile"), item("Brand New", "/account/brand-new")],
    },
  ],
  dataScope: {},
};

export const PROFILE = {
  id: "u-1",
  displayName: "Tara",
  phone: "9876543210",
  username: "tara",
  email: null,
  roles: ["TEACHER"],
};
