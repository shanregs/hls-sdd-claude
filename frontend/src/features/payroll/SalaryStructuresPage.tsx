import { useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import { Box, Button, Stack, Tab, Tabs, Typography } from "@mui/material";
import { DesignationDefaultsTab } from "./DesignationDefaultsTab";
import { PeopleTab } from "./PeopleTab";

export const SALARY_STRUCTURES_ROUTE = "/operations/salary-structures";

type TabKey = "people" | "defaults";

/**
 * OPERATIONS -> Salary Structures (spec 013a). HLS has no fixed pay scale, so the screen is about each person's own
 * salary: the People tab lists everyone with the salary in effect and flags those with none. The designation amounts
 * are only suggested defaults, kept on the second tab. Shown to Admin and Director only.
 */
export function SalaryStructuresPage() {
  const [tab, setTab] = useState<TabKey>("people");

  return (
    <Box>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        spacing={1}
        sx={{
          alignItems: { sm: "center" },
          justifyContent: "space-between",
          mb: 2,
        }}
      >
        <Typography variant="h5" component="h1">
          Salary Structures
        </Typography>
        <Button
          component={RouterLink}
          to={`${SALARY_STRUCTURES_ROUTE}/policy`}
          variant="outlined"
        >
          Pay policy
        </Button>
      </Stack>

      <Tabs
        value={tab}
        onChange={(_event, value: TabKey) => setTab(value)}
        aria-label="Salary structures"
        variant="scrollable"
        sx={{ mb: 2, borderBottom: 1, borderColor: "divider" }}
      >
        <Tab value="people" label="People" />
        <Tab value="defaults" label="Designation defaults" />
      </Tabs>

      {tab === "people" ? <PeopleTab /> : <DesignationDefaultsTab />}
    </Box>
  );
}
