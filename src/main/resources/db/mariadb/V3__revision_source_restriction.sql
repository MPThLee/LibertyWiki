-- SPDX-License-Identifier: AGPL-3.0-only
-- Presence restricts source to operators; revision contents remain append-only.
CREATE TABLE wiki_revision_source_restriction (
    revision_id UUID PRIMARY KEY,
    FOREIGN KEY (revision_id) REFERENCES wiki_revision (id)
) ENGINE=InnoDB;
