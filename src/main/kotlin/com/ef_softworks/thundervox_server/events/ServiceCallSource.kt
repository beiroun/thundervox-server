// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.servicecall.ServiceCallOperation
import com.ef_softworks.thundervox_server.servicecall.ServiceCallerAuth
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** Calls of the operator's backends to the service API, signed by the token each presented (service_call_log). */
@Component
class ServiceCallSource(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) : ServerEventSource {

    override val category = ServerEventCategory.SERVICE_CALL

    override val administratorsOnly = true

    override fun select(selection: EventSelection): SourceSelect =
        sourceSelect(
            category = category,
            from = "service_call_log s",
            alias = "s",
            timeColumn = "occurred_at",
            selection = selection,
            searchableFields = listOf(
                "s.token_name", "s.token_prefix", "s.caller_auth", "s.method", "s.path", "s.operation", "s.sip_kind",
                "s.external_id", "s.username", "s.http_status::text", "s.remote_address", "s.error",
            ),
            exactKeys = listOf("s.username", "s.external_id", "s.token_name"),
            // Every answer that was not a success: refused (token), wrong request, unknown endpoint, server error
            problemCondition = "s.http_status >= 400",
        )

    override fun load(ids: Collection<Long>): Map<Long, ServerEventResponse> =
        jdbcClient.sql(
            """
            SELECT s.id, s.occurred_at, s.caller_auth, s.token_id, s.token_name, s.token_prefix, s.method, s.path,
                   s.operation, s.sip_kind, s.external_id, s.username, s.http_status, s.duration_ms, s.remote_address,
                   s.error, a.id AS account_id
            FROM service_call_log s
            LEFT JOIN sip_account a ON a.tenant_id = s.tenant_id AND a.username = s.username
            WHERE s.tenant_id = :tenantId AND s.id IN (:ids)
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
                    occurredAt = resultSet.localDateTime("occurred_at")!!,
                    serviceCall = ServiceCallResponse(
                        callerAuth = ServiceCallerAuth.valueOf(resultSet.getString("caller_auth")),
                        tokenId = resultSet.nullableLong("token_id"),
                        tokenName = resultSet.getString("token_name"),
                        tokenPrefix = resultSet.getString("token_prefix"),
                        method = resultSet.getString("method"),
                        path = resultSet.getString("path"),
                        operation = ServiceCallOperation.valueOf(resultSet.getString("operation")),
                        sipKind = resultSet.getString("sip_kind"),
                        externalId = resultSet.getString("external_id"),
                        username = resultSet.getString("username"),
                        accountId = resultSet.nullableLong("account_id"),
                        httpStatus = resultSet.getInt("http_status"),
                        durationMs = resultSet.getInt("duration_ms"),
                        remoteAddress = resultSet.getString("remote_address"),
                        error = resultSet.getString("error"),
                    ),
                )
            }
            .list()
            .associateBy { it.id }
}
