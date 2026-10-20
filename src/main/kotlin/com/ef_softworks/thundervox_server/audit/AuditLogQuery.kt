// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.audit

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.search.SmartQuery
import com.ef_softworks.thundervox_server.search.SmartSearchSql
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/** Newest-first pages of the audit trail for the console, narrowed by the smart search. */
@Component
class AuditLogQuery(
    private val jdbcClient: JdbcClient,
    private val jsonMapper: JsonMapper,
    private val defaultTenant: DefaultTenant,
) {

    fun page(page: Int, size: Int, query: String?): AuditPageResponse {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) {
            throw WrongTypeException(
                "Audit page out of range: page=$page, size=$size",
                "Страница журнала: номер от 0, размер от 1 до $MAX_PAGE_SIZE"
            )
        }
        val search = SmartSearchSql(SmartQuery.parse(query), searchableFields, exactKeys)
        val total = search.bind(
            jdbcClient.sql("SELECT count(*) FROM admin_action_log WHERE tenant_id = :tenantId${search.condition}")
        )
            .param("tenantId", defaultTenant.id)
            .query(Long::class.javaObjectType)
            .single()

        val entries = search.bind(
            jdbcClient.sql(
                """
                SELECT id, created_at, actor_type, actor_login, action, target_type, target_id, details::text AS details
                FROM admin_action_log
                WHERE tenant_id = :tenantId${search.condition}
                ORDER BY ${search.exactFirstOrder}created_at DESC, id DESC
                LIMIT :limit OFFSET :offset
                """.trimIndent()
            )
        )
            .param("tenantId", defaultTenant.id)
            .param("limit", size)
            .param("offset", page.toLong() * size)
            .query { resultSet, _ ->
                AuditEntryResponse(
                    id = resultSet.getLong("id"),
                    createdAt = resultSet.getObject("created_at", LocalDateTime::class.java),
                    actorType = resultSet.getString("actor_type"),
                    actorLogin = resultSet.getString("actor_login"),
                    action = resultSet.getString("action"),
                    targetType = resultSet.getString("target_type"),
                    targetId = resultSet.getObject("target_id", Long::class.javaObjectType),
                    details = resultSet.getString("details")?.let { jsonMapper.readTree(it) },
                )
            }
            .list()

        return AuditPageResponse(items = entries, total = total, page = page, size = size)
    }

    companion object {
        const val MAX_PAGE_SIZE = 200

        /** Who, what, to which record and every value of the details (an IP, a number, a login, a token name). */
        private val searchableFields = listOf(
            "actor_login",
            "action",
            "target_type",
            "target_id::text",
            "CASE WHEN jsonb_typeof(details) = 'object' THEN (SELECT string_agg(value, ' ') FROM jsonb_each_text(details)) END",
        )

        /** A one-word query equal to one of these puts the entry first: the record id, the actor, the number, the login. */
        private val exactKeys = listOf("target_id::text", "actor_login", "details->>'username'", "details->>'login'")
    }
}
