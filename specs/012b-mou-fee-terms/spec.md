# Feature Specification: MoU Fee Terms (Billing Basis and GST)

**Feature Branch**: `spec/012b-mou-fee-terms`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "012b-mou-fee-terms: An amendment on top of 012 (school contracts) and 022 (school billing). Version 1 bills monthly per Teacher with no GST (decision D10). This amendment lets a contract (MoU) say how the School is billed: monthly (as today), per term, or annual, and whether GST applies, with the School's GST number and tax lines on the dues. Payment due days already live in 022 as a setting; this spec only refers to it. Sources: docs/spec-roadmap.md row 012b and decision D10, specs/012-school-contracts, specs/022-school-billing, docs/spec-inputs/022-school-billing.md."

## Clarifications

### Session 2026-11-07

Owner questions are listed in `clarification-questions.md`. None blocks this spec: every question has a recommended default, used below and recorded under Assumptions. Questions marked OWNER are commercial or legal and should be confirmed with the owner (and, for GST, the company's accountant) before building.

## Relationship to Other Specs (amendments)

This spec amends, and does not replace, three merged specs. Each change is listed so the earlier specs stay readable.

- **A1, Spec 012 (School Contracts)**: the contract gains **fee terms** (billing basis, billing periods, fee per position, GST details). The contract's `cycle` field, "MONTHLY only" in 012, accepts two more values. Signing details and fee terms are written once; a change is a new MoU, as in 012.
- **A2, Spec 022 (School Billing)**: a monthly due becomes a **due for a billing period** (a month, a term or a year). The 022 assumption "Version 1 is monthly per Teacher, no GST (D10)" and FR-022 "no GST or tax lines exist" no longer hold for contracts that opt in. Payment due days and escalation days stay 022 settings (`billing.payment_due_days`, `billing.escalation_days`); this spec adds no due-days setting and every kind of due uses the same one.
- **A3, Spec 023 (School Marketing and MoU)**: the proposal's fee basis and GST details may be pre-filled into the MoU form (optional, can follow later).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Record How the School Is Billed on the MoU (Priority: P1)

When an Admin or Director records a MoU, they also choose the **billing basis**: Monthly (today's behaviour, and the default), Per term, or Annual. For Per term they enter the terms (a name and a start and end date for each, for example "Term 1, 01/04/2027 to 30/09/2027"); for Annual the periods are successive 12-month spans from the contract start. For each Teacher position they enter the **fee per billing period** (one amount for all positions on a "same for all" contract). The screen shows the monthly equivalent of each fee so it can be checked against what the School agreed.

**Why this priority**: Nothing else in this spec can be used until the contract carries the terms.

**Independent Test**: Record a MoU for 3 Teachers, Per term, two terms, a fee of 60,000 per Teacher per term; the contract shows both terms, three positions at 60,000 and a monthly equivalent. Record another as Monthly; it behaves exactly as before.

**Acceptance Scenarios**:

1. **Given** the MoU form, **When** the basis is left on Monthly, **Then** the form, the contract and billing are exactly those of specs 012 and 022.
2. **Given** basis Per term, **When** the terms overlap, leave a gap inside the contract dates, or fall outside them, **Then** the form is refused with the reason named.
3. **Given** basis Annual and a contract starting 01/04/2027 and ending 31/03/2029, **When** it is saved, **Then** two annual periods are shown (01/04/2027 to 31/03/2028 and 01/04/2028 to 31/03/2029).
4. **Given** a saved contract, **When** anyone looks for a way to change its basis, periods or fees, **Then** none exists; a change is a new MoU (012 FR-003).
5. **Given** a Zone Manager, **When** they open a contract of their School, **Then** they see the fee terms but cannot record or change them.

---

### User Story 2 - Raise a Due for a Term or a Year (Priority: P1)

An Admin or Director raises the due for a School and a billing period from the same screen as the monthly due (022). Each line is one Teacher mapped to a position in that period, at the position's fee for the period. A Teacher assigned for the whole period counts in full; one who joined, moved or exited inside it counts for the part of the period at the School, by working days (the 022 rule, with the period's working days as the denominator, built from the School's month working days of spec 037). Absences never reduce it (D6). A period is billed **in advance, from its first day** (assumption); it can be raised once the period has started. The due date is the raised date plus the payment due days setting of 022.

**Why this priority**: It is the point of the amendment.

**Independent Test**: With the Per term contract above and three Teachers (one joining 40 days into a 130-working-day term), raise Term 1; the lines, the pro-rated amount and the total match a hand calculation; raising it again is refused; the due date follows the 022 setting.

**Acceptance Scenarios**:

1. **Given** a Per term contract, **When** Term 1 is raised, **Then** one due exists for that School and period with a line per mapped Teacher at the position fee.
2. **Given** a Teacher who joined partway through the term, **When** it is raised, **Then** the line is the fee times (the Teacher's working days at the School in the term) over (working days of the term), and shows the working.
3. **Given** a School and period that already has a non-cancelled due, **When** a second is raised, **Then** it is refused and the existing one shown.
4. **Given** a Teacher who exits during a term already billed in advance, **When** the exit is recorded, **Then** the due is not changed; any agreed refund is a credit note (022 User Story 4).
5. **Given** a Monthly contract, **When** dues are raised, **Then** nothing differs from 022.
6. **Given** "raise all Schools" for a date, **When** it runs, **Then** every School whose current contract has a started, not yet billed period is raised, whatever its basis; others are skipped with a reason.
7. **Given** a period containing a month whose working days are not yet set, **When** it is raised, **Then** it is refused naming the School and month (as in 022).

---

### User Story 3 - GST Number and Tax Lines (Priority: P1)

A MoU can say **GST applies**. It then records the School's **GST number (GSTIN)**; the School's state is read from its first two digits. HLS's own GST number, state, service code and default rate are settings (Admin). When a due is raised on such a contract, it shows a **taxable value** (the sum of the lines), the **tax lines** and a **total**: for a School in the same state as HLS, CGST and SGST each at half the rate; for another state, IGST at the full rate. Fees are **exclusive of tax** (tax is added on top) unless the owner decides otherwise. A contract with GST not applicable bills exactly as today. Tax is worked out and stored on the due when it is raised and never changes afterwards.

**Why this priority**: The roadmap names GST number and tax lines as the second half of the amendment; invoices to registered Schools may need them.

**Independent Test**: A GST contract for a School in the same state, rate 18%, taxable value 1,80,000: the due shows CGST 16,200, SGST 16,200 and total 2,12,400. The same fee for a School in another state shows IGST 32,400 and the same total.

**Acceptance Scenarios**:

1. **Given** GST applies and the GSTIN is not valid, **When** the MoU is saved, **Then** it is refused with the reason.
2. **Given** GST applies and the HLS tax settings (GSTIN, state, service code, rate) are incomplete, **When** a due is raised, **Then** it is refused, naming the missing setting.
3. **Given** a School whose state equals HLS's state, **When** a due is raised, **Then** the tax lines are CGST and SGST at half the rate each; for another state, one IGST line.
4. **Given** tax that does not divide evenly, **When** raised, **Then** each tax line is rounded to 2 decimals half up and the total is the taxable value plus the lines as shown.
5. **Given** a raised due, **When** the rate setting is later changed, **Then** the due is unchanged.
6. **Given** a contract with GST not applicable, **When** a due is raised, **Then** there are no tax lines and the total equals the taxable value.
7. **Given** a contract with a GSTIN, **When** it is shown, **Then** the GSTIN is visible to anyone who may see the contract; it is a business identifier, not personal data.

---

### User Story 4 - Credit Notes and Collections Carry the Tax (Priority: P2)

An adjustment (credit note) on a GST due is entered as a taxable amount with a reason; the tax on it is worked out with the same split and rate as the due, and it shows taxable value, tax lines and total. It cannot take the due's balance below zero. A collection is allocated against the **total** (taxable value plus tax) of dues, exactly as in 022. Balances, ageing and advance credit are on totals.

**Why this priority**: Without it a GST due could not be corrected or paid correctly, but the first dues can be raised without it.

**Independent Test**: A due of 2,12,400; a credit note of 10,000 taxable gives a total of 11,800 and the balance falls to 2,00,600; a collection of 2,00,600 pays it.

**Acceptance Scenarios**:

1. **Given** a GST due, **When** a credit note of 10,000 taxable at 18% is added, **Then** it shows tax lines and a total of 11,800, and the balance falls by 11,800.
2. **Given** a credit note whose total exceeds the balance, **When** it is saved, **Then** it is refused.
3. **Given** a GST due, **When** it is cancelled (022 rules), **Then** its tax lines are cancelled with it and it contributes nothing to totals.
4. **Given** a credit note, **When** it is reversed, **Then** its tax lines are reversed with it.

---

### User Story 5 - Tax Invoice View for a GST Due (Priority: P2)

For a GST due the system offers a **tax invoice** that can be viewed and printed: invoice number, date, HLS and School names, both GST numbers, place of supply, service description and code, period, taxable value, tax lines, total in figures and words, and due date. Invoice numbers run in one unbroken sequence per financial year (April to March) for GST dues. Nothing is sent to the School by the system (022 remains an internal ledger); the person prints or shares the view themselves. Dues on contracts where GST does not apply have no tax invoice.

**Why this priority**: A tax invoice is a legal document for a registered supplier. It is P2 because the ledger and tax lines work without it; the owner must confirm the format with the accountant (Q5, Q6).

**Independent Test**: Raise two GST dues; their numbers are consecutive; cancel one; its number stays used and shown as cancelled; the printout contains every field listed above.

**Acceptance Scenarios**:

1. **Given** the first GST due in financial year 2027-28, **When** it is raised, **Then** it gets number 1 of that year and the next gets number 2, however many Schools are raised at once.
2. **Given** a cancelled GST due, **When** the next is raised, **Then** the cancelled number is not reused and the cancelled invoice stays viewable, marked cancelled.
3. **Given** the first due raised on or after 1 April, **When** raised, **Then** the sequence restarts at 1 for the new financial year.
4. **Given** a Zone Manager, **When** they open a tax invoice of their own School, **Then** they can view and print it; for another Zone's School it returns not found.

---

### User Story 6 - Margin and Reports Use the Taxable Value (Priority: P2)

Tax collected is not HLS income. The figures that 013b, 014 and 028 read for margin (School fee minus Teacher pay) MUST be the **taxable** amount per Teacher per month, never the tax. Balances and ageing stay on totals because that is what the School owes. A **tax summary** (Admin, Director; CSV) shows, for a month, taxable value and CGST, SGST and IGST by School, to support return filing; filing itself is outside this system.

**Why this priority**: It stops tax inflating margin.

**Independent Test**: A GST due totalling 2,12,400 over a taxable 1,80,000: the public interface returns taxable amounts per Teacher summing to 1,80,000 over the period's months and a School balance based on 2,12,400; the tax summary shows 32,400 of tax.

**Acceptance Scenarios**:

1. **Given** a GST due, **When** the margin interface is asked for a Teacher's amount in a month, **Then** it returns the taxable line amount (for a period, spread evenly over its months).
2. **Given** a term or annual due, **When** margin is asked for a month inside the period, **Then** the amount is the monthly equivalent, so one month does not spike.
3. **Given** a month, **When** the tax summary is exported, **Then** totals equal the tax lines of dues raised in that month less those of credit notes added in it, in the caller's scope.

---

### User Story 7 - Existing Contracts and Dues Stay As They Are (Priority: P1)

Every contract and due that exists when this spec ships is Monthly, GST not applicable, and is neither changed nor re-priced. A School moving to a per term basis or GST gets a new MoU (012), which ends the current one the day before.

**Why this priority**: Money already raised must not move.

**Independent Test**: Count and sum the dues before and after the upgrade; they are identical, and a due raised afterwards on an old contract is what 022 would have produced.

**Acceptance Scenarios**:

1. **Given** the upgrade, **When** it completes, **Then** every existing contract reads Monthly and GST not applicable and every existing due has no tax lines and no invoice.
2. **Given** a School on a Monthly contract replaced by a Per term MoU from 01/10/2027, **When** dues are raised, **Then** the old contract's monthly dues stop at 30/09/2027 and the new contract's terms apply from its start date.

---

### Edge Cases

- A new MoU starts in the middle of a term already billed: the old contract's due stands; the new contract's first period starts at its start date and is pro-rated from it. Any refund of the overlap is a credit note, never automatic.
- A Teacher moves between two Schools inside a term: each School's due clips its own span (022 rule), by working days in the period.
- A term spans months with different working-day figures: the period's working days are the sum of the School's month working days (037).
- A position vacant for part of a period adds nothing for those days; a replacement in the same position continues.
- A contract ends before its last term finishes: the last period is cut at the end date and pro-rated like a leaver.
- GST applies but the GSTIN's state code is not a known state: refused at save.
- A School with no GSTIN (unregistered): the contract is saved with GST applicable and "unregistered"; the State used is the one on the School record (005); see Q4.
- The GST rate setting is 0%: the due is raised with zero tax lines and is still a tax invoice.
- Two users raise the same period at once: only one succeeds (022 rule); invoice numbers stay gapless because they are issued in the same transaction as the due.
- A tax invoice is cancelled after being shared with the School: the cancellation is a record only; HLS tells the School itself.
- The financial year is taken from the raised date in the business time zone, so a due raised just after midnight on 1 April belongs to the new year.

## Requirements *(mandatory)*

### Functional Requirements

**Fee terms on the MoU (amends 012)**

- **FR-001**: An Admin or Director MUST be able to record, with the MoU, a billing basis of Monthly (default), Per term or Annual. Monthly MUST behave exactly as specs 012 and 022 define.
- **FR-002**: For Per term the MoU MUST list one to twelve billing periods, each with a name, start and end date, in order, not overlapping, with no gap, together covering the contract from its start date to its end date (for an open-ended contract, to the end of the last listed period). For Annual the periods MUST be the successive 12-month spans from the contract start; the last is cut at the end date.
- **FR-003**: Each contract position MUST carry a fee per billing period (positive, two decimals; one amount for all positions on a "same for all" contract). The fee is the same in every period of the contract (Q2).
- **FR-004**: The system MUST show and expose each position's **monthly equivalent** (fee per period divided by the whole months in the period, rounded half up to 2 decimals), so margin and the position's salary figure remain monthly; for Monthly contracts it is the salary of 012.
- **FR-005**: Fee terms MUST be written once with the contract and MUST NOT be editable; a change is a new MoU that ends the current one (012 FR-003).
- **FR-006**: Only Admin and Director MAY record fee terms (the `SCHOOL_CONTRACTS` create permission); a Zone Manager sees them within scope and never changes them.

**Dues per billing period (amends 022)**

- **FR-007**: The system MUST let an Admin or Director raise one due per School per billing period (a month for Monthly, a term for Per term, a year for Annual) from the School Billing screen.
- **FR-008**: Each line MUST be one Teacher mapped to a position in the period at that position's fee; a Teacher assigned for the whole period counts in full; otherwise the line is the fee times (the Teacher's working days at the School in the period) over (the period's working days), the latter being the sum of the School's month working days from spec 037 (022 research R1 extended from a month to a period).
- **FR-009**: Absences, leave and substitute cover MUST NOT change a due (D6); a Teacher leaving during an advance-billed period MUST NOT change the raised due; any refund is a credit note.
- **FR-010**: A period MUST be raisable from its first day. At most one non-cancelled due per School and period MUST exist. A due, its lines and its tax lines are immutable after raising; cancellation, adjustments, collections and reversals work as in 022.
- **FR-011**: The due date of every due MUST be the raised date plus the **payment due days setting of spec 022**; this spec adds no due-days setting. Overdue follow-up, escalation and ageing (022) apply unchanged to term and annual dues.
- **FR-012**: "Raise all" MUST raise, for a chosen date, each School whose current contract has a started, not yet billed period, whatever its basis, and report skipped Schools with reasons (as 022).

**GST (amends 022 FR-022)**

- **FR-013**: A MoU MUST record whether GST applies. When it does, it MUST record the School's GSTIN (15 characters, valid format and check character) or mark the School unregistered, and MUST derive the School's state from the GSTIN's state code (or from the School record when unregistered).
- **FR-014**: HLS's GSTIN, state, service accounting code, legal name and address for invoices, and the default GST rate MUST be settings changed by an Admin and audited. A due on a GST contract MUST be refused while any is missing.
- **FR-015**: When a due is raised on a GST contract the system MUST compute and store taxable value (sum of lines), tax lines and total. The tax lines are CGST and SGST (each half the rate) when the School's state equals HLS's state, otherwise IGST (the whole rate). The rate applied is the default rate at the raise date, stored on the due; each tax line is rounded half up to 2 decimals.
- **FR-016**: Fees MUST be treated as exclusive of tax: tax is added to the taxable value (Q3).
- **FR-017**: For a contract where GST does not apply, a due MUST have no tax lines, its total equals its taxable value, and no tax invoice exists.
- **FR-018**: An adjustment (credit note) on a GST due MUST be entered as a taxable amount; its tax lines are computed with the same split and rate as the due; its total MUST NOT exceed the due's balance. Reversing it reverses its tax lines.
- **FR-019**: Collections, allocations, advance credit, balances and ageing (022) MUST work on due totals (taxable value plus tax).

**Tax invoice and reporting**

- **FR-020**: For a GST due the system MUST provide a viewable, printable tax invoice with the fields in User Story 5, numbered from an unbroken sequence per financial year (1 April to 31 March) allocated in the same transaction as the due. A cancelled invoice keeps its number and is marked cancelled; no number is reused. The system MUST NOT send it to a School.
- **FR-021**: The public receivables interface of 022 MUST return, for margin, the **taxable** amount per Teacher per month, spreading a term or annual line evenly over the months of its period (the monthly equivalent), MUST return balances on totals, and MUST return the tax of a month for reports.
- **FR-022**: Admin and Director MUST be able to view and export (CSV) a monthly **tax summary**: taxable value, CGST, SGST and IGST by School, with credit notes deducted, in the caller's scope.

**Cross-cutting**

- **FR-023**: Every fee-terms recording, HLS tax setting change, due raise, invoice number allocation, cancellation and credit note MUST be written to the audit trail (spec 003) with actor, time and before and after values.
- **FR-024**: The upgrade MUST set every existing contract to Monthly with GST not applicable, MUST NOT change any existing due, and MUST leave 022 results identical for them.
- **FR-025**: Every list, search, detail, invoice view and export MUST be filtered by the caller's scope (Zone Manager: own Schools only); an out-of-scope id returns not found.
- **FR-026**: Money MUST be exact to two decimals and shown in rupees with Indian digit grouping; dates as DD/MM/YYYY; screens MUST meet WCAG 2.2 AA, have loading, empty and error states and work at phone width. Amounts in words on the invoice MUST use the Indian system (lakh, crore).
- **FR-027**: Menu items and actions MUST be offered only when the access model grants them, and every endpoint MUST enforce the same permission and scope server-side.

### Key Entities

- **Fee terms (on the contract)**: billing basis, GST applicable flag, School GSTIN or unregistered flag, School state.
- **Billing period**: a named span of a contract (a month for Monthly, a term, or a year) used for dues; derived for Monthly and Annual, listed for Per term.
- **Position fee**: the amount for one position for one billing period, with its monthly equivalent.
- **Due (extended 022)**: now for a billing period; adds taxable value, tax rate, tax lines, total and, for GST dues, an invoice number.
- **Tax line**: type (CGST, SGST, IGST), rate, amount; belongs to a due or a credit note; immutable.
- **Invoice number**: financial year and sequence number of a GST due; gapless.
- **HLS tax details**: settings (GSTIN, state, service code, legal name, address, default rate).

## Role & Permission Impact *(mandatory — Constitution Principles II–IV)*

No new permission module and no new menu item.

| Role     | Menu (section → item) | Default actions | Data scope |
| -------- | --------------------- | --------------- | ---------- |
| Admin    | OPERATIONS → School Contracts, School Billing | Record fee terms (`SCHOOL_CONTRACTS` create); raise, cancel, credit note, view and print invoice, tax summary (`SCHOOL_BILLING`); edit HLS tax settings (System Settings) | Org-wide |
| Director | same | as Admin for contracts and billing; no settings edit by default | Org-wide |
| Manager (Zone Manager) | same | View fee terms, dues and tax invoices of own Schools; export own-scope CSV; record collections (022); cannot record terms or raise | Assigned (own Schools) |
| Teacher  | none | none | None |
| System   | none | none | None (business data) |

**New permission keys**: none. The tax summary uses `SCHOOL_BILLING.export`; HLS tax settings use the existing System Settings permission. **Constitution**: no principle change and no new matrix row, so no version bump is required (see plan.md Constitution Check). If the owner wants a separate right for the tax summary, a key is added in planning and the matrix gains a row then, as a PATCH.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: An Admin records a Per term MoU for 4 Teachers with two terms and fees in under 4 minutes.
- **SC-002**: Every term, annual and part-period line amount matches a hand calculation in 100% of test cases, and dues on old contracts are identical to what 022 produced.
- **SC-003**: For every GST due, taxable value plus tax lines equals the total, and the split (CGST and SGST, or IGST) follows the two states in 100% of test cases.
- **SC-004**: GST invoice numbers are gapless and unique per financial year, including when 50 Schools are raised at once and when a due is cancelled.
- **SC-005**: Margin figures never include tax: for every GST due, the margin interface's taxable amounts over the months of the period add up to its taxable value.
- **SC-006**: No fee term, due, tax line or invoice number can be edited or deleted through any screen or API; each correction is a separate entry with actor, time and reason.
- **SC-007**: A Zone Manager never sees a School outside their area in any due, invoice, tax summary or export (verified per endpoint).
- **SC-008**: After the upgrade, the count and sum of existing dues, balances and ageing are identical to before.
- **SC-009**: Raising dues for 500 Schools of mixed basis completes without error and the overview loads in under 3 seconds.

## Assumptions

- Version 1 (D10) is monthly per Teacher without GST; this amendment was deferred and is built only because the business now needs it. Monthly without GST stays the default and a complete, supported mode.
- Billing is **in advance**: a term or year is due from its first day. Billing in arrears is not supported (Q1).
- The fee per position is the same in every period of a contract (Q2). Fees are exclusive of tax (Q3). The default GST rate is 18% as a setting; the rate in force at raise time is stored on the due and a later change affects only later dues.
- The School's state comes from its GSTIN; an unregistered School uses the state on its School record (Q4). Reverse charge, composition scheme, exports, tax deducted at source and input credit are out of scope.
- The tax invoice is a printable view; the system does not send it, produce e-invoice (IRN) data or file returns. Whether this meets the owner's legal needs is Q5 and Q6, to be confirmed with the accountant.
- Payment due days and escalation days are the 022 settings; they are referenced, not duplicated.
- A mid-contract change of fee or rate is a new MoU; refunds and overlaps are credit notes; nothing is refunded automatically.
- Margin uses the monthly equivalent so a term or annual fee does not distort one month.
- Out of scope: mobile screens, payment gateways, bank reconciliation, other currencies, Teacher pay and payslips (013), substitute payout (017b), automatic emailing of invoices, the signed MoU document (031).
- Dependencies: 012 (contract, positions, assignments), 022 (dues, credit notes, collections, settings, public interface), 037 and 038 (School month working days), 011 (system settings), 005 (Schools, states, scope), 003 (audit), 013b and 014b (consumers of the margin figures), 023 (optional pre-fill, A3).
