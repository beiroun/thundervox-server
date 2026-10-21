// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.push.PushDeliveryKind
import com.ef_softworks.thundervox_server.push.PushDeliveryOutcome
import com.ef_softworks.thundervox_server.search.SmartQuery
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.util.UUID

/** Wake pushes to the operator's backend, live and test (push_delivery) - the former delivery log of the Integration page. */
@Component
class PushDeliverySource(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) : ServerEventSource {

    override val category = ServerEventCategory.PUSH

    override val administratorsOnly = true

    override fun select(query: SmartQuery?, problemsOnly: Boolean): SourceSelect =
        sourceSelect(
            category = category,
            from = "push_delivery p",
            alias = "p",
            timeColumn = "created_at",
            query = query,
            searchableFields = listOf(
                "p.call_id::text", "p.sip_call_id", "p.kind", "p.caller_number", "p.caller_external_id",
                "p.callee_number", "p.callee_external_id", "p.url", "p.outcome", "p.http_status::text",
                "p.response_excerpt", "p.error",
            ),
            exactKeys = listOf("p.caller_number", "p.callee_number", "p.call_id::text", "p.sip_call_id", "p.callee_external_id"),
            // Anything but a 2xx from the operator's backend: rejected, failed, or never sent (switched off)
            problemCondition = "p.outcome <> '${PushDeliveryOutcome.DELIVERED.name}'",
        )

    override fun load(ids: Collection<Long>): Map<Long, ServerEventResponse> =
        jdbcClient.sql(
            """
            SELECT p.id, p.created_at, p.call_id, p.sip_call_id, p.kind, p.caller_number, p.caller_external_id,
                   p.callee_number, p.callee_external_id, p.url, p.outcome, p.http_status, p.attempts, p.duration_ms,
                   p.response_excerpt, p.error, ca.id AS caller_account_id, ce.id AS callee_account_id
            FROM push_delivery p
            LEFT JOIN sip_account ca ON ca.tenant_id = p.tenant_id AND ca.username = p.caller_number
            LEFT JOIN sip_account ce ON ce.tenant_id = p.tenant_id AND ce.username = p.callee_number
            WHERE p.tenant_id = :tenantId AND p.id IN (:ids)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("ids", ids)
            .query { resultSet, _ ->
                val id = resultSet.getLong("id")
                ServerEventResponse(
                    key = ServerEventResponse.keyOf(category, id),
                    category = category,
                    id = id,
                    occurredAt = resultSet.localDateTime("created_at")!!,
                    push = PushDeliveryResponse(
                        callId = resultSet.getObject("call_id", UUID::class.java).toString(),
                        sipCallId = resultSet.getString("sip_call_id"),
                        kind = PushDeliveryKind.valueOf(resultSet.getString("kind")),
                        callerNumber = resultSet.getString("caller_number"),
                        callerExternalId = resultSet.getString("caller_external_id"),
                        callerAccountId = resultSet.nullableLong("caller_account_id"),
                        calleeNumber = resultSet.getString("callee_number"),
                        calleeExternalId = resultSet.getString("callee_external_id"),
                        calleeAccountId = resultSet.nullableLong("callee_account_id"),
                        url = resultSet.getString("url"),
                        outcome = PushDeliveryOutcome.valueOf(resultSet.getString("outcome")),
                        httpStatus = resultSet.nullableInt("http_status"),
                        attempts = resultSet.getInt("attempts"),
                        durationMs = resultSet.nullableInt("duration_ms"),
                        responseExcerpt = resultSet.getString("response_excerpt"),
                        error = resultSet.getString("error"),
                    ),
                )
            }
            .list()
            .associateBy { it.id }
}
