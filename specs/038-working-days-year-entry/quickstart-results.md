
## Browser run (2026-10-29, Selenium, headless Chrome 156, local Docker stack)

`e2e/test_year_entry.py` (run `python -m pytest test_year_entry.py -v` in `e2e/`): 8 of 8 pass. Uses Demo School Two, 2031.

| Step | Result |
| --- | --- |
| 1, 2 open, twelve empty fields with calendar hints and "Nothing stored" | Pass |
| 3 enter a full year, one Save: "12 created"; the grid lists 12 rows | Pass |
| 4 change two months: "0 created, 2 updated, 0 unchanged" | Pass (see note) |
| 5 bad figures (31 in a 30-day month, 25.3): stopped in the browser with the range message, typed values kept, "Fix the figures marked in red.", the valid change saved | Pass (see note) |
| 6 two windows: second save refused for the changed month with "Use the stored figure (21)", its other month saved; typing the same figure is reported unchanged | Pass |
| 7 row deleted in the grid, Save from a stale screen: that month not saved, not recreated (grid still 11 rows) | Pass |
| 8 375 px: no horizontal page scroll, first and last field and Save reachable | Pass |
| 9 Use calendar counts fills only empty months; Same figure fills the rest; nothing stored until Save | Pass |
| 10 changing School with unsaved figures asks "Discard your changes?"; the page asks the browser for a reload warning (headless Chrome does not show the prompt, so the event was checked) | Pass |
| 11 Director works; Manager gets the not-authorized page | Pass (Create-without-Edit grant not tested) |
| 12 Save while offline, then online: 12 created once; saving the same figures again writes nothing | Pass |

Timings, scripted (a floor, not a person's time): screen load 0.6 to 1.7 s (SC-009 under 2 s), type and Save all twelve months 1.1 to 1.4 s (SC-009 under 3 s; SC-001 under 1 minute).

Notes:
- Quickstart step 4 says "2 updated, 10 unchanged". The screen sends only the months that changed, so "unchanged" counts only months re-sent with the figure already stored; the observed "2 updated, 0 unchanged" matches spec user story 5. Quickstart wording should be corrected.
- Quickstart step 5 expects the server to list refused months; figures that cannot be valid are stopped earlier, in the browser, and never sent. Focus did not move to the first bad month (that happens only after a server refusal).
