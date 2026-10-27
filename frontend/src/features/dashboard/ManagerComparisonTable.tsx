import { useMemo, useState } from "react";
import { Link as RouterLink } from "react-router-dom";
import {
  Link,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
} from "@mui/material";
import type { TileLink, TileTable } from "./dashboardApi";
import { formatValue } from "./format";

function target(link: TileLink) {
  const search = new URLSearchParams(link.query).toString();
  return { pathname: link.path, search: search === "" ? "" : `?${search}` };
}

/**
 * The Manager-by-Manager table (spec 028 US2): sortable columns, each figure a link to that Manager's list. It scrolls
 * inside its own container so the page never scrolls sideways at phone width.
 */
export function ManagerComparisonTable({ table }: { table: TileTable }) {
  const [sortColumn, setSortColumn] = useState<number>(0);
  const [direction, setDirection] = useState<"asc" | "desc">("asc");

  const rows = useMemo(() => {
    const sorted = [...table.rows].sort((a, b) => {
      const x = a[sortColumn];
      const y = b[sortColumn];
      const numeric = typeof x?.value === "number" && typeof y?.value === "number";
      const result = numeric
        ? Number(x.value) - Number(y.value)
        : String(x?.value ?? "").localeCompare(String(y?.value ?? ""));
      return direction === "asc" ? result : -result;
    });
    return sorted;
  }, [table.rows, sortColumn, direction]);

  function sortBy(column: number) {
    if (column === sortColumn) {
      setDirection(direction === "asc" ? "desc" : "asc");
    } else {
      setSortColumn(column);
      setDirection(column === 0 ? "asc" : "desc");
    }
  }

  return (
    <TableContainer sx={{ overflowX: "auto", maxWidth: "100%", mt: 1 }}>
      <Table
        size="small"
        aria-label="Managers compared"
        sx={{
          "& th, & td": { whiteSpace: "nowrap" },
          // Keep the Manager's name in view while the figures scroll.
          "& th:first-of-type, & td:first-of-type": {
            position: "sticky",
            left: 0,
            zIndex: 1,
            bgcolor: "background.paper",
          },
        }}
      >
        <TableHead>
          <TableRow>
            {table.columns.map((label, index) => (
              <TableCell
                key={label}
                align={index === 0 ? "left" : "right"}
                sortDirection={sortColumn === index ? direction : false}
              >
                <TableSortLabel
                  active={sortColumn === index}
                  direction={sortColumn === index ? direction : "asc"}
                  onClick={() => sortBy(index)}
                >
                  {label}
                </TableSortLabel>
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row, rowIndex) => (
            <TableRow key={`${row[0]?.value}-${rowIndex}`}>
              {row.map((cell, index) => (
                <TableCell key={index} align={index === 0 ? "left" : "right"}>
                  {cell.link ? (
                    <Link
                      component={RouterLink}
                      to={target(cell.link)}
                      underline="hover"
                      aria-label={`${row[0]?.value}: ${cell.label} ${formatValue(cell)}`}
                    >
                      {formatValue(cell)}
                    </Link>
                  ) : (
                    formatValue(cell)
                  )}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
