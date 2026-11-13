-- SPDX-License-Identifier: AGPL-3.0-only
-- Presence restricts source to operators; revision contents remain append-only.
CREATE TABLE wiki_revision_source_restriction (
    revision_id uuid PRIMARY KEY REFERENCES wiki_revision (id)
);
