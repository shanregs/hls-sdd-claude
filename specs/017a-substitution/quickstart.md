# Quickstart: validating Substitution (spec 017a)

Prerequisites: dev profile with demo seed, PostgreSQL (Testcontainers or compose).

1. As a Teacher mark yourself absent today. As the School's Manager, open the bell: "Substitute needed". Follow the link to
   OPERATIONS → Substitutions: one Open requirement.
2. Open it: candidates list; an unplaced active Teacher is Available, a placed one is not (reason shown). Assign the
   available one. Status Assigned; sign in as that Teacher: My Substitutions shows the day; the bell shows the assignment.
3. Record School informed (contact, method). Reassign: the informed record clears; the first substitute is notified.
4. Release, then "I will cover": status Covered by Manager, no credit.
5. Correct the absence to Present: the requirement is Cancelled, the substitute is told.
6. Approve leave for three days: three requirements. Revoke: all cancelled.
7. Attendance: record Principal confirmation on a day; it shows in the day's detail and in Change History.
8. Check as Admin / Director (all), other Zone Manager (not found), System (403), Teacher (403 on /substitutions).

Automated: `mvn test -Dtest='Substitution*,PrincipalConfirmation*,AbsenceEvent*,LossOfPay*'` and `npx vitest run substitution`.
