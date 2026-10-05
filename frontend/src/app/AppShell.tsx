import { useState } from "react";
import { Navigate, Outlet } from "react-router-dom";
import {
  AppBar,
  Box,
  Chip,
  IconButton,
  Stack,
  Toolbar,
  Typography,
  Button,
} from "@mui/material";
import MenuIcon from "@mui/icons-material/Menu";
import ChevronLeftIcon from "@mui/icons-material/ChevronLeft";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import LightModeIcon from "@mui/icons-material/LightMode";
import DarkModeIcon from "@mui/icons-material/DarkMode";
import { useAuth } from "../auth/useAuth";
import { useThemeMode } from "../theme/ThemeModeProvider";
import { NavigationDrawer } from "../navigation/NavigationDrawer";
import { NotificationBell } from "../features/notifications/NotificationBell";

/**
 * The shared app shell (FR-009, FR-011, FR-013): top bar, persistent/collapsible left navigation,
 * and a router outlet for the current screen. Every later spec's screens render inside this
 * outlet, guarded by {@code RouteGuard}.
 */
export function AppShell() {
  const { user, logout } = useAuth();
  const { mode, toggleMode } = useThemeMode();
  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);

  if (!user) {
    return <Navigate to="/sign-in" replace />;
  }

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar
        position="fixed"
        sx={{
          zIndex: (theme) => theme.zIndex.drawer + 1,
          bgcolor: "background.paper",
          color: "text.primary",
          borderBottom: "1px solid",
          borderColor: "divider",
        }}
        elevation={0}
      >
        <Toolbar sx={{ gap: 1 }}>
          <IconButton
            aria-label="Open navigation"
            edge="start"
            onClick={() => setMobileOpen(true)}
            sx={{ display: { xs: "inline-flex", sm: "none" } }}
          >
            <MenuIcon />
          </IconButton>
          <IconButton
            aria-label={collapsed ? "Expand navigation" : "Collapse navigation"}
            edge="start"
            onClick={() => setCollapsed((c) => !c)}
            sx={{ display: { xs: "none", sm: "inline-flex" } }}
          >
            {collapsed ? <ChevronRightIcon /> : <ChevronLeftIcon />}
          </IconButton>
          <Typography variant="subtitle1" sx={{ fontWeight: 700, flexGrow: 1 }}>
            HLS Teacher Management System
          </Typography>
          <Stack
            direction="row"
            spacing={1}
            sx={{ display: { xs: "none", sm: "flex" } }}
          >
            {user.roles.map((role) => (
              <Chip key={role} label={role} size="small" />
            ))}
          </Stack>
          <NotificationBell />
          <IconButton
            aria-label={
              mode === "dark" ? "Switch to light theme" : "Switch to dark theme"
            }
            onClick={toggleMode}
          >
            {mode === "dark" ? <LightModeIcon /> : <DarkModeIcon />}
          </IconButton>
          <Typography
            variant="body2"
            sx={{ display: { xs: "none", md: "block" } }}
          >
            {user.displayName}
          </Typography>
          <Button size="small" onClick={() => logout()}>
            Logout
          </Button>
        </Toolbar>
      </AppBar>

      <NavigationDrawer
        mobileOpen={mobileOpen}
        onMobileClose={() => setMobileOpen(false)}
        collapsed={collapsed}
      />

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0,
          p: { xs: 2, sm: 3 },
          mt: "64px",
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
}
