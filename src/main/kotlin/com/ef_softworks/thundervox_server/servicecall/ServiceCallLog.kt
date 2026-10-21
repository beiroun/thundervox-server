// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicecall

import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logError
import jakarta.annotation.PreDestroy
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The journal of service API calls (service_call_log). Written off the request thread: the operator's backend gets
 * its answer without waiting for the journal, and a flood of refused calls costs the database one writer thread,
 * not one connection per request.
 *
 * A full queue means the database is far behind (or someone is hammering the API); the call is dropped from the
 * journal and logged rather than slowing the API down.
 */
@Component
class ServiceCallLog(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) {

    private val executor = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUE_CAPACITY),
        { runnable -> Thread(runnable, "service-call-log").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    /** Queues the call for the journal; false when the queue is full (the row is lost). */
    fun submit(call: ServiceCall): Boolean =
        try {
            executor.execute { recordSafely(call) }
            true
        } catch (ex: RejectedExecutionException) {
            // Data-loss point: the event log misses this call
            logError("Service call log queue is full ($QUEUE_CAPACITY): ${call.method} ${call.path} -> ${call.httpStatus} not recorded")
            false
        }

    private fun recordSafely(call: ServiceCall) {
        try {
            record(call)
        } catch (ex: Exception) {
            // Data-loss point: the event log misses this call
            logError("Service call ${call.method} ${call.path} -> ${call.httpStatus} could not be recorded", ex)
        }
    }

    private fun record(call: ServiceCall) {
        jdbcClient.sql(
            """
            INSERT INTO service_call_log (tenant_id, occurred_at, caller_auth, token_id, token_name, token_prefix, method,
                                          path, operation, sip_kind, external_id, username, http_status, duration_ms,
                                          remote_address, error)
            VALUES (:tenantId, :occurredAt, :callerAuth, :tokenId, :tokenName, :tokenPrefix, :method,
                    :path, :operation, :sipKind, :externalId, :username, :httpStatus, :durationMs,
                    :remoteAddress, :error)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("occurredAt", call.occurredAt)
            .param("callerAuth", call.caller.auth.name)
            .param("tokenId", call.caller.tokenId)
            .param("tokenName", call.caller.tokenName?.take(TOKEN_NAME_LENGTH))
            .param("tokenPrefix", call.caller.tokenPrefix?.take(TOKEN_PREFIX_LENGTH))
            .param("method", call.method.take(METHOD_LENGTH))
            .param("path", call.path.take(PATH_LENGTH))
            .param("operation", call.target.operation.name)
            .param("sipKind", call.target.sipKind?.take(SIP_KIND_LENGTH))
            .param("externalId", call.target.externalId?.take(EXTERNAL_ID_LENGTH))
            .param("username", numberOf(call.target))
            .param("httpStatus", call.httpStatus)
            .param("durationMs", call.durationMs)
            .param("remoteAddress", call.remoteAddress?.take(REMOTE_ADDRESS_LENGTH))
            .param("error", call.error?.take(ERROR_LENGTH))
            .update()
    }

    // Looked up after the call, so a number the call has just created is already there and the row can be found
    // by it; an endpoint without a number (a typo in the id, a 404) stays without one
    private fun numberOf(target: ServiceCallTarget): String? {
        val kind = target.sipKind ?: return null
        val externalId = target.externalId ?: return null
        return jdbcClient.sql(
            "SELECT username FROM sip_account WHERE tenant_id = :tenantId AND kind = :kind AND external_id = :externalId"
        )
            .param("tenantId", defaultTenant.id)
            .param("kind", kind)
            .param("externalId", externalId)
            .query(String::class.java)
            .optional()
            .orElse(null)
    }

    /** Rows older than the cut-off are dropped; returns how many. */
    fun deleteOlderThan(cutoff: LocalDateTime): Int =
        jdbcClient.sql("DELETE FROM service_call_log WHERE occurred_at < :cutoff").param("cutoff", cutoff).update()

    @PreDestroy
    fun shutdown() {
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        private const val QUEUE_CAPACITY = 1000
        private const val TOKEN_NAME_LENGTH = 64
        private const val TOKEN_PREFIX_LENGTH = 16
        private const val METHOD_LENGTH = 8
        private const val PATH_LENGTH = 512
        private const val SIP_KIND_LENGTH = 8
        private const val EXTERNAL_ID_LENGTH = 128
        private const val REMOTE_ADDRESS_LENGTH = 64
        private const val ERROR_LENGTH = 512
    }
}
