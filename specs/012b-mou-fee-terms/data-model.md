# Data Model: MoU Fee Terms (spec 012b)

Flyway `V170__contract_fee_terms.sql`. All changes are inside `schoolbilling`; no cross-module foreign keys. Money `NUMERIC(14,2)`. Existing rows are backfilled in the same migration.

## contract (columns added to the 012 table)

| Column | Type | Notes |
| --- | --- | --- |
| cycle | VARCHAR(10) | existing; now `MONTHLY`, `PER_TERM` or `ANNUAL` (012 allowed `MONTHLY` only; widen the check) |
| gst_applicable | BOOLEAN | default false |
| school_gstin | VARCHAR(15) null | required when `gst_applicable` and not unregistered; format and check character validated in the service |
| school_unregistered | BOOLEAN | default false; true only when `gst_applicable` |
| school_state_code | CHAR(2) null | from the GSTIN, or from the School record when unregistered; required when `gst_applicable` |

Checks: `gst_applicable = false` requires `school_gstin`, `school_state_code` null and `school_unregistered` false. `RATE_PENDING` contracts have `cycle = MONTHLY`, GST false. Fee terms are written once with the MoU (the existing 012 write-once rule on signing details is extended to these columns).

## contract_position (column added)

| Column | Type | Notes |
| --- | --- | --- |
| period_fee | NUMERIC(14,2) | fee per billing period; `> 0`; equals `salary` when `cycle = MONTHLY`; `salary` stays the monthly equivalent |

## contract_period (new; Per term only)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| contract_id | UUID FK to `contract` | |
| number | INT | 1 to 12, unique per contract |
| name | VARCHAR(60) | for example "Term 1" |
| starts_on, ends_on | DATE | `ends_on >= starts_on`; exclusion constraint: no overlap within a contract; contiguity and coverage checked in the service |

Annual and Monthly periods are derived and not stored.

## monthly_due (022 table; columns added; remains the due of any period)

| Column | Type | Notes |
| --- | --- | --- |
| contract_id | UUID | the contract the period belongs to (backfilled from the contract in effect for existing dues) |
| period_start, period_end | DATE | backfilled to first and last day of the month |
| period_label | VARCHAR(60) | for example "November 2027", "Term 1", "2027-28" |
| taxable_value | NUMERIC(14,2) | sum of lines; backfilled to the old total |
| tax_rate | NUMERIC(5,2) null | null when no GST |
| tax_total | NUMERIC(14,2) | default 0 |
| total | NUMERIC(14,2) | taxable + tax; backfilled to the old total |
| hls_state_code, school_state_code | CHAR(2) null | snapshot, GST dues only |
| invoice_fy | VARCHAR(7) null, invoice_no | INT null | for GST dues, for example `2027-28` and 1; unique (invoice_fy, invoice_no) |
| invoice_snapshot | JSONB null | HLS legal name, address, GSTIN, SAC, School name and GSTIN, copied at raise time |

Partial unique index replaces (school_id, month): (school_id, contract_id, period_start) where `cancelled_at is null`. The `reference` format `MD-YYYYMM-<seq>` continues for Monthly; other periods use `PD-<YYYYMMDD of period start>-<seq>`. All new columns are write-once (extend the append-only trigger).

## due_tax_line (new)

| Column | Type | Notes |
| --- | --- | --- |
| id | UUID PK | |
| due_id | UUID FK to `monthly_due` | |
| adjustment_id | UUID FK null | set when the line belongs to a credit note instead of the due (one of the two is set) |
| kind | VARCHAR(4) | `CGST`, `SGST`, `IGST` |
| rate | NUMERIC(5,2) | the rate of this line (half for CGST and SGST) |
| amount | NUMERIC(14,2) | `>= 0` |

Insert-only (UPDATE and DELETE refused by trigger); a reversal of a credit note carries its own reversing tax lines.

## due_adjustment (column added)

`taxable_amount NUMERIC(14,2)` (backfilled to `amount`) and `tax_amount NUMERIC(14,2)` default 0; `amount` remains the total used by the balance rules.

## due_line (column added)

`period_fee NUMERIC(14,2)` is not needed: the line already stores salary (now the fee for the period), days, period days and the working. Column `month_days` is renamed in the working text only; existing rows keep their values.

## invoice_counter (new)

| Column | Type | Notes |
| --- | --- | --- |
| fy | VARCHAR(7) PK | `2027-28` |
| last_no | INT | incremented under `FOR UPDATE` in the raise transaction |

## Settings (spec 011 store)

`billing.hls_gstin`, `billing.hls_state_code`, `billing.hls_legal_name`, `billing.hls_address`, `billing.sac_code`, `billing.gst_rate` (default 18.00, 0 to 28). Existing `billing.payment_due_days` and `billing.escalation_days` are not changed.
