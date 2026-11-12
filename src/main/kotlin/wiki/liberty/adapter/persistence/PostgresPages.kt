// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import wiki.liberty.application.FirstRevision
import wiki.liberty.application.PageInsert
import wiki.liberty.application.PageRepository
import wiki.liberty.application.RevisionAppend
import wiki.liberty.application.RevisionWrite
import wiki.liberty.domain.Page
import wiki.liberty.domain.PageLookup
import wiki.liberty.domain.PageTitle
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresPages(private val jdbc: JdbcTemplate) : PageRepository {
    override fun findByTitle(title: PageTitle) = find("p.namespace_id = 0 AND p.normalized_title = ?", title.value)
    override fun findById(id: PageLookup.Id) = find("p.id = ?", id.value)
    override fun findByCompatId(id: PageLookup.CompatId) = find("p.compat_id = ?", id.value)

    private fun find(condition: String, key: Any): Page? = jdbc.query(
        """
        SELECT p.id, p.compat_id, p.normalized_title, r.id AS revision_id,
               r.compat_id AS compat_revision_id, r.source, r.created_at, parent.compat_id AS compat_parent_id
        FROM wiki_page p
        JOIN wiki_revision r ON r.page_id = p.id AND r.id = p.current_revision_id
        LEFT JOIN wiki_revision parent ON parent.page_id = p.id AND parent.id = r.parent_revision_id
        WHERE $condition
        """.trimIndent(),
        { row, _ ->
            Page(
                row.getObject("id", UUID::class.java), row.getLong("compat_id"),
                checkNotNull(PageTitle.parse(row.getString("normalized_title"))),
                row.getObject("revision_id", UUID::class.java), row.getLong("compat_revision_id"),
                row.getString("source"), row.getTimestamp("created_at").toInstant(),
                row.getObject("compat_parent_id", Long::class.javaObjectType),
            )
        },
        key,
    ).singleOrNull()

    override fun create(page: FirstRevision): PageInsert {
        val pageId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        val compatId = jdbc.query(
            """
            INSERT INTO wiki_page (id, namespace_id, normalized_title, current_revision_id)
            VALUES (?, 0, ?, ?)
            ON CONFLICT (namespace_id, normalized_title) DO NOTHING
            RETURNING compat_id
            """.trimIndent(),
            { row, _ -> row.getLong(1) },
            pageId, page.title.value, revisionId,
        ).singleOrNull() ?: return PageInsert.DuplicateTitle

        val revision = page.revision
        val compatRevisionId = writeRevision(pageId, revisionId, null, revision, "page.created")
        return PageInsert.Created(Page(pageId, compatId, page.title, revisionId, compatRevisionId, revision.source, revision.createdAt))
    }

    override fun append(base: Page, revision: RevisionWrite): RevisionAppend {
        val revisionId = UUID.randomUUID()
        // The deferred foreign key permits claiming the head before inserting the revision.
        val updated = jdbc.update(
            "UPDATE wiki_page SET current_revision_id = ? WHERE id = ? AND current_revision_id = ?",
            revisionId, base.id, base.revisionId,
        )
        if (updated == 0) return RevisionAppend.StaleBase
        val compatRevisionId = writeRevision(base.id, revisionId, base.revisionId, revision, "revision.committed")
        return RevisionAppend.Committed(base.copy(
            revisionId = revisionId,
            compatRevisionId = compatRevisionId,
            source = revision.source,
            createdAt = revision.createdAt,
            compatParentRevisionId = base.compatRevisionId,
        ))
    }

    private fun writeRevision(
        pageId: UUID, revisionId: UUID, parentId: UUID?, revision: RevisionWrite, eventType: String,
    ): Long {
        val createdAt = revision.createdAt.atOffset(ZoneOffset.UTC)
        val actor = revision.actor.name.lowercase()
        val compatRevisionId = checkNotNull(jdbc.queryForObject(
            """
            INSERT INTO wiki_revision (id, page_id, parent_revision_id, source, source_sha256, summary, actor, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING compat_id
            """.trimIndent(),
            Long::class.javaObjectType,
            revisionId, pageId, parentId, revision.source, revision.sha256, revision.summary, actor, createdAt,
        ))
        jdbc.update(
            """
            INSERT INTO wiki_audit (event_type, page_id, revision_id, actor, request_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            eventType, pageId, revisionId, actor, revision.requestId, createdAt,
        )
        jdbc.update(
            """
            INSERT INTO wiki_outbox (event_type, page_id, revision_id, actor, request_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            eventType, pageId, revisionId, actor, revision.requestId, createdAt,
        )
        return compatRevisionId
    }
}
