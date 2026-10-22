// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.search.SmartQuery
import com.ef_softworks.thundervox_server.search.SmartSearchSql
import java.sql.ResultSet
import java.time.LocalDateTime

/**
 * One journal of the event log. The journals stay the only truth - each is written where its events happen (the
 * presence sampler, the call event queue, the push worker, the service call filter) - and the log reads them
 * side by side: [select] gives the journal's keys for the shared page, [load] the full rows of the page.
 */
interface ServerEventSource {

    val category: ServerEventCategory

    /** Pushes and service calls belong to the Integration side, which readers never see. */
    val administratorsOnly: Boolean

    /**
     * `SELECT '<category>' AS category, <id> AS id, <time> AS occurred_at, <rank> AS exact_rank FROM … WHERE …`
     * for the union of the page, narrowed as [selection] asks.
     */
    fun select(selection: EventSelection): SourceSelect

    /** The full rows of the page, by id; an id that vanished in between (housekeeping) is simply absent. */
    fun load(ids: Collection<Long>): Map<Long, ServerEventResponse>
}

/** What one page of the log asks every journal for. */
data class EventSelection(
    val query: SmartQuery?,
    /** Only what did not get through. */
    val problemsOnly: Boolean,
)

/** A journal's part of the union; [search] binds the words its condition refers to. */
class SourceSelect(val sql: String, val search: SmartSearchSql)

/**
 * Builds a [SourceSelect] the same way for every journal: [from] is the FROM clause with the journal aliased as
 * [alias], every expression is over the journal's own columns (and its joins), never operator input.
 * [problemCondition] applies only when the selection asks for problems.
 */
internal fun sourceSelect(
    category: ServerEventCategory,
    from: String,
    alias: String,
    timeColumn: String,
    selection: EventSelection,
    searchableFields: List<String>,
    exactKeys: List<String>,
    problemCondition: String,
): SourceSelect {
    val search = SmartSearchSql(selection.query, searchableFields, exactKeys)
    val conditions = if (selection.problemsOnly) " AND ($problemCondition)" else ""
    val sql = """
        SELECT '${category.name}' AS category, $alias.id AS id, $alias.$timeColumn AS occurred_at, ${search.exactRank} AS exact_rank
        FROM $from
        WHERE $alias.tenant_id = :tenantId${search.condition}$conditions
    """.trimIndent()
    return SourceSelect(sql, search)
}

internal fun ResultSet.localDateTime(column: String): LocalDateTime? = getObject(column, LocalDateTime::class.java)

internal fun ResultSet.nullableLong(column: String): Long? = getObject(column, Long::class.javaObjectType)

internal fun ResultSet.nullableInt(column: String): Int? = getObject(column, Int::class.javaObjectType)
