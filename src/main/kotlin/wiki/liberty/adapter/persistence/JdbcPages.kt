// SPDX-License-Identifier: AGPL-3.0-only
package wiki.liberty.adapter.persistence

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import wiki.liberty.application.FirstRevision
import wiki.liberty.application.HistoryDirection
import wiki.liberty.application.HistoryWindow
import wiki.liberty.application.PageInsert
import wiki.liberty.application.PageRepository
import wiki.liberty.application.RevisionWrite
import wiki.liberty.domain.Page
import wiki.liberty.domain.PageLookup
import wiki.liberty.domain.PageTitle
import wiki.liberty.domain.RevisionLookup
import wiki.liberty.domain.RevisionMetadata
import wiki.liberty.domain.RevisionPage
import wiki.liberty.domain.RevisionSource
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.sql.DataSource

@Repository
class JdbcPages(private val jdbc: JdbcTemplate, dataSource: DataSource) : PageRepository {
    private val maria = dataSource.connection.use { connection ->
        val metadata = connection.metaData
        when (val engine = metadata.databaseProductName) {
            "MariaDB" -> {
                check(metadata.databaseMajorVersion > 11 ||
                    metadata.databaseMajorVersion == 11 && metadata.databaseMinorVersion >= 8) {
                    "MariaDB 11.8 or newer is required."
                }
                check(connection.transactionIsolation == Connection.TRANSACTION_READ_COMMITTED) {
                    "MariaDB requires READ COMMITTED transaction isolation."
                }
                val mode = connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT @@SESSION.sql_mode").use { result ->
                        check(result.next())
                        result.getString(1).split(',').toSet()
                    }
                }
                check("STRICT_ALL_TABLES" in mode || "STRICT_TRANS_TABLES" in mode) {
                    "MariaDB requires a strict SQL mode for transactional tables."
                }
                true
            }
            "PostgreSQL" -> {
                check(metadata.databaseMajorVersion >= 17) { "PostgreSQL 17 or newer is required." }
                false
            }
            else -> error("Unsupported database engine: $engine")
        }
    }
    private fun databaseValue(value: Any?): Any? = if (maria && value is UUID) value.toString() else value
    private fun uuid(row: ResultSet, name: String): UUID? = row.getString(name)?.let(UUID::fromString)
    private fun time(value: Instant): Any = if (maria) LocalDateTime.ofInstant(value, ZoneOffset.UTC) else value.atOffset(ZoneOffset.UTC)
    private fun instant(row: ResultSet): Instant = if (maria) row.getObject("created_at", LocalDateTime::class.java).toInstant(ZoneOffset.UTC) else row.getTimestamp("created_at").toInstant()
    override fun findByTitle(title: PageTitle) = find("p.namespace_id = 0 AND p.normalized_title = ?", title.value)
    override fun findById(id: PageLookup.Id) = find("p.id = ?", id.value)
    override fun findByCompatId(id: PageLookup.CompatId) = find("p.compat_id = ?", id.value)

    override fun findRevisionPage(lookup: PageLookup): RevisionPage? {
        val (condition, key) = pageKey(lookup)
        return jdbc.query(
            "SELECT p.id, p.compat_id, p.normalized_title, p.current_revision_id FROM wiki_page p WHERE $condition",
            { row, _ -> revisionPage(row) },
            databaseValue(key),
        ).singleOrNull()
    }

    override fun lockForEdit(lookup: PageLookup): Page? {
        val (condition, key) = pageKey(lookup)
        val id = jdbc.query(
            "SELECT p.id FROM wiki_page p WHERE $condition FOR UPDATE",
            { row, _ -> checkNotNull(uuid(row, "id")) }, databaseValue(key),
        ).singleOrNull() ?: return null
        // Read the committed revision and visibility after acquiring the page lock.
        return findById(PageLookup.Id(id))
    }

    private fun pageKey(lookup: PageLookup) = when (lookup) {
        is PageLookup.Title -> "p.namespace_id = 0 AND p.normalized_title = ?" to lookup.input
        is PageLookup.Id -> "p.id = ?" to lookup.value
        is PageLookup.CompatId -> "p.compat_id = ?" to lookup.value
    }

    override fun findRevision(lookup: RevisionLookup): RevisionSource? {
        val (column, key) = revisionKey(lookup)
        return jdbc.query(
            """
            SELECT $revisionColumns, p.id, p.compat_id, p.normalized_title, p.current_revision_id, r.source
            FROM wiki_revision r JOIN wiki_page p ON p.id = r.page_id
            $parentJoin
            WHERE r.$column = ?
            """.trimIndent(),
            { row, _ -> RevisionSource(revisionPage(row), metadata(row), row.getString("source")) },
            databaseValue(key),
        ).singleOrNull()
    }

    override fun findRevisions(from: RevisionLookup, to: RevisionLookup): Pair<RevisionSource?, RevisionSource?> {
        val (fromColumn, fromKey) = revisionKey(from)
        val (toColumn, toKey) = revisionKey(to)
        val revisions = jdbc.query(
            """
            SELECT $revisionColumns, p.id, p.compat_id, p.normalized_title, p.current_revision_id, r.source
            FROM wiki_revision r JOIN wiki_page p ON p.id = r.page_id
            $parentJoin
            WHERE r.$fromColumn = ? OR r.$toColumn = ?
            """.trimIndent(),
            { row, _ -> RevisionSource(revisionPage(row), metadata(row), row.getString("source")) },
            databaseValue(fromKey), databaseValue(toKey),
        )
        fun select(lookup: RevisionLookup) = revisions.singleOrNull {
            when (lookup) {
                is RevisionLookup.Id -> it.revision.id == lookup.value
                is RevisionLookup.CompatId -> it.revision.compatId == lookup.value
            }
        }
        return select(from) to select(to)
    }

    override fun findHistoryAnchor(page: RevisionPage, lookup: RevisionLookup): RevisionMetadata? {
        val (column, key) = revisionKey(lookup)
        return jdbc.query(
            "SELECT $revisionColumns FROM wiki_revision r $parentJoin WHERE $historyScope AND r.$column = ?",
            { row, _ -> metadata(row) },
            databaseValue(page.id), databaseValue(page.currentRevisionId), databaseValue(key),
        ).singleOrNull()
    }

    override fun findHistory(page: RevisionPage, window: HistoryWindow, count: Int): List<RevisionMetadata> {
        val older = window.direction == HistoryDirection.OLDER
        val order = if (older) "DESC" else "ASC"
        val operator = (if (older) "<" else ">") + if (window.inclusive) "=" else ""
        val anchor = window.anchor
        val bound = when {
            anchor == null -> ""
            maria -> {
                val strict = if (older) "<" else ">"
                "AND (r.created_at $strict ? OR (r.created_at = ? AND r.compat_id $operator ?))"
            }
            else -> "AND (r.created_at, r.compat_id) $operator (?, ?)"
        }
        val arguments = mutableListOf<Any>(checkNotNull(databaseValue(page.id)), checkNotNull(databaseValue(page.currentRevisionId)))
        if (anchor != null) {
            val createdAt = time(anchor.createdAt)
            arguments.add(createdAt)
            if (maria) arguments.add(createdAt)
            arguments.add(anchor.compatId)
        }
        arguments.add(count)
        return jdbc.query(
            """
            SELECT $revisionColumns FROM wiki_revision r
            $parentJoin
            WHERE $historyScope $bound
            ORDER BY r.created_at $order, r.compat_id $order LIMIT ?
            """.trimIndent(),
            { row, _ -> metadata(row) },
            *arguments.toTypedArray(),
        )
    }

    private fun revisionKey(lookup: RevisionLookup) = when (lookup) {
        is RevisionLookup.Id -> "id" to lookup.value
        is RevisionLookup.CompatId -> "compat_id" to lookup.value
    }

    private fun revisionPage(row: ResultSet) = RevisionPage(
        checkNotNull(uuid(row, "id")), row.getLong("compat_id"),
        checkNotNull(PageTitle.parse(row.getString("normalized_title"))),
        checkNotNull(uuid(row, "current_revision_id")),
    )

    private fun metadata(row: ResultSet) = RevisionMetadata(
        checkNotNull(uuid(row, "revision_id")), row.getLong("compat_revision_id"),
        uuid(row, "parent_revision_id"),
        row.getObject("compat_parent_id", Long::class.javaObjectType),
        instant(row), row.getString("summary"), row.getString("actor"),
        row.getInt("source_size"), row.getObject("delta", Int::class.javaObjectType),
        row.getBoolean("source_restricted"), row.getBoolean("parent_source_restricted"),
    )

    private fun find(condition: String, key: Any): Page? = jdbc.query(
        """
        SELECT p.id, p.compat_id, p.normalized_title, r.id AS revision_id,
               r.compat_id AS compat_revision_id, r.source, r.created_at, parent.compat_id AS compat_parent_id,
               restriction.revision_id IS NOT NULL AS source_restricted
        FROM wiki_page p
        JOIN wiki_revision r ON r.page_id = p.id AND r.id = p.current_revision_id
        LEFT JOIN wiki_revision parent ON parent.page_id = p.id AND parent.id = r.parent_revision_id
        LEFT JOIN wiki_revision_source_restriction restriction ON restriction.revision_id = r.id
        WHERE $condition
        """.trimIndent(),
        { row, _ ->
            Page(
                checkNotNull(uuid(row, "id")), row.getLong("compat_id"),
                checkNotNull(PageTitle.parse(row.getString("normalized_title"))),
                checkNotNull(uuid(row, "revision_id")), row.getLong("compat_revision_id"),
                row.getString("source"), instant(row),
                row.getObject("compat_parent_id", Long::class.javaObjectType),
                row.getBoolean("source_restricted"),
            )
        },
        databaseValue(key),
    ).singleOrNull()

    override fun create(page: FirstRevision): PageInsert {
        val pageId = UUID.randomUUID()
        val revisionId = UUID.randomUUID()
        val compatId = if (maria) {
            // A committed duplicate can be reported without waiting for an edit's row lock.
            val exists = jdbc.query(
                "SELECT 1 FROM wiki_page WHERE namespace_id = 0 AND normalized_title = ?",
                { _, _ -> true }, page.title.value,
            ).isNotEmpty()
            if (exists) return PageInsert.DuplicateTitle
            val rows = jdbc.query(
                """
                INSERT INTO wiki_page (id, namespace_id, normalized_title, current_revision_id)
                VALUES (?, 0, ?, NULL)
                ON DUPLICATE KEY UPDATE id = id
                RETURNING id, compat_id
                """.trimIndent(),
                { row, _ -> checkNotNull(uuid(row, "id")) to row.getLong("compat_id") },
                databaseValue(pageId), page.title.value,
            ).single()
            if (rows.first != pageId) return PageInsert.DuplicateTitle
            rows.second
        } else jdbc.query(
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
        if (maria) check(jdbc.update("UPDATE wiki_page SET current_revision_id = ? WHERE id = ? AND current_revision_id IS NULL", databaseValue(revisionId), databaseValue(pageId)) == 1)
        return PageInsert.Created(Page(pageId, compatId, page.title, revisionId, compatRevisionId, revision.source, revision.createdAt))
    }

    override fun append(base: Page, revision: RevisionWrite): Page {
        val revisionId = UUID.randomUUID()
        val compatRevisionId = if (maria) writeRevision(base.id, revisionId, base.revisionId, revision, "revision.committed") else null
        // PostgreSQL defers the foreign key; MariaDB requires the revision first.
        val updated = jdbc.update(
            "UPDATE wiki_page SET current_revision_id = ? WHERE id = ? AND current_revision_id = ?",
            databaseValue(revisionId), databaseValue(base.id), databaseValue(base.revisionId),
        )
        check(updated == 1) { "The locked page no longer has the expected current revision." }
        val writtenId = compatRevisionId ?: writeRevision(base.id, revisionId, base.revisionId, revision, "revision.committed")
        return base.copy(
            revisionId = revisionId,
            compatRevisionId = writtenId,
            source = revision.source,
            createdAt = revision.createdAt,
            compatParentRevisionId = base.compatRevisionId,
            sourceRestricted = false,
        )
    }

    private fun writeRevision(
        pageId: UUID, revisionId: UUID, parentId: UUID?, revision: RevisionWrite, eventType: String,
    ): Long {
        val createdAt = time(revision.createdAt)
        val actor = revision.actor.name.lowercase()
        val compatRevisionId = checkNotNull(jdbc.queryForObject(
            """
            INSERT INTO wiki_revision (id, page_id, parent_revision_id, source, source_sha256, summary, actor, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING compat_id
            """.trimIndent(),
            Long::class.javaObjectType,
            databaseValue(revisionId), databaseValue(pageId), databaseValue(parentId), revision.source, revision.sha256, revision.summary, actor, createdAt,
        ))
        jdbc.update(
            """
            INSERT INTO wiki_audit (event_type, page_id, revision_id, actor, request_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            eventType, databaseValue(pageId), databaseValue(revisionId), actor, databaseValue(revision.requestId), createdAt,
        )
        jdbc.update(
            """
            INSERT INTO wiki_outbox (event_type, page_id, revision_id, actor, request_id, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            eventType, databaseValue(pageId), databaseValue(revisionId), actor, databaseValue(revision.requestId), createdAt,
        )
        return compatRevisionId
    }

    private companion object {
        // Revisions of one page are serialized, so its captured head bounds the read across statements.
        const val historyScope = "r.page_id = ? AND r.compat_id <= (SELECT compat_id FROM wiki_revision WHERE id = ?)"
        const val parentJoin = """
            LEFT JOIN wiki_revision parent ON parent.page_id = r.page_id AND parent.id = r.parent_revision_id
            LEFT JOIN wiki_revision_source_restriction restriction ON restriction.revision_id = r.id
            LEFT JOIN wiki_revision_source_restriction parent_restriction ON parent_restriction.revision_id = parent.id
        """
        const val revisionColumns = """
            r.id AS revision_id, r.compat_id AS compat_revision_id, r.parent_revision_id,
            parent.compat_id AS compat_parent_id, r.created_at, r.summary, r.actor,
            octet_length(r.source) AS source_size,
            octet_length(r.source) - octet_length(parent.source) AS delta,
            restriction.revision_id IS NOT NULL AS source_restricted,
            parent_restriction.revision_id IS NOT NULL AS parent_source_restricted
        """
    }
}
