-- Spring Modulith's JPA-backed event publication registry (research.md §1): backs
-- @ApplicationModuleListener with durable, at-least-once delivery. Flyway owns the schema project-
-- wide (Hibernate ddl-auto=validate), so this table is hand-authored to match exactly what
-- spring-modulith-events-jpa 2.1.1's DefaultJpaEventPublication/ArchivedJpaEventPublication
-- entities expect (verified against the library's actual Hibernate mapping). `serialized_event` is
-- widened to TEXT: the entity's default-length (255) VARCHAR overflowed on real payloads in testing
-- (event class name + all field names/values as JSON routinely exceeds 255 chars); Hibernate's
-- ddl-auto=validate does not enforce exact column length, only presence/type family, so a wider
-- column validates fine against the library's un-annotated (implicit length=255) mapping.
CREATE TABLE event_publication (
    id                       UUID PRIMARY KEY,
    listener_id              VARCHAR(255) NOT NULL,
    event_type               VARCHAR(255) NOT NULL,
    serialized_event         TEXT         NOT NULL,
    publication_date         TIMESTAMPTZ  NOT NULL,
    completion_date          TIMESTAMPTZ,
    last_resubmission_date   TIMESTAMPTZ,
    completion_attempts      INTEGER      NOT NULL,
    status                   VARCHAR(255)
);

CREATE TABLE event_publication_archive (
    id                       UUID PRIMARY KEY,
    listener_id              VARCHAR(255) NOT NULL,
    event_type               VARCHAR(255) NOT NULL,
    serialized_event         TEXT         NOT NULL,
    publication_date         TIMESTAMPTZ  NOT NULL,
    completion_date          TIMESTAMPTZ,
    last_resubmission_date   TIMESTAMPTZ,
    completion_attempts      INTEGER      NOT NULL,
    status                   VARCHAR(255)
);
