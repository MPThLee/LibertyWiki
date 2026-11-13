-- SPDX-License-Identifier: AGPL-3.0-only
-- Preserve byte-exact title collation while removing dependence on the catalog default.
ALTER TABLE wiki_page DEFAULT CHARACTER SET utf8mb4;
ALTER TABLE wiki_revision
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY source_sha256 VARCHAR(64) CHARACTER SET utf8mb4 NOT NULL,
    MODIFY actor VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL,
    ADD CONSTRAINT wiki_revision_sha256_length CHECK (CHAR_LENGTH(source_sha256) = 64),
    ADD CONSTRAINT wiki_revision_summary_length CHECK (CHAR_LENGTH(summary) <= 500);
ALTER TABLE wiki_audit
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY event_type VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL,
    MODIFY actor VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL;
ALTER TABLE wiki_outbox
    DEFAULT CHARACTER SET utf8mb4,
    MODIFY event_type VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL,
    MODIFY actor VARCHAR(255) CHARACTER SET utf8mb4 NOT NULL;
ALTER TABLE wiki_revision_source_restriction DEFAULT CHARACTER SET utf8mb4;
