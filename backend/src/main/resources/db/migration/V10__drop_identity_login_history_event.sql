-- Decommissions identity's interim login_history_event table (spec 001), now that spec 003's
-- audit.login_history_entry is the system of record (Constitution Principle VII, research.md §6).
-- Nothing in the codebase read this table (only LoginHistoryPublisher wrote to it), so this is a
-- pure removal with no data-migration need.
DROP TABLE login_history_event;
