// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Append-only log of wake pushes (push_delivery). Written outside any transaction of the caller: a live push is
 * recorded by the delivery worker, a skipped one right away, a test push on the request thread. The console reads
 * it as the pushes of the event log (events.PushDeliverySource).
 */
@Component
class PushDeliveryLog(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) {

    fun record(kind: PushDeliveryKind, payload: WakePushPayload, url: String?, result: PushDeliveryResult) {
        jdbcClient.sql(
            """
            INSERT INTO push_delivery (tenant_id, call_id, sip_call_id, kind, caller_number, caller_external_id,
                                       callee_number, callee_external_id, url, outcome, http_status, attempts,
                                       duration_ms, response_excerpt, error)
            VALUES (:tenantId, CAST(:callId AS uuid), :sipCallId, :kind, :callerNumber, :callerExternalId,
                    :calleeNumber, :calleeExternalId, :url, :outcome, :httpStatus, :attempts,
                    :durationMs, :responseExcerpt, :error)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("callId", payload.callId)
            .param("sipCallId", payload.sipCallId?.take(SIP_CALL_ID_LENGTH))
            .param("kind", kind.name)
            .param("callerNumber", payload.callerNumber)
            .param("callerExternalId", payload.callerId)
            .param("calleeNumber", payload.calleeNumber)
            .param("calleeExternalId", payload.calleeId)
            .param("url", url?.take(URL_LENGTH))
            .param("outcome", result.outcome.name)
            .param("httpStatus", result.httpStatus)
            .param("attempts", result.attempts)
            .param("durationMs", result.durationMs)
            .param("responseExcerpt", result.responseExcerpt?.take(PushDeliveryClient.EXCERPT_LENGTH))
            .param("error", result.error?.take(PushDeliveryClient.EXCERPT_LENGTH))
            .update()
    }

    /** Rows older than the cut-off are dropped; returns how many. */
    fun deleteOlderThan(cutoff: LocalDateTime): Int =
        jdbcClient.sql("DELETE FROM push_delivery WHERE created_at < :cutoff")
            .param("cutoff", cutoff)
            .update()

    companion object {
        private const val SIP_CALL_ID_LENGTH = 256
        private const val URL_LENGTH = 512
    }
}
