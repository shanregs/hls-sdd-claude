# Quickstart results: 040 same-day flexibility (browser part)

Run 2026-11-07 on the local stack with `e2e/test_same_day_flexibility.py` (Selenium, headless Chrome): **12 passed, 0 failed**.

Covered (web parts of steps 1, 3, 6, 8 and 10): the same-day leave dialog and refusal (a repeat run on the same day meets the leave the first run recorded and gets the overlap refusal), the Leave Management same-day filter, "Done on the spot" on a planned task (the note of 10 characters is checked when Mark done is pressed), the Manager Tasks, Activity Calendar and Substitutions same-day filters, the school-visit dialog and the Same-day report with its Zone filter.

Not covered here (T049 stays open for these): the mobile steps, notification inboxes and the steps that need a second day.
