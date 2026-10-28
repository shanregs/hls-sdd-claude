# Data Model: User Themes

## user_preferences (Flyway V140__create_user_preferences.sql)

| Column | Type | Notes |
| --- | --- | --- |
| user_id | uuid PK, FK to users | one row per user; no row means defaults |
| theme_preset | varchar(30) not null default 'classic' | catalogue id; unknown id falls back to classic on read |
| theme_mode | varchar(10) not null default 'device' | check in ('light','dark','device') |
| custom_accent | varchar(7) null | `#RRGGBB`, validated against the contrast rule |
| sidebar_dark | boolean not null default false | web only |
| changed_at | timestamptz not null | client change time, used for last-write-wins; capped at server now + 5 minutes |
| updated_at | timestamptz not null default now() | server write time |

Named constraints per project convention; no soft delete. Reset deletes the row.

## Catalogue (not a table)

`theme-presets.json`: `{ id, name, light: {primary, accent, background, surface}, dark: {...} }` for classic, indigo-amber, teal-coral, deep-blue-green, plus semantic status chip colours and derived text colours.

## Derived values

Text, muted text, header text, tint and sidebar shade are computed from the four base colours by one function, so a custom accent needs no extra stored data.
