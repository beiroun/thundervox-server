// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.calls

import com.ef_softworks.thundervox_server.sipaccount.SipAccountEntity
import com.ef_softworks.thundervox_server.sipaccount.SipAccountRepository
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logDebug
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * The call journal (call_log): one row per SIP Call-ID, built up by the three events of a call. Events are applied
 * one at a time by [CallEventQueue], in the order they arrived, so an "ended" never races its own "started"; an
 * event for a call the journal does not know (the server was restarted mid-call, the core came up first) still
 * gets its row, with what is known.
 */
@Component
class CallLog(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
    private val sipAccountRepository: SipAccountRepository,
) {

    @Transactional
    fun apply(event: CallEvent, at: LocalDateTime) {
        when (event.kind) {
            CallEventKind.STARTED -> started(event, at)
            CallEventKind.ANSWERED -> answered(event, at)
            CallEventKind.ENDED -> ended(event, at)
        }
    }

    private fun started(event: CallEvent, at: LocalDateTime) {
        val caller = findByNumber(event.callerNumber)
        val callee = findByNumber(event.calleeNumber)
        jdbcClient.sql(
            """
            INSERT INTO call_log (tenant_id, sip_call_id, mode, caller_number, caller_external_id, caller_name,
                                  callee_number, callee_external_id, callee_name, started_at, outcome)
            VALUES (:tenantId, :sipCallId, :mode, :callerNumber, :callerExternalId, :callerName,
                    :calleeNumber, :calleeExternalId, :calleeName, :startedAt, 'RINGING')
            ON CONFLICT (tenant_id, sip_call_id) DO UPDATE SET
                mode = COALESCE(call_log.mode, EXCLUDED.mode),
                caller_number = COALESCE(call_log.caller_number, EXCLUDED.caller_number),
                caller_external_id = COALESCE(call_log.caller_external_id, EXCLUDED.caller_external_id),
                caller_name = COALESCE(call_log.caller_name, EXCLUDED.caller_name),
                callee_number = COALESCE(call_log.callee_number, EXCLUDED.callee_number),
                callee_external_id = COALESCE(call_log.callee_external_id, EXCLUDED.callee_external_id),
                callee_name = COALESCE(call_log.callee_name, EXCLUDED.callee_name)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("sipCallId", event.sipCallId.take(SIP_CALL_ID_LENGTH))
            .param("mode", event.mode?.name)
            .param("callerNumber", event.callerNumber?.take(NUMBER_LENGTH))
            .param("callerExternalId", caller?.externalId)
            .param("callerName", caller?.name?.take(NAME_LENGTH))
            .param("calleeNumber", event.calleeNumber?.take(NUMBER_LENGTH))
            .param("calleeExternalId", callee?.externalId)
            .param("calleeName", callee?.name?.take(NAME_LENGTH))
            .param("startedAt", at)
            .update()
        logDebug("Call ${event.sipCallId} started: ${event.callerNumber} -> ${event.calleeNumber} (${event.mode})")
    }

    private fun answered(event: CallEvent, at: LocalDateTime) {
        val updated = jdbcClient.sql(
            """
            UPDATE call_log
            SET answered_at = COALESCE(answered_at, :at),
                outcome = CASE WHEN ended_at IS NULL THEN 'ANSWERED' ELSE outcome END
            WHERE tenant_id = :tenantId AND sip_call_id = :sipCallId
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("sipCallId", event.sipCallId.take(SIP_CALL_ID_LENGTH))
            .param("at", at)
            .update()
        if (updated == 0) {
            logWarn("Call ${event.sipCallId} answered but never started in the journal; recording it from here")
            insertUnknownCall(event, at, answeredAt = at, endedAt = null, outcome = CallOutcome.ANSWERED)
        }
    }

    private fun ended(event: CallEvent, at: LocalDateTime) {
        val answeredAt = jdbcClient.sql(
            "SELECT answered_at FROM call_log WHERE tenant_id = :tenantId AND sip_call_id = :sipCallId"
        )
            .param("tenantId", defaultTenant.id)
            .param("sipCallId", event.sipCallId.take(SIP_CALL_ID_LENGTH))
            .query { resultSet, _ -> AnsweredAt(resultSet.getObject("answered_at", LocalDateTime::class.java)) }
            .optional()
            .orElse(null)
        val outcome = outcomeOf(event, answeredAt?.value != null)
        if (answeredAt == null) {
            logWarn("Call ${event.sipCallId} ended (${event.reason} ${event.code}) but never started in the journal; recording it from here")
            insertUnknownCall(event, at, answeredAt = null, endedAt = at, outcome = outcome)
            return
        }
        jdbcClient.sql(
            """
            UPDATE call_log
            SET ended_at = COALESCE(ended_at, :at), end_reason = :reason, end_code = :code, outcome = :outcome
            WHERE tenant_id = :tenantId AND sip_call_id = :sipCallId
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("sipCallId", event.sipCallId.take(SIP_CALL_ID_LENGTH))
            .param("at", at)
            .param("reason", event.reason?.take(REASON_LENGTH))
            .param("code", event.code)
            .param("outcome", outcome.name)
            .update()
        logDebug("Call ${event.sipCallId} ended: $outcome (${event.reason} ${event.code})")
    }

    private fun insertUnknownCall(event: CallEvent, at: LocalDateTime, answeredAt: LocalDateTime?, endedAt: LocalDateTime?, outcome: CallOutcome) {
        jdbcClient.sql(
            """
            INSERT INTO call_log (tenant_id, sip_call_id, started_at, answered_at, ended_at, end_reason, end_code, outcome)
            VALUES (:tenantId, :sipCallId, :at, :answeredAt, :endedAt, :reason, :code, :outcome)
            ON CONFLICT (tenant_id, sip_call_id) DO NOTHING
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("sipCallId", event.sipCallId.take(SIP_CALL_ID_LENGTH))
            .param("at", at)
            .param("answeredAt", answeredAt)
            .param("endedAt", endedAt)
            .param("reason", event.reason?.take(REASON_LENGTH))
            .param("code", event.code)
            .param("outcome", outcome.name)
            .update()
    }

    /**
     * What the end of a call means. A BYE always ends an established call, so it counts as answered even when the
     * "answered" event got lost; the rest is read off the reason the core gives and the final SIP code.
     */
    private fun outcomeOf(event: CallEvent, wasAnswered: Boolean): CallOutcome = when {
        wasAnswered || event.reason == "bye" -> CallOutcome.ANSWERED
        event.reason == "cancel" -> CallOutcome.CANCELED
        event.reason == "window" -> CallOutcome.NO_ANSWER
        event.code in NO_ANSWER_CODES -> CallOutcome.NO_ANSWER
        event.code in REJECTED_CODES -> CallOutcome.REJECTED
        else -> CallOutcome.FAILED
    }

    // The core sends "<null>" for an unset variable: not a number, not an error
    private fun findByNumber(number: String?): SipAccountEntity? =
        number?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let(sipAccountRepository::findByUsername)

    fun deleteOlderThan(cutoff: LocalDateTime): Int =
        jdbcClient.sql("DELETE FROM call_log WHERE started_at < :cutoff").param("cutoff", cutoff).update()

    private data class AnsweredAt(val value: LocalDateTime?)

    companion object {
        private const val SIP_CALL_ID_LENGTH = 256
        private const val NUMBER_LENGTH = 64
        private const val NAME_LENGTH = 128
        private const val REASON_LENGTH = 16
        private val NO_ANSWER_CODES = setOf(408, 480)
        private val REJECTED_CODES = setOf(486, 600, 603, 607)
    }
}
