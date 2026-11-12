-- SPDX-License-Identifier: AGPL-3.0-only
CREATE INDEX wiki_revision_page_history ON wiki_revision (page_id, created_at, compat_id);
