// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.registration.RegistrationEventKind
import com.ef_softworks.thundervox_server.search.SmartQuery
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** Numbers coming online and going offline (registration_event), with the account behind the number. */
@Component
class RegistrationEventSource(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) : ServerEventSource {

    override val category = ServerEventCategory.REGISTRATION

    override val administratorsOnly = false

    // A number going offline is the normal life of a phone, not a failure: nothing here for "problems only"
    override fun select(query: SmartQuery?, problemsOnly: Boolean): SourceSelect? {
        if (problemsOnly) {
            return null
        }
        return sourceSelect(
            category = category,
            from = "registration_event e LEFT JOIN sip_account a ON a.tenant_id = e.tenant_id AND a.username = e.username",
            alias = "e",
            timeColumn = "occurred_at",
            query = query,
            searchableFields = listOf("e.username", "e.kind", "e.contact", "e.received", "e.user_agent", "a.name", "a.external_id"),
            exactKeys = listOf("e.username", "a.external_id"),
            problemCondition = null,
        )
    }

    override fun load(ids: Collection<Long>): Map<Long, ServerEventResponse> =
        jdbcClient.sql(
            """
            SELECT e.id, e.occurred_at, e.username, e.kind, e.contact, e.received, e.user_agent,
                   a.id AS account_id, a.kind AS account_kind, a.name AS account_name, a.external_id AS account_external_id
            FROM registration_event e
            LEFT JOIN sip_account a ON a.tenant_id = e.tenant_id AND a.username = e.username
            WHERE e.tenant_id = :tenantId AND e.id IN (:ids)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("ids", ids)
            .query { resultSet, _ ->
                val id = resultSet.getLong("id")
                val account = resultSet.nullableLong("account_id")?.let { accountId ->
                    EventSipAccountResponse(
                        id = accountId,
                        kind = SipAccountKind.valueOf(resultSet.getString("account_kind")),
                        name = resultSet.getString("account_name"),
                        externalId = resultSet.getString("account_external_id"),
                    )
                }
                ServerEventResponse(
                    key = ServerEventResponse.keyOf(category, id),
                    category = category,
                    id = id,
                    occurredAt = resultSet.localDateTime("occurred_at")!!,
                    registration = RegistrationEventResponse(
                        username = resultSet.getString("username"),
                        kind = RegistrationEventKind.valueOf(resultSet.getString("kind")),
                        contact = resultSet.getString("contact"),
                        received = resultSet.getString("received"),
                        userAgent = resultSet.getString("user_agent"),
                        account = account,
                    ),
                )
            }
            .list()
            .associateBy { it.id }
}
