# Quickstart: MoU Fee Terms (Billing Basis and GST)

Prerequisites: JDK 25 (`JAVA_HOME`), Docker for Testcontainers, dev profile for demo data (seeds one Monthly contract as before, one Per term GST contract and one Annual contract without GST, and HLS tax settings).

1. Sign in as Admin: System Settings, fill the HLS tax details (GSTIN, state, SAC, legal name, address, rate 18%).
2. OPERATIONS > School Contracts > record a MoU: choose Per term, add two terms, enter a fee per position, tick GST applies and enter the School GSTIN. The screen shows the monthly equivalent. Try overlapping terms and a bad GSTIN: refused with the reason. Save; no edit action exists.
3. OPERATIONS > School Billing > Raise: pick Term 1. A Teacher who joined mid-term shows the working (days over period days). Raise again: refused, existing due shown. The due date equals the raised date plus the payment due days setting from 022.
4. Open the due: taxable value, CGST and SGST (or IGST for a School in another state), total. Change the rate setting; the due is unchanged.
5. Add a credit note of a taxable amount: tax lines and total shown, balance falls; one above the balance is refused; reverse it.
6. Record a collection for the total; the due is paid. Ageing and the overdue job behave as in 022.
7. Open the tax invoice: number `INV/<fy>/<seq>`, both GSTINs, tax lines, amount in words. Cancel a due: its number stays and is marked cancelled; the next number is the next in sequence.
8. Tax summary for the month and CSV export: totals equal the tax lines.
9. Sign in as the Zone Manager: sees own Schools' terms, dues and invoices; cannot record terms or raise; another Zone's id returns 404.
10. Margin: ask `SchoolReceivables` (test) for a Teacher's taxable amount for each month of the term: the months add up to the line, and no tax is included. Monthly non-GST contracts give the same figures as before the upgrade.
11. Backend: `mvn -f backend/pom.xml test -Dtest='com.hls.schoolbilling.*FeeTerms*,com.hls.schoolbilling.*Period*,com.hls.schoolbilling.*Tax*,com.hls.schoolbilling.*Invoice*'`. Frontend: `npm test -- schoolbilling` in `frontend`.
