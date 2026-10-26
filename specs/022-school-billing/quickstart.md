# Quickstart: School Billing and Receivables

Prerequisites: JDK 25 (`JAVA_HOME`), Docker for Testcontainers, dev profile for demo data (seeds a School with a contract and Teachers, one overdue due and one collection).

1. Sign in as Admin: OPERATIONS > School Billing > Raise dues for the current month. Check the lines of a School with a Teacher who joined mid-month: the line shows days, month days and the working; an absent Teacher's line is unchanged. The "not billed" list shows a Teacher with no position and a "MoU pending" School.
2. Raise the same School and month again: refused, existing due shown. Cancel a due with a reason, raise a corrected one; both are listed, totals count one.
3. Record a collection for a School with two open dues: leave allocation empty (oldest first); statuses become Paid and Part paid. Record one larger than owed: the excess shows as advance credit. Record a duplicate: a warning, confirm to continue. Reverse a collection: balances return.
4. Add an adjustment with a reason; the balance falls; one above the balance is refused; reverse it.
5. Open Ageing: buckets match the seeded overdue due. Sign in as a Zone Manager: only own Schools appear in overview, ageing and the CSV export; another Zone's School id returns 404.
6. Overdue: run the overdue job from a test (as in 025, no dev endpoint). A follow-up task appears in the Zone Manager's My Tasks; running it again adds none; pay the due and the task completes. A School with no Zone Manager notifies the Director and Admin instead. After the escalation days, 025's deadline job notifies the Director.
7. Ask `SchoolReceivables` (test) for a Teacher's billed amount and a School's balance: they equal the screens.
8. Backend: `mvn -f backend/pom.xml test -Dtest='com.hls.schoolbilling.*Receivable*,com.hls.schoolbilling.*Due*,com.hls.schoolbilling.*Collection*,com.hls.notification.Receivables*'`. Frontend: `npm test -- schoolbilling` in `frontend`.
