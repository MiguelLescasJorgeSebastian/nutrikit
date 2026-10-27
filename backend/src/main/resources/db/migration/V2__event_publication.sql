-- Registro de publicaciones de eventos de Spring Modulith (patrón outbox).
-- Antes lo creaba Modulith al arrancar; ahora lo crea Flyway porque la app ya no se conecta
-- como dueña del esquema y no puede crear tablas. Estructura v2 de spring-modulith-events-jdbc.
-- IF NOT EXISTS: las bases de desarrollo anteriores ya tienen la tabla.

CREATE TABLE IF NOT EXISTS event_publication (
    id                     uuid                     NOT NULL,
    listener_id            text                     NOT NULL,
    event_type             text                     NOT NULL,
    serialized_event       text                     NOT NULL,
    publication_date       timestamp with time zone NOT NULL,
    completion_date        timestamp with time zone,
    status                 text,
    completion_attempts    integer,
    last_resubmission_date timestamp with time zone,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
    ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
    ON event_publication (completion_date);
