// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.UUID

/** One row of the delivery log, as the console shows it. */
data class PushDeliveryResponse(
    @JsonProperty("id") val id: Long,
    @JsonProperty("created_at") val createdAt: LocalDateTime,
    @JsonProperty("call_id") val callId: String,
    @JsonProperty("sip_call_id") val sipCallId: String?,
    @JsonProperty("kind") val kind: PushDeliveryKind,
    @JsonProperty("caller_number") val callerNumber: String?,
    @JsonProperty("caller_external_id") val callerExternalId: String?,
    @JsonProperty("callee_number") val calleeNumber: String?,
    @JsonProperty("callee_external_id") val calleeExternalId: String?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("outcome") val outcome: PushDeliveryOutcome,
    @JsonProperty("http_status") val httpStatus: Int?,
    @JsonProperty("attempts") val attempts: Int,
    @JsonProperty("duration_ms") val durationMs: Int?,
    @JsonProperty("response_excerpt") val responseExcerpt: String?,
    @JsonProperty("error") val error: String?,
)

data class PushDeliveryPageResponse(
    @JsonProperty("items") val items: List<PushDeliveryResponse>,
    @JsonProperty("total") val total: Long,
    @JsonProperty("page") val page: Int,
    @JsonProperty("size") val size: Int,
)

/**
 * Append-only log of wake pushes (push_delivery). Written outside any transaction of the caller: a live push is
 * recorded by the delivery worker, a skipped one right away, a test push on the request thread.
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

    fun page(page: Int, size: Int): PushDeliveryPageResponse {
        if (page < 0 || size !in 1..MAX_PAGE_SIZE) {
            throw WrongTypeException(
                "Push delivery page out of range: page=$page, size=$size",
                "Страница журнала доставок: номер от 0, размер от 1 до $MAX_PAGE_SIZE"
            )
        }
        val total = jdbcClient.sql("SELECT count(*) FROM push_delivery WHERE tenant_id = :tenantId")
            .param("tenantId", defaultTenant.id)
            .query(Long::class.javaObjectType)
            .single()
        val items = jdbcClient.sql(
            """
            SELECT id, created_at, call_id, sip_call_id, kind, caller_number, caller_external_id, callee_number,
                   callee_external_id, url, outcome, http_status, attempts, duration_ms, response_excerpt, error
            FROM push_delivery
            WHERE tenant_id = :tenantId
            ORDER BY created_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("limit", size)
            .param("offset", page.toLong() * size)
            .query { resultSet, _ ->
                PushDeliveryResponse(
                    id = resultSet.getLong("id"),
                    createdAt = resultSet.getObject("created_at", LocalDateTime::class.java),
                    callId = resultSet.getObject("call_id", UUID::class.java).toString(),
                    sipCallId = resultSet.getString("sip_call_id"),
                    kind = PushDeliveryKind.valueOf(resultSet.getString("kind")),
                    callerNumber = resultSet.getString("caller_number"),
                    callerExternalId = resultSet.getString("caller_external_id"),
                    calleeNumber = resultSet.getString("callee_number"),
                    calleeExternalId = resultSet.getString("callee_external_id"),
                    url = resultSet.getString("url"),
                    outcome = PushDeliveryOutcome.valueOf(resultSet.getString("outcome")),
                    httpStatus = resultSet.getObject("http_status", Int::class.javaObjectType),
                    attempts = resultSet.getInt("attempts"),
                    durationMs = resultSet.getObject("duration_ms", Int::class.javaObjectType),
                    responseExcerpt = resultSet.getString("response_excerpt"),
                    error = resultSet.getString("error"),
                )
            }
            .list()
        return PushDeliveryPageResponse(items = items, total = total, page = page, size = size)
    }

    /** Rows older than the cut-off are dropped; returns how many. */
    fun deleteOlderThan(cutoff: LocalDateTime): Int =
        jdbcClient.sql("DELETE FROM push_delivery WHERE created_at < :cutoff")
            .param("cutoff", cutoff)
            .update()

    companion object {
        const val MAX_PAGE_SIZE = 200
        private const val SIP_CALL_ID_LENGTH = 256
        private const val URL_LENGTH = 512
    }
}
