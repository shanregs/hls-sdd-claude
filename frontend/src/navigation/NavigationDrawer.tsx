import { useTheme } from "@mui/material/styles";
import useMediaQuery from "@mui/material/useMediaQuery";
import { useState } from "react";
import {
  Box,
  Collapse,
  Drawer,
  List,
  ListItemButton,
  ListItemText,
  Toolbar,
} from "@mui/material";
import ExpandLess from "@mui/icons-material/ExpandLess";
import ExpandMore from "@mui/icons-material/ExpandMore";
import { useLocation, useNavigate } from "react-router-dom";
import { useAccessModel } from "../access-model/useAccessModel";

export const DRAWER_WIDTH = 260;
export const DRAWER_WIDTH_COLLAPSED = 64;

const COLLAPSED_SECTIONS_KEY = "hls.nav.collapsedSections";

function loadCollapsedSections(): Set<string> {
  try {
    const raw = window.localStorage.getItem(COLLAPSED_SECTIONS_KEY);
    return new Set(raw ? (JSON.parse(raw) as string[]) : []);
  } catch {
    return new Set();
  }
}

function saveCollapsedSections(sections: Set<string>) {
  try {
    window.localStorage.setItem(
      COLLAPSED_SECTIONS_KEY,
      JSON.stringify([...sections]),
    );
  } catch {
    // Storage can be unavailable (private windows); the choice then lasts for this visit only.
  }
}

interface NavigationDrawerProps {
  mobileOpen: boolean;
  onMobileClose: () => void;
  collapsed: boolean;
}

function sectionId(section: string): string {
  return section.toLowerCase().replace(/[^a-z0-9]+/g, "-");
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
  const [hiddenSections, setHiddenSections] = useState(loadCollapsedSections);

  const toggleSection = (section: string) => {
    setHiddenSections((current) => {
      const next = new Set(current);
      if (next.has(section)) next.delete(section);
      else next.add(section);
      saveCollapsedSections(next);
      return next;
    });
  };

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
                <ListItemButton
                  onClick={() => toggleSection(section.section)}
                  aria-expanded={!hiddenSections.has(section.section)}
                  aria-controls={`nav-section-${sectionId(section.section)}`}
                  sx={{
                    mt: 1,
                    py: 0.5,
                    color: "primary.main",
                    bgcolor: "action.hover",
                    borderLeft: 4,
                    borderColor: "primary.main",
                  }}
                >
                  <ListItemText
                    primary={section.section}
                    slotProps={{
                      primary: {
                        sx: {
                          fontSize: 12,
                          fontWeight: 800,
                          letterSpacing: "0.08em",
                          textTransform: "uppercase",
                          lineHeight: "30px",
                        },
                      },
                    }}
                  />
                  {hiddenSections.has(section.section) ? (
                    <ExpandMore fontSize="small" />
                  ) : (
                    <ExpandLess fontSize="small" />
                  )}
                </ListItemButton>
              )}
              <Collapse
                in={collapsed || !hiddenSections.has(section.section)}
                unmountOnExit
                id={`nav-section-${sectionId(section.section)}`}
              >
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
              </Collapse>
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
