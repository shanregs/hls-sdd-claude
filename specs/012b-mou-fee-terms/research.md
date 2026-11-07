# Research: MoU Fee Terms (Billing Basis and GST)

## R1. Billing periods

Decision: Monthly periods are calendar months (as 022). Annual periods are 12-month spans from the contract start, the last cut at the end date. Per term periods are stored rows listed on the MoU (1 to 12, contiguous, inside the contract dates). A due is keyed by (School, contract, period start); the 022 unique index on non-cancelled (school, month) becomes (school, contract, period start).
Rationale: a month is just a one-month period, so one raise path serves all three bases and old dues keep their meaning (their period start is the first of the month).
Alternative rejected: separate tables for term dues; it would duplicate ageing, collections and follow-up.

## R2. Proration over a period

Decision: line = fee x workedDays / periodDays, HALF_UP at the line, where workedDays is the working days of the clipped assignment span (as 022 R1) and periodDays is the sum of `SchoolWorkingDays.forMonth` over the months of the period (clipped to the contract for the last period). A whole-period span gives the fee exactly.
Rationale: the same rule as 022 and payroll (spec 037), so receivable and pay agree.

## R3. Fee and monthly equivalent

Decision: the position keeps its 012 `salary` column as the **monthly equivalent**, and a new `period_fee` column holds the fee per period for non-Monthly contracts (equal to `salary` for Monthly). Monthly equivalent = period_fee / whole months in the period (HALF_UP). For Per term with unequal term lengths, the monthly equivalent is taken from the first period and shown on the form.
Rationale: every existing reader of `salary` (013b margin, 014b) keeps working unchanged.
Alternative rejected: replacing `salary`; breaks the readers and the 012 constraints.

## R4. Tax computation

Decision: taxable = sum of line amounts. Intra-state (School state code = HLS state code): CGST = SGST = round(taxable x rate / 2 / 100) each; otherwise IGST = round(taxable x rate / 100). Total = taxable + tax lines. Rate and both state codes are stored on the due. State code is the first two characters of a validated GSTIN (15 characters, pattern and check character per the published algorithm), or the School record's state for an unregistered School (Q4).
Rationale: simple, deterministic, reproducible by hand. Rounding per tax line matches common invoicing practice; the accountant confirms (Q6).

## R5. Credit notes with tax

Decision: a credit note on a GST due stores a taxable amount, the same rate and split, its own tax lines and total; the existing 022 balance check applies to the total. A credit note reverses by an insert-only reversing row as in 022 R5.

## R6. Gapless invoice numbers

Decision: one counter row per financial year (`invoice_counter(fy, last_no)`), incremented with `SELECT ... FOR UPDATE` inside the raise transaction; the number is stored on the due. A rolled-back raise rolls back the increment, so no gap. A cancelled due keeps its number. Financial year = April to March from `businessToday()`. "Raise all" runs one transaction per School, so numbers are consecutive in commit order.
Rationale: gaplessness is a statutory expectation for tax invoices; a sequence object would leave gaps on rollback.

## R7. Tax invoice view

Decision: a server-rendered print view (HTML with print styles) from stored values only, so a later setting change never alters an old invoice; HLS name, address, GSTIN and SAC are copied onto the due at raise time (snapshot). Amount in words by a tested pure function (Indian system).
Alternative rejected: PDF generation library now; the browser prints to PDF and avoids a new dependency. Revisit if the owner wants stored PDFs.

## R8. Margin and the public interface

Decision: `SchoolReceivables` keeps its signatures and returns the taxable line amount per Teacher per month; for a period line the monthly figure is line / months in period (last month takes the rounding remainder so the months add up to the line). Balances stay on totals. A new `taxOfMonth` gives tax for reports.

## R9. Existing data

Decision: the migration adds columns with defaults (basis MONTHLY, GST false, taxable = total, tax 0, period start = first of month) and backfills in the same migration; an automated check compares count and sum of dues, balances and ageing before and after.

## R10. Payment due days

Decision: read `billing.payment_due_days` from the existing `BillingSettings` for every due. No new setting. Escalation days likewise.
