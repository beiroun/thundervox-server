// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.consoleuser.ConsoleRole
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.search.SmartQuery
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** What the console asks the event log for. */
data class ServerEventFilter(
    /** One journal, or all the viewer may see. */
    val category: ServerEventCategory?,
    /** Only what did not get through: calls nobody answered or that broke, pushes not delivered, refused API calls. */
    val problemsOnly: Boolean,
    val query: String?,
    val page: Int,
    val size: Int,
)

/**
 * The event log of the console: what happens on the server, newest first - numbers coming and going, calls,
 * wake pushes and the operator backends' calls to the service API - read from their journals side by side.
 *
 * One page is two statements over the union of the journals' keys (the count and the page itself, ordered by time
 * across journals) and one load per journal present on the page. The smart search runs in every journal over its
 * own fields; a one-word query equal to a key (a number, an external id, a call id, a token name) goes first.
 */
@Component
class ServerEventLog(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
    sources: List<ServerEventSource>,
) {

    private val sourcesByCategory = sources.associateBy { it.category }

    init {
        require(sourcesByCategory.keys == ServerEventCategory.entries.toSet()) {
            "Every event category needs exactly one source, got ${sources.map { it.category }}"
        }
    }

    /** The categories a role may see: readers never see the Integration side (pushes, service calls). */
    fun visibleCategories(role: ConsoleRole): List<ServerEventCategory> =
        ServerEventCategory.entries.filter { role != ConsoleRole.READER || !sourcesByCategory.getValue(it).administratorsOnly }

    fun page(filter: ServerEventFilter, role: ConsoleRole): ServerEventPageResponse {
        if (filter.page < 0 || filter.size !in 1..MAX_PAGE_SIZE) {
            throw WrongTypeException(
                "Event log page out of range: page=${filter.page}, size=${filter.size}",
                "Страница журнала событий: номер от 0, размер от 1 до $MAX_PAGE_SIZE"
            )
        }
        val visible = visibleCategories(role)
        if (filter.category != null && filter.category !in visible) {
            throw InsufficientPrivilegesException("Role $role may not read ${filter.category} events")
        }
        val query = SmartQuery.parse(filter.query)
        val selects = (filter.category?.let(::listOf) ?: visible)
            .mapNotNull { sourcesByCategory.getValue(it).select(query, filter.problemsOnly) }
        if (selects.isEmpty()) {
            return ServerEventPageResponse(emptyList(), 0, filter.page, filter.size, visible)
        }

        val union = selects.joinToString("\nUNION ALL\n") { it.sql }
        val total = bindAll(selects, jdbcClient.sql("SELECT count(*) FROM (\n$union\n) feed"))
            .param("tenantId", defaultTenant.id)
            .query(Long::class.javaObjectType)
            .single()

        val exactFirst = if (selects.any { it.search.ranksExactFirst }) "exact_rank, " else ""
        val keys = bindAll(
            selects,
            jdbcClient.sql(
                """
                SELECT category, id FROM (
                $union
                ) feed
                ORDER BY ${exactFirst}occurred_at DESC, category, id DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent()
            )
        )
            .param("tenantId", defaultTenant.id)
            .param("limit", filter.size)
            .param("offset", filter.page.toLong() * filter.size)
            .query { resultSet, _ -> ServerEventCategory.valueOf(resultSet.getString("category")) to resultSet.getLong("id") }
            .list()

        val loaded = keys.groupBy({ it.first }, { it.second })
            .mapValues { (category, ids) -> sourcesByCategory.getValue(category).load(ids) }
        // Page order is the union's; a row purged between the two statements is left out rather than failing the page
        val items = keys.mapNotNull { (category, id) -> loaded[category]?.get(id) }
        return ServerEventPageResponse(items, total, filter.page, filter.size, visible)
    }

    // Every journal's condition refers to the same :searchWordN / :searchExact with the same values, so binding them
    // from each journal in turn sets each parameter to what it already was
    private fun bindAll(selects: List<SourceSelect>, statement: JdbcClient.StatementSpec): JdbcClient.StatementSpec =
        selects.fold(statement) { bound, select -> select.search.bind(bound) }

    companion object {
        const val MAX_PAGE_SIZE = 200
    }
}
