# Quickstart: MoU Documents and Approval

How to prove the feature end to end once built. Endpoints: [contracts/mou-approval-api.md](contracts/mou-approval-api.md);
interfaces: [contracts/module-interfaces.md](contracts/module-interfaces.md); tables: [data-model.md](data-model.md).

## Prerequisites

- Backend running with the demo seeder (`docker-compose.yml` or `scripts/run-all.ps1`), migration V100 applied. A writable
  `hls.files.directory`.
- Demo users: Admin, Director, Manager A (Zone of Schools A1, A2), Manager B (School B1), a Teacher.
- Demo data: A1 with a MoU recorded before this spec (shows "approved (before approval step)"), A2 with a new MoU recorded now
  (DRAFT), a School with a "MoU pending" placeholder, a small PDF, a JPG, a PNG, a `.docx` and a 12 MB file.
- Frontend running; sign in as each role in turn.

## 1. Attach the document (US1)

1. As Admin, OPERATIONS then School Contracts, open A2, upload the PDF. Expect the file with name, size, who and when, and
   "document attached".
2. Upload the PNG as a second file: it becomes version 2 and current; version 1 stays listed.
3. Try the `.docx` and the 12 MB file: each refused with a clear reason, nothing stored. Try a program renamed `.pdf`: refused.
4. As Manager A, open A2: download works, there is no Upload or Remove. Calling upload directly returns 403.
5. As Manager B, request A2's document id: 404, same as a missing id.
6. As Admin, remove version 1 with a reason: it shows as removed with the reason; the bytes are not served any more.

## 2. Submit, reject, resubmit, approve (US2)

1. As Admin, submit A2 with its document: status "pending approval"; the Director and Manager A get a notification; Admin does not.
2. As Admin, try Approve: the button is absent and the API returns 403.
3. As Manager A, reject without a reason: refused. With a reason: "rejected"; Admin (submitter) and the other Admins are notified.
4. As Admin, upload a corrected document, resubmit: back to "pending approval", round 2, the rejection stays in the history.
5. As Manager A, change the rate on the pending MoU through Amend terms: the new rate shows, Change History has old and new
   values, the history has "terms amended". Try changing the teacher count: refused.
6. As the Director, approve. A stale screen (opened before step 5) gets 409 and reloads. Status "approved" with approver and time.
7. As Admin, upload another document to the approved MoU: it goes back to "pending approval" and needs approval again.
8. Submit a MoU that has no document (another School): it is accepted; after approval it shows "approved without document".
9. As the Director, record a MoU for a School, submit it and approve it yourself: allowed, history shows the same person for both
   actions, Change History shows `selfDecided`.
10. Two browsers as Director and Manager A decide the same pending MoU: one succeeds, the other gets 409 naming the decider.

## 3. Lists (US3)

1. As Admin, the School Contracts list shows approval and document columns; filter by Pending, Rejected, Approved, document None.
2. As Manager A, open MoU Approvals: only A1/A2 MoUs that are pending; as Manager B, none of them.
3. Change the Zone Manager of A2 while a MoU is pending: the new Manager sees it, the old one no longer does.
4. The "MoU pending" placeholder cannot take a document or be submitted; a cancelled MoU cannot be submitted, its documents
   can still be downloaded.

## 4. Public interface (US4)

1. Call `MouApprovals.stateOn` (the integration test, or the dev `jshell`/test harness) for the School of each case: approved
   gives `APPROVED` with the date and `documentAttached`, pending/rejected/draft give their name and never approved, the old MoU
   gives `APPROVED_BEFORE_APPROVAL_STEP`, no MoU gives `NO_MOU`, the placeholder gives `NOT_APPLICABLE`.

## 5. Nothing else changes (FR-013, SC-006)

1. For a test month, map Teachers and mark attendance for A1 (legacy), A2 in DRAFT, PENDING, REJECTED and APPROVED: the mapping
   and attendance results are identical in every state. The 012 flows (record MoU, end, cancel) behave as before.

## Automated

- Backend: `./mvnw -pl backend test -Dtest='Mou*,ContractApiTest,ContractListApiTest,SchoolContractsPublicApiTest,SchoolBillingModuleRulesTest'`,
  plus `ModulithVerificationTest`, the permission eligibility and seed tests, `AuditVisibility` tests, and the V100 migration
  test (backfill, trigger).
- Frontend: `npm test -- schoolbilling` in `frontend/`.
