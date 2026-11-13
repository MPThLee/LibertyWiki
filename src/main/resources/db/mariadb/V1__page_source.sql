-- SPDX-License-Identifier: AGPL-3.0-only
CREATE TABLE wiki_page (
    id UUID PRIMARY KEY,
    compat_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    namespace_id INTEGER NOT NULL CHECK (namespace_id = 0),
    normalized_title VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_nopad_bin NOT NULL CHECK (OCTET_LENGTH(normalized_title) BETWEEN 1 AND 255),
    current_revision_id UUID,
    UNIQUE (namespace_id, normalized_title)
) ENGINE=InnoDB;

CREATE TABLE wiki_revision (
    id UUID PRIMARY KEY,
    compat_id BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
    page_id UUID NOT NULL,
    parent_revision_id UUID,
    source MEDIUMTEXT CHARACTER SET utf8mb4 NOT NULL CHECK (OCTET_LENGTH(source) <= 1048576),
    source_sha256 VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(source_sha256) = 64),
    summary VARCHAR(500) CHARACTER SET utf8mb4 NOT NULL,
    actor VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE (page_id, id),
    FOREIGN KEY (page_id) REFERENCES wiki_page (id),
    FOREIGN KEY (page_id, parent_revision_id) REFERENCES wiki_revision (page_id, id)
) ENGINE=InnoDB;

ALTER TABLE wiki_page ADD CONSTRAINT wiki_page_current_revision
    FOREIGN KEY (id, current_revision_id) REFERENCES wiki_revision (page_id, id);

CREATE TABLE wiki_audit (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    page_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    actor VARCHAR(255) NOT NULL,
    request_id UUID NOT NULL UNIQUE,
    occurred_at DATETIME(6) NOT NULL,
    FOREIGN KEY (page_id, revision_id) REFERENCES wiki_revision (page_id, id)
) ENGINE=InnoDB;

CREATE TABLE wiki_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(255) NOT NULL,
    page_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    actor VARCHAR(255) NOT NULL,
    request_id UUID NOT NULL UNIQUE,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    FOREIGN KEY (page_id, revision_id) REFERENCES wiki_revision (page_id, id)
) ENGINE=InnoDB;
