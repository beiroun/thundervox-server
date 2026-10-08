// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.push.PushDeliveryOutcome
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** GET /integration: everything the Integration page shows except the token list and the delivery log. */
data class IntegrationResponse(
    /** Address the operator's backend reaches this server at; empty when the deployment did not say. */
    @JsonProperty("api_base_url") val apiBaseUrl: String,
    @JsonProperty("sip_domain") val sipDomain: String,
    @JsonProperty("push") val push: PushSettingsResponse,
)

data class PushSettingsResponse(
    @JsonProperty("enabled") val enabled: Boolean,
    @JsonProperty("url") val url: String,
    @JsonProperty("auth_header_name") val authHeaderName: String,
    /** Whether a header value is stored; the value itself is never returned. */
    @JsonProperty("auth_header_value_set") val authHeaderValueSet: Boolean,
    /** Last characters of the stored value, to recognise which secret is in place. */
    @JsonProperty("auth_header_value_hint") val authHeaderValueHint: String?,
    @JsonProperty("connect_timeout_ms") val connectTimeoutMs: Int,
    @JsonProperty("read_timeout_ms") val readTimeoutMs: Int,
    @JsonProperty("updated_at") val updatedAt: LocalDateTime,
    @JsonProperty("updated_by") val updatedBy: String?,
) {
    companion object {
        private const val HINT_LENGTH = 4

        fun from(settings: PushSettingsEntity) = PushSettingsResponse(
            enabled = settings.enabled,
            url = settings.url,
            authHeaderName = settings.authHeaderName,
            authHeaderValueSet = !settings.authHeaderValue.isNullOrEmpty(),
            authHeaderValueHint = settings.authHeaderValue?.takeIf { it.length > HINT_LENGTH }?.takeLast(HINT_LENGTH),
            connectTimeoutMs = settings.connectTimeoutMs,
            readTimeoutMs = settings.readTimeoutMs,
            updatedAt = settings.updatedAt,
            updatedBy = settings.updatedBy,
        )
    }
}

/** PUT /integration/push. auth_header_value absent or null = keep the stored secret, empty = clear it. */
data class UpdatePushSettingsRequest(
    @JsonProperty("enabled") val enabled: Boolean,
    @JsonProperty("url") val url: String,
    @JsonProperty("auth_header_name") val authHeaderName: String,
    @JsonProperty("auth_header_value") val authHeaderValue: String? = null,
    @JsonProperty("connect_timeout_ms") val connectTimeoutMs: Int = PushSettingsEntity.DEFAULT_CONNECT_TIMEOUT_MS,
    @JsonProperty("read_timeout_ms") val readTimeoutMs: Int = PushSettingsEntity.DEFAULT_READ_TIMEOUT_MS,
)

/** POST /integration/push/test: a panel calling an app client, by their numbers. */
data class PushTestRequest(
    @JsonProperty("caller_username") val callerUsername: String,
    @JsonProperty("callee_username") val calleeUsername: String,
)

data class PushTestResponse(
    @JsonProperty("call_id") val callId: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("outcome") val outcome: PushDeliveryOutcome,
    @JsonProperty("http_status") val httpStatus: Int?,
    @JsonProperty("attempts") val attempts: Int,
    @JsonProperty("duration_ms") val durationMs: Int,
    @JsonProperty("response_excerpt") val responseExcerpt: String?,
    @JsonProperty("error") val error: String?,
    /** The body that was sent, so the operator sees the contract filled in with real values. */
    @JsonProperty("sent_body") val sentBody: String,
)
