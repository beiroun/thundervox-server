// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicecall

import com.ef_softworks.thundervox_server.util.logDebug
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingResponseWrapper
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Records every request to the service API (paths under /service/) in the call journal: which backend (the token
 * the service token filter resolved), what it asked for, the status it got, how long it took, and the error when
 * it failed.
 *
 * Sits in the security chain before the service token filter, so refusals (no token, an unknown or revoked one)
 * are recorded too - a backend still configured with a revoked token is exactly what the event log has to show.
 * The response is buffered to read the error of a failed call; the body of a success is never read: it may carry
 * a freshly issued SIP password. Not a Spring bean, like the other filters of the chain.
 */
class ServiceCallJournalFilter(
    private val serviceCallLog: ServiceCallLog,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("${request.contextPath}/service/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val occurredAt = LocalDateTime.now()
        val startedAt = System.nanoTime()
        val bufferedResponse = ContentCachingResponseWrapper(response)
        var failure: Exception? = null
        try {
            filterChain.doFilter(request, bufferedResponse)
        } catch (ex: Exception) {
            failure = ex
            throw ex
        } finally {
            // An exception that escaped every handler becomes a 500 at the container; the journal says so too
            val status = if (failure != null) HttpServletResponse.SC_INTERNAL_SERVER_ERROR else bufferedResponse.status
            val error = if (failure != null) failure.javaClass.simpleName else errorOf(status, bufferedResponse.contentAsByteArray)
            bufferedResponse.copyBodyToResponse()
            val path = request.requestURI.removePrefix(request.contextPath)
            val call = ServiceCall(
                occurredAt = occurredAt,
                caller = request.getAttribute(ServiceCaller.ATTRIBUTE) as? ServiceCaller ?: ServiceCaller.NO_TOKEN,
                method = request.method,
                path = path,
                target = ServiceCallTarget.of(request.method, path),
                httpStatus = status,
                durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt).toInt(),
                remoteAddress = request.remoteAddr,
                error = error,
            )
            logDebug("Service call ${call.method} ${call.path} by ${call.caller.tokenName ?: call.caller.auth} -> $status in ${call.durationMs} ms")
            serviceCallLog.submit(call)
        }
    }

    // Every error of the API is the envelope { error: { message, localizedMessage } }; the technical message names
    // what was wrong (the id, the field), which is what the operator needs to fix the backend's request
    private fun errorOf(status: Int, body: ByteArray): String? {
        if (status < HttpServletResponse.SC_BAD_REQUEST) {
            return null
        }
        if (body.isEmpty()) {
            return "HTTP $status"
        }
        return try {
            jsonMapper.readValue(body, ErrorEnvelope::class.java).error?.message ?: "HTTP $status"
        } catch (ex: Exception) {
            String(body, Charsets.UTF_8).take(ERROR_EXCERPT_LENGTH)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ErrorEnvelope(@JsonProperty("error") val error: ErrorBody? = null)

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class ErrorBody(@JsonProperty("message") val message: String? = null)

    companion object {
        private const val ERROR_EXCERPT_LENGTH = 512
    }
}
