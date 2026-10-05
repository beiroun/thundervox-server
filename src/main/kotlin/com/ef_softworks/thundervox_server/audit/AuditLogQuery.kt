// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.audit

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime

/** Newest-first pages of the audit trail for the console. */
@Component
class AuditLogQuery(
    private val jdbcClient: JdbcClient,
    private val jsonMapper: JsonMapper,
    private val defaultTenant: DefaultTenant,
) {

    fun page(page: Int, size: Int): AuditPageResponse {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) {
            throw WrongTypeException(
                "Audit page out of range: page=$page, size=$size",
                "Страница журнала: номер от 0, размер от 1 до $MAX_PAGE_SIZE"
            )
        }
        val total = jdbcClient.sql("SELECT count(*) FROM admin_action_log WHERE tenant_id = :tenantId")
            .param("tenantId", defaultTenant.id)
            .query(Long::class.javaObjectType)
            .single()

        val entries = jdbcClient.sql(
            """
            SELECT id, created_at, actor_type, actor_login, action, target_type, target_id, details::text AS details
            FROM admin_action_log
            WHERE tenant_id = :tenantId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.trimIndent()
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
    }
}
