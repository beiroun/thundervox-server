// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/** Result of delivering one push, as the delivery log records it. */
data class PushDeliveryResult(
    val outcome: PushDeliveryOutcome,
    val httpStatus: Int?,
    val attempts: Int,
    val durationMs: Int,
    val responseExcerpt: String?,
    val error: String?,
)

/**
 * The HTTP call to the operator's backend. Blocking by design: it runs on the delivery worker (live pushes) or on
 * the request thread of a test push, never on the core's wake request.
 *
 * One retry after a short pause, and only when a retry can change anything: a network error, a timeout or a 5xx.
 * A 4xx is the operator's verdict on the request and is recorded as REJECTED at once.
 */
@Component
class PushDeliveryClient(
    private val jsonMapper: JsonMapper,
    buildProperties: ObjectProvider<BuildProperties>,
) {

    private val userAgent = "thundervox-server/${buildProperties.ifAvailable?.version ?: "dev"}"

    fun deliver(target: PushTarget, payload: WakePushPayload): PushDeliveryResult {
        val body = jsonMapper.writeValueAsString(payload)
        val started = System.nanoTime()
        var attempts = 0
        var attempt: Attempt
        while (true) {
            attempts++
            attempt = attempt(target, body)
            if (attempt.retryable && attempts < MAX_ATTEMPTS) {
                Thread.sleep(RETRY_DELAY_MS)
                continue
            }
            break
        }
        val durationMs = ((System.nanoTime() - started) / 1_000_000L).toInt()
        val outcome = when {
            attempt.status != null && attempt.status in 200..299 -> PushDeliveryOutcome.DELIVERED
            attempt.status != null && attempt.status in 400..499 -> PushDeliveryOutcome.REJECTED
            else -> PushDeliveryOutcome.FAILED
        }
        if (outcome != PushDeliveryOutcome.DELIVERED) {
            // Data-loss point: the device will not wake; the delivery log keeps the same facts for the console
            logWarn("Push to ${target.url} ${outcome.name.lowercase()}: status=${attempt.status}, attempts=$attempts, error=${attempt.error}")
        }
        return PushDeliveryResult(outcome, attempt.status, attempts, durationMs, attempt.body, attempt.error)
    }

    private fun attempt(target: PushTarget, body: String): Attempt {
        val restClient = RestClient.builder().requestFactory(requestFactory(target)).build()
        return try {
            restClient.post()
                .uri(target.url)
                .headers { headers ->
                    headers.set(HttpHeaders.USER_AGENT, userAgent)
                    if (!target.authHeaderValue.isNullOrEmpty()) {
                        headers.set(target.authHeaderName, target.authHeaderValue)
                    }
                }
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .exchange { _, response ->
                    val status = response.statusCode.value()
                    val text = runCatching { response.bodyTo(String::class.java) }.getOrNull()
                    Attempt(status = status, body = text?.take(EXCERPT_LENGTH)?.takeIf { it.isNotBlank() }, error = null)
                }
        } catch (ex: RestClientException) {
            Attempt(status = null, body = null, error = (ex.message ?: ex.javaClass.simpleName).take(EXCERPT_LENGTH))
        }
    }

    private fun requestFactory(target: PushTarget): JdkClientHttpRequestFactory {
        val httpClient = HttpClient.newBuilder()
            .connectTimeout(target.connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
        return JdkClientHttpRequestFactory(httpClient).apply { setReadTimeout(target.readTimeout) }
    }

    private data class Attempt(val status: Int?, val body: String?, val error: String?) {
        val retryable: Boolean get() = status == null || status >= 500
    }

    companion object {
        const val EXCERPT_LENGTH = 512
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 500L
    }
}
