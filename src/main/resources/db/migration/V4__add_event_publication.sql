-- V4__add_event_publication.sql
CREATE TABLE event_publication (
    id UUID PRIMARY KEY,
    listener_id VARCHAR(512) NOT NULL,
    event_type VARCHAR(512) NOT NULL,
    serialized_event VARCHAR(4000) NOT NULL,
    publication_date TIMESTAMPTZ NOT NULL,
    completion_date TIMESTAMPTZ,
    status VARCHAR(255),
    completion_attempts INT,
    last_resubmission_date TIMESTAMPTZ
);
