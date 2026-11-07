# Contract: Fee Terms, Period Dues, Tax Invoice (spec 012b)

All endpoints are server-enforced for grant and scope; an out-of-scope id returns 404. Amounts are decimal strings with two places. Existing 012 and 022 endpoints keep their paths; new fields are optional and default to the old behaviour.

## Contracts (012 endpoints, extended)

`POST /api/school-contracts` (create the MoU, `SCHOOL_CONTRACTS` create) accepts additionally:

```json
{
  "billingBasis": "PER_TERM",
  "periods": [{"name": "Term 1", "startsOn": "2027-04-01", "endsOn": "2027-09-30"}],
  "positionFees": [{"number": 1, "periodFee": "60000.00"}],
  "gst": {"applicable": true, "gstin": "29ABCDE1234F1Z5", "unregistered": false}
}
```

Rules: `billingBasis` defaults to `MONTHLY`; `periods` only for `PER_TERM`; `positionFees` required for non-Monthly (one entry for "same for all"); 400 with the reason for overlapping or non-covering periods or an invalid GSTIN. `GET /api/school-contracts/{id}` returns `billingBasis`, `periods` (derived for Annual), `positionFees` with `monthlyEquivalent`, and `gst`.

## Dues (022 endpoints, extended)

- `GET /api/school-billing/periods?date=YYYY-MM-DD&basis=` : billable periods per School for the date, with state (not started, raisable, raised) and the reason when not raisable.
- `POST /api/school-billing/dues` body `{schoolId, contractId, periodStart}` : raise one due. 409 with the existing due when one exists. 422 when HLS tax settings are missing (names the setting) or a month in the period has no working days set.
- `POST /api/school-billing/dues/raise` : bulk, body `{date}`; response lists raised and skipped Schools with reasons (as 022).
- Due detail adds `periodLabel`, `periodStart`, `periodEnd`, `taxableValue`, `taxRate`, `taxLines[]` (`kind`, `rate`, `amount`), `total`, `invoiceNo` (string such as `INV/2027-28/00001`) and keeps `lines[]` with the working.
- `POST /api/school-billing/dues/{id}/adjustments` body `{taxableAmount, reason}`; response carries the tax lines and total; 422 when the total exceeds the balance.

## Tax invoice

- `GET /api/school-billing/dues/{id}/tax-invoice` : JSON of the invoice (snapshot values, amount in words, status ACTIVE or CANCELLED). 404 for a due without GST. `SCHOOL_BILLING` view and scope.
- The web app renders it as a print view; nothing is sent.

## Tax summary

- `GET /api/school-billing/tax-summary?month=YYYY-MM` and `.csv` : rows by School with `taxableValue`, `cgst`, `sgst`, `igst`, `creditNoteTaxable`, `creditNoteTax`, and totals. `SCHOOL_BILLING` export, scope applied; CSV cells beginning with `=`, `+`, `-` or `@` are escaped.

## HLS tax settings

Read and written through the existing System Settings endpoints: `billing.hls_gstin`, `billing.hls_state_code`, `billing.hls_legal_name`, `billing.hls_address`, `billing.sac_code`, `billing.gst_rate`. Changes are audited. `billing.payment_due_days` is read, not changed, by this spec.

## Public interface (`schoolbilling.api.SchoolReceivables`, extended)

- `taxableAmount(teacherId, month)` : taxable monthly figure (period lines spread over the months of the period).
- `taxOfMonth(schoolId or all, month)` : tax billed less credit-note tax.
- Existing balance and collected methods return figures on totals.
