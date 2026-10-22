// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.calls.CallMode
import com.ef_softworks.thundervox_server.calls.CallOutcome
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * Calls the core saw (call_log), one line per call at the moment it started; the line follows the call to its end
 * (RINGING until the core reports it), so the log shows a call once, not its three events.
 */
@Component
class CallEventSource(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) : ServerEventSource {

    override val category = ServerEventCategory.CALL

    override val administratorsOnly = false

    override fun select(selection: EventSelection): SourceSelect =
        sourceSelect(
            category = category,
            from = "call_log c",
            alias = "c",
            timeColumn = "started_at",
            selection = selection,
            searchableFields = listOf(
                "c.sip_call_id", "c.mode", "c.caller_number", "c.caller_external_id", "c.caller_name",
                "c.callee_number", "c.callee_external_id", "c.callee_name", "c.outcome", "c.end_reason", "c.end_code::text",
            ),
            exactKeys = listOf("c.caller_number", "c.callee_number", "c.sip_call_id", "c.caller_external_id", "c.callee_external_id"),
            // Did not get through: nobody answered (the wake window closed, the phone never woke) or the call broke.
            // A cancel is the caller's own choice and a reject is the callee's answer - both reached somebody
            problemCondition = "c.outcome IN ('${CallOutcome.NO_ANSWER.name}', '${CallOutcome.FAILED.name}')",
        )

    override fun load(ids: Collection<Long>): Map<Long, ServerEventResponse> =
        jdbcClient.sql(
            """
            SELECT c.id, c.sip_call_id, c.mode, c.caller_number, c.caller_external_id, c.caller_name, c.callee_number,
                   c.callee_external_id, c.callee_name, c.started_at, c.answered_at, c.ended_at, c.end_reason, c.end_code,
                   c.outcome, ca.id AS caller_account_id, ce.id AS callee_account_id, woke.occurred_at AS callee_registered_at
            FROM call_log c
            LEFT JOIN sip_account ca ON ca.tenant_id = c.tenant_id AND ca.username = c.caller_number
            LEFT JOIN sip_account ce ON ce.tenant_id = c.tenant_id AND ce.username = c.callee_number
            -- The first time the callee came online while its parked call waited: the wake push reached the phone.
            -- Bounded by the call's end, or by five minutes for a call still ringing or whose end never came (no
            -- now() here: the journals hold the server's local time without a zone)
            LEFT JOIN LATERAL (
                SELECT r.occurred_at
                FROM registration_event r
                WHERE c.mode = 'WAKE' AND r.tenant_id = c.tenant_id AND r.username = c.callee_number AND r.kind = 'ONLINE'
                  AND r.occurred_at >= c.started_at
                  AND r.occurred_at <= COALESCE(c.ended_at, c.started_at + interval '5 minutes')
                ORDER BY r.occurred_at
                LIMIT 1
            ) woke ON TRUE
            WHERE c.tenant_id = :tenantId AND c.id IN (:ids)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("ids", ids)
            .query { resultSet, _ ->
                val id = resultSet.getLong("id")
                val startedAt = resultSet.localDateTime("started_at")!!
                ServerEventResponse(
                    key = ServerEventResponse.keyOf(category, id),
                    category = category,
                    id = id,
                    occurredAt = startedAt,
                    call = CallEventResponse(
                        sipCallId = resultSet.getString("sip_call_id"),
                        mode = resultSet.getString("mode")?.let(CallMode::valueOf),
                        callerNumber = resultSet.getString("caller_number"),
                        callerExternalId = resultSet.getString("caller_external_id"),
                        callerName = resultSet.getString("caller_name"),
                        callerAccountId = resultSet.nullableLong("caller_account_id"),
                        calleeNumber = resultSet.getString("callee_number"),
                        calleeExternalId = resultSet.getString("callee_external_id"),
                        calleeName = resultSet.getString("callee_name"),
                        calleeAccountId = resultSet.nullableLong("callee_account_id"),
                        startedAt = startedAt,
                        answeredAt = resultSet.localDateTime("answered_at"),
                        endedAt = resultSet.localDateTime("ended_at"),
                        endReason = resultSet.getString("end_reason"),
                        endCode = resultSet.nullableInt("end_code"),
                        outcome = CallOutcome.valueOf(resultSet.getString("outcome")),
                        calleeRegisteredAt = resultSet.localDateTime("callee_registered_at"),
                    ),
                )
            }
            .list()
            .associateBy { it.id }
}
