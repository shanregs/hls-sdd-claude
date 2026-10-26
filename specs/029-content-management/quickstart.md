# Quickstart: Content Management (manual walkthrough)

Prerequisites: backend and frontend running with the dev seed (it adds a Content Contributor user, "Cora Contributor").

1. Sign in as Admin, open User Management, create a user with only the Content Contributor role. Confirm the role appears in the picker and no Manager or Teacher record is asked for.
2. Sign in as the new user. Confirm the menu shows only Dashboard, My Content, Notifications, Account, and the dashboard shows own-content counts. Open `/operations/tasks` directly: "not authorized".
3. As Contributor, create content "Phonics Week 1" (category Lesson, subject English), submit a 20 MB mp4. Confirm version 1, status Submitted, no task.
4. Try a 30 MB PDF: refused naming the PDF limit (25 MB). Try a .doc file: refused. Try a renamed .exe as .pdf: refused.
5. Sign in as Director. Open Content, find the item, open the file, return it with a comment. Confirm the Contributor is notified.
6. As Contributor, submit version 2 (a docx). Director approves. Confirm the history shows both versions, the comment and the approval date.
7. As Director, create content for a Manager with a due date. Confirm the Manager gets a task in My Tasks. The Manager submits a pdf; the task shows Completed; Director approves; the task shows Verified.
8. As Director, create and submit your own content. Confirm you cannot approve it. With no Admin holding Approve, confirm Directors and Admins get the "no reviewer" notice once. Grant Approve to Admin in the matrix; Admin approves.
9. Submit a new version of approved content: status returns to Submitted, the earlier approved version stays marked.
10. As Admin, remove a file version with a reason; the row stays, the file does not download.
11. Open the permission matrix: six role columns; Content Contributor is "not applicable" on everything except the allowed modules.
12. Sign in to the mobile app as the Contributor: refused. Dashboard (Director): Content tile counts match.
13. Regression: upload a 15 MB expense bill (refused at 10 MB) and a 5 MB bill (accepted); a MoU document still accepts 10 MB.
