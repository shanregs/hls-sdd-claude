# Quickstart: Manager Tasks

1. Run the backend with the dev profile (demo data seeds a few tasks) and sign in as Director.
2. OPERATIONS > Manager Tasks > New task: School visit for a Manager, due tomorrow.
3. Sign in as that Manager: OPERATIONS > My Tasks shows it under Upcoming and a notification "New task".
4. Acknowledge, Start, Complete with a note and one file.
5. As Director: Awaiting verification shows it; Verify. Try Send back on another.
6. Make a task overdue (dev seed has one): the Manager opens it, records a reason and a follow-up date; a Follow-up
   task appears. Run the deadline job (`POST /api/v1/dev/...` is not provided; tests call the job directly) and
   check the Director's notification after the escalation days.
7. Backend tests: `mvn -f backend/pom.xml test -Dtest='com.hls.tasks.*,com.hls.notification.Task*'`.
   Frontend: `npm test -- tasks` in `frontend`.
