export interface GridFilterValues {
  zoneId: string;
  schoolId: string;
  managerId: string;
  status: string;
}

export const NO_FILTERS: GridFilterValues = {
  zoneId: "",
  schoolId: "",
  managerId: "",
  status: "",
};
