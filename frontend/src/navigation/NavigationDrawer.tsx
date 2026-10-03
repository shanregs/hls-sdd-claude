import { useTheme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import {
  Box,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  ListSubheader,
  Toolbar,
} from "@mui/material";
import { useLocation, useNavigate } from "react-router-dom";
import { useAccessModel } from "../access-model/useAccessModel";

export const DRAWER_WIDTH = 260;
export const DRAWER_WIDTH_COLLAPSED = 64;

interface NavigationDrawerProps {
  mobileOpen: boolean;
  onMobileClose: () => void;
  collapsed: boolean;
}

/**
 * Renders strictly from {@link useAccessModel}'s `navigation` array (FR-006) — no per-role menu
 * logic is hard-coded here. A persistent column on desktop/tablet, an overlay drawer below phone
 * width that closes on selection (FR-009, User Story 5).
 */
export function NavigationDrawer({
  mobileOpen,
  onMobileClose,
  collapsed,
}: NavigationDrawerProps) {
  const { accessModel } = useAccessModel();
  const navigate = useNavigate();
  const location = useLocation();
  const theme = useTheme();
  const isPhone = useMediaQuery(theme.breakpoints.down("sm"));

  const handleSelect = (route: string) => {
    navigate(route);
    if (isPhone) {
      onMobileClose();
    }
  };

  const content = (
    <>
      <Toolbar />
      <Box component="nav" aria-label="Main navigation">
        <List sx={{ py: 0 }}>
          {(accessModel?.navigation ?? []).map((section) => (
            <Box
              component="li"
              key={section.section}
              sx={{ listStyle: "none" }}
            >
              {!collapsed && (
                <ListSubheader
                  component="div"
                  disableSticky
                  sx={{
                    fontSize: 12,
                    fontWeight: 800,
                    letterSpacing: "0.08em",
                    textTransform: "uppercase",
                    lineHeight: "36px",
                    mt: 1,
                    color: "primary.main",
                    bgcolor: "action.hover",
                    borderLeft: 4,
                    borderColor: "primary.main",
                  }}
                >
                  {section.section}
                </ListSubheader>
              )}
              {section.items.map((item) => (
                <ListItemButton
                  key={item.route}
                  selected={location.pathname === item.route}
                  onClick={() => handleSelect(item.route)}
                  sx={{ pl: collapsed ? 2 : 4 }}
                >
                  <ListItemText
                    primary={item.label}
                    slotProps={{
                      primary: { sx: { fontSize: 14, fontWeight: 400 } },
                    }}
                    sx={{ opacity: collapsed ? 0 : 1 }}
                  />
                </ListItemButton>
              ))}
            </Box>
          ))}
        </List>
      </Box>
    </>
  );

  if (isPhone) {
    return (
      <Drawer
        variant="temporary"
        open={mobileOpen}
        onClose={onMobileClose}
        ModalProps={{ keepMounted: true }}
        sx={{
          "& .MuiDrawer-paper": {
            width: DRAWER_WIDTH,
            boxSizing: "border-box",
          },
        }}
      >
        {content}
      </Drawer>
    );
  }

  const width = collapsed ? DRAWER_WIDTH_COLLAPSED : DRAWER_WIDTH;
  return (
    <Drawer
      variant="permanent"
      sx={{
        width,
        flexShrink: 0,
        transition: theme.transitions.create("width"),
        "& .MuiDrawer-paper": {
          width,
          boxSizing: "border-box",
          overflowX: "hidden",
        },
      }}
    >
      {content}
    </Drawer>
  );
}
