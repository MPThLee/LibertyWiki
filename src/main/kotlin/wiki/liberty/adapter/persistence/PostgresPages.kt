// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import wiki.liberty.application.FirstRevision
import wiki.liberty.application.PageInsert
import wiki.liberty.application.PageRepository
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
               r.compat_id AS compat_revision_id, r.source, r.created_at
        FROM wiki_page p
        JOIN wiki_revision r ON r.page_id = p.id AND r.id = p.current_revision_id
        WHERE $condition
        """.trimIndent(),
        { row, _ ->
            Page(
                row.getObject("id", UUID::class.java), row.getLong("compat_id"),
                checkNotNull(PageTitle.parse(row.getString("normalized_title"))),
                row.getObject("revision_id", UUID::class.java), row.getLong("compat_revision_id"),
                row.getString("source"), row.getTimestamp("created_at").toInstant(),
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

        val createdAt = page.createdAt.atOffset(ZoneOffset.UTC)
        val actor = page.actor.name.lowercase()
        val compatRevisionId = checkNotNull(jdbc.queryForObject(
            """
            INSERT INTO wiki_revision (id, page_id, source, source_sha256, summary, actor, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING compat_id
            """.trimIndent(),
            Long::class.javaObjectType,
            revisionId, pageId, page.source, page.sha256, page.summary, actor, createdAt,
        ))
        jdbc.update(
            """
            INSERT INTO wiki_audit (event_type, page_id, revision_id, actor, request_id, occurred_at)
            VALUES ('page.created', ?, ?, ?, ?, ?)
            """.trimIndent(),
            pageId, revisionId, actor, page.requestId, createdAt,
        )
        jdbc.update(
            """
            INSERT INTO wiki_outbox (event_type, page_id, revision_id, actor, request_id, occurred_at)
            VALUES ('page.created', ?, ?, ?, ?, ?)
            """.trimIndent(),
            pageId, revisionId, actor, page.requestId, createdAt,
        )
        return PageInsert.Created(Page(pageId, compatId, page.title, revisionId, compatRevisionId, page.source, page.createdAt))
    }
}
