-- SPDX-License-Identifier: AGPL-3.0-only
CREATE TABLE wiki_page (
    id uuid PRIMARY KEY,
    compat_id bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    namespace_id integer NOT NULL CHECK (namespace_id = 0),
    normalized_title text COLLATE "C" NOT NULL CHECK (octet_length(normalized_title) BETWEEN 1 AND 255),
    current_revision_id uuid NOT NULL,
    UNIQUE (namespace_id, normalized_title)
);

CREATE TABLE wiki_revision (
    id uuid PRIMARY KEY,
    compat_id bigint GENERATED ALWAYS AS IDENTITY UNIQUE,
    page_id uuid NOT NULL REFERENCES wiki_page (id),
    parent_revision_id uuid,
    source text NOT NULL CHECK (octet_length(source) <= 1048576),
    source_sha256 text NOT NULL CHECK (length(source_sha256) = 64),
    summary text NOT NULL CHECK (length(summary) <= 500),
    actor text NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (page_id, id),
    FOREIGN KEY (page_id, parent_revision_id) REFERENCES wiki_revision (page_id, id)
);

ALTER TABLE wiki_page ADD CONSTRAINT wiki_page_current_revision
    FOREIGN KEY (id, current_revision_id) REFERENCES wiki_revision (page_id, id)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE wiki_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type text NOT NULL,
    page_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    actor text NOT NULL,
    request_id uuid NOT NULL UNIQUE,
    occurred_at timestamptz NOT NULL,
    FOREIGN KEY (page_id, revision_id) REFERENCES wiki_revision (page_id, id)
);

CREATE TABLE wiki_outbox (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_type text NOT NULL,
    page_id uuid NOT NULL,
    revision_id uuid NOT NULL,
    actor text NOT NULL,
    request_id uuid NOT NULL UNIQUE,
    occurred_at timestamptz NOT NULL,
    published_at timestamptz,
    FOREIGN KEY (page_id, revision_id) REFERENCES wiki_revision (page_id, id)
);
