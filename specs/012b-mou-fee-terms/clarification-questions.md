# Spec 012b clarification questions (OPEN, nothing blocks: the recommended default is used in spec.md)

`/speckit-clarify` was run as a review only; nothing here has been answered. Each question shows the default used in `spec.md`. Questions marked OWNER are commercial or legal and need the owner's answer (GST ones: with the accountant) before the build starts. Reply with a choice per question and the answers will be folded into `spec.md` under Clarifications.

## Q1 (OWNER). Is a term or year billed in advance or in arrears?

Affects FR-010 and User Story 2.

- A (recommended): in advance, due from the first day of the term or year.
- B: in arrears, raised after the period ends.
- C: per contract, chosen on the MoU.

Default used: A.

## Q2 (OWNER). Is the fee the same in every term of a contract?

Affects FR-003 and the MoU form.

- A (recommended): one fee per position, the same every term.
- B: a fee per position per term (for example a higher fee in term 1).

Default used: A. B adds a fee grid to the form.

## Q3 (OWNER, with the accountant). Are the fees on the MoU exclusive or inclusive of GST?

Affects FR-015, FR-016.

- A (recommended): exclusive; GST is added on top of the fee.
- B: inclusive; the tax is carved out of the fee.
- C: chosen per MoU.

Default used: A.

## Q4 (OWNER, with the accountant). How is an unregistered School (no GSTIN) taxed, and which state is used?

Affects FR-013, FR-015.

- A (recommended): GST still applies at the normal rate; the state comes from the School record (005); the GSTIN field is left empty.
- B: an unregistered School is always treated as in the same state as HLS (CGST and SGST).
- C: such Schools are not allowed on a GST contract.

Default used: A.

## Q5 (OWNER, with the accountant). Does HLS need a legal tax invoice from this system?

Affects User Story 5, FR-020. Spec 022 is an internal ledger and sends nothing.

- A (recommended): a printable tax invoice view with a gapless number per financial year; the accountant confirms the layout; nothing is sent by the system.
- B: no invoice document; the accountant issues invoices elsewhere and this system holds only tax lines.
- C: A plus e-invoice (IRN) data. A separate, later spec.

Default used: A. If B, User Story 5 and FR-020 are dropped.

## Q6 (OWNER, with the accountant). What are the HLS invoice details and the invoice number format?

Affects FR-014, FR-020. Needed as values, not a decision: HLS legal name, address, GSTIN, state, service accounting code (SAC), default rate, and the number format.

- Default used: rate 18%, number `INV/2027-28/00001` (financial year and a five-digit sequence), SAC left as a setting to be filled by an Admin.

## Q7 (OWNER). May a GST contract also be Monthly, and may Monthly Schools be moved to GST?

Affects FR-001, FR-024.

- A (recommended): GST and billing basis are independent; any combination is allowed. A School changes basis or GST status only through a new MoU (012); existing contracts are never converted in place.
- B: GST only with Per term or Annual.

Default used: A.

## Q8. Does a term or annual due need approval before it counts?

Affects User Story 2. 022 dues need none.

- A (recommended): no approval; Admin or Director raises it, as in 022.
- B: a Director approves dues above an amount.

Default used: A.

## Q9. Should the 023 marketing proposal carry the billing basis and GST flag so the MoU form is pre-filled (amendment A3)?

- A (recommended): yes, as a later small change to 023 after 012b is built; the MoU form works without it.
- B: not at all.

Default used: A; no task in this spec's first build.
