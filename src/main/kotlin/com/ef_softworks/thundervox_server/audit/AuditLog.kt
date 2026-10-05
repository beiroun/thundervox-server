// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.audit

import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * Append-only trail of every change to who may register or log in.
 *
 * MANDATORY transaction: an entry is written in the transaction of the change it describes, so the change and its
 * trace commit or roll back together - an action without its entry (or the other way round) cannot happen.
 * Details never carry a password, only the fact that one was issued.
 */
@Component
class AuditLog(
    private val jdbcClient: JdbcClient,
    private val jsonMapper: JsonMapper,
    private val defaultTenant: DefaultTenant,
) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun record(
        actor: AuditActor,
        action: AuditAction,
        targetType: AuditTargetType,
        targetId: Long?,
        details: Map<String, Any?> = emptyMap(),
    ) {
        jdbcClient.sql(
            """
            INSERT INTO admin_action_log (tenant_id, actor_type, actor_login, action, target_type, target_id, details)
            VALUES (:tenantId, :actorType, :actorLogin, :action, :targetType, :targetId, CAST(:details AS jsonb))
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("actorType", actor.type.name)
            .param("actorLogin", actor.login)
            .param("action", action.name)
            .param("targetType", targetType.name)
            .param("targetId", targetId)
            .param("details", jsonMapper.writeValueAsString(details))
            .update()
    }
}
