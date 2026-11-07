-- LibertyWiki / illustrative schema baseline (PostgreSQL 16+)
-- NOT a complete production schema. No credentials included.
CREATE TABLE wiki_instance (
    id UUID PRIMARY KEY,
    site_name TEXT NOT NULL,
    default_locale VARCHAR(35) NOT NULL DEFAULT 'ko',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE wiki_namespace (
    wiki_id UUID NOT NULL REFERENCES wiki_instance(id),
    ns_id INTEGER NOT NULL,
    canonical_name TEXT NOT NULL,
    talk_ns_id INTEGER,
    allow_subpages BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (wiki_id, ns_id)
);
CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    email TEXT,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE access_role (
    id UUID PRIMARY KEY,
    role_name TEXT NOT NULL UNIQUE,
    permissions JSONB NOT NULL DEFAULT '[]'::jsonb
);
CREATE TABLE role_membership (
    user_id UUID NOT NULL REFERENCES app_user(id),
    role_id UUID NOT NULL REFERENCES access_role(id),
    assigned_by UUID REFERENCES app_user(id),
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ,
    reason TEXT,
    PRIMARY KEY (user_id,role_id)
);
CREATE TABLE page (
    id UUID PRIMARY KEY,
    wiki_id UUID NOT NULL,
    namespace_id INTEGER NOT NULL,
    normalized_title TEXT NOT NULL,
    current_revision_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    redirect_target TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (wiki_id,namespace_id) REFERENCES wiki_namespace(wiki_id,ns_id),
    UNIQUE (wiki_id,namespace_id,normalized_title)
);
CREATE TABLE content_blob (
    id UUID PRIMARY KEY,
    sha256 CHAR(64) NOT NULL,
    source_text TEXT NOT NULL,
    content_model VARCHAR(64) NOT NULL,
    content_format VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL CHECK(byte_size>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE page_revision (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL REFERENCES page(id),
    parent_revision_id UUID REFERENCES page_revision(id),
    author_id UUID REFERENCES app_user(id),
    edit_summary TEXT NOT NULL DEFAULT '',
    minor_edit BOOLEAN NOT NULL DEFAULT FALSE,
    visibility VARCHAR(24) NOT NULL DEFAULT 'VISIBLE',
    source_sha256 CHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE page ADD CONSTRAINT fk_page_current_revision
    FOREIGN KEY(current_revision_id) REFERENCES page_revision(id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE revision_slot (
    revision_id UUID NOT NULL REFERENCES page_revision(id),
    slot_role VARCHAR(64) NOT NULL DEFAULT 'main',
    content_id UUID NOT NULL REFERENCES content_blob(id),
    PRIMARY KEY (revision_id,slot_role)
);
CREATE TABLE page_protection (
    id UUID PRIMARY KEY,
    page_id UUID NOT NULL REFERENCES page(id),
    action_type VARCHAR(32) NOT NULL,
    required_permission VARCHAR(80) NOT NULL,
    expires_at TIMESTAMPTZ
);
CREATE TABLE object_blob (
    id UUID PRIMARY KEY,
    bucket TEXT NOT NULL,
    object_key TEXT NOT NULL,
    object_version TEXT,
    sha256 CHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL CHECK(byte_size>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE file_asset (
    id UUID PRIMARY KEY,
    wiki_id UUID NOT NULL REFERENCES wiki_instance(id),
    normalized_name TEXT NOT NULL,
    current_file_revision_id UUID,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    UNIQUE (wiki_id,normalized_name)
);
CREATE TABLE file_revision (
    id UUID PRIMARY KEY,
    file_id UUID NOT NULL REFERENCES file_asset(id),
    object_blob_id UUID NOT NULL REFERENCES object_blob(id),
    uploader_id UUID REFERENCES app_user(id),
    mime_type TEXT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    state VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE file_asset ADD CONSTRAINT fk_file_current_revision
    FOREIGN KEY(current_file_revision_id) REFERENCES file_revision(id) DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE page_link (
    source_page_id UUID NOT NULL REFERENCES page(id),
    link_type VARCHAR(24) NOT NULL,
    target_namespace_id INTEGER NOT NULL,
    target_normalized_title TEXT NOT NULL,
    PRIMARY KEY (source_page_id,link_type,target_namespace_id,target_normalized_title)
);
CREATE INDEX idx_page_link_target ON page_link(link_type,target_namespace_id,target_normalized_title);
CREATE TABLE watchlist (
    user_id UUID NOT NULL REFERENCES app_user(id),
    page_id UUID NOT NULL REFERENCES page(id),
    expires_at TIMESTAMPTZ,
    PRIMARY KEY (user_id,page_id)
);
CREATE TABLE audit_event (
    id UUID PRIMARY KEY,
    wiki_id UUID REFERENCES wiki_instance(id),
    actor_id UUID REFERENCES app_user(id),
    target_type VARCHAR(64),
    target_id TEXT,
    action_type VARCHAR(80) NOT NULL,
    reason TEXT,
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    aggregate_key TEXT NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_pending ON outbox_event(next_attempt_at,occurred_at) WHERE delivered_at IS NULL;
-- Additional triggers must enforce same-page current revision, same-file current revision,
-- parent revision belonging to same page, immutable revisions, and audit append-only semantics.
