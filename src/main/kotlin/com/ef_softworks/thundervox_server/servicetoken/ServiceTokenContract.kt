// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class ServiceTokenResponse(
    @JsonProperty("id") val id: Long,
    @JsonProperty("name") val name: String,
    /** First characters of the value, enough to recognise it in a config file; the value itself is never returned. */
    @JsonProperty("token_prefix") val tokenPrefix: String,
    @JsonProperty("enabled") val enabled: Boolean,
    @JsonProperty("created_by") val createdBy: String,
    @JsonProperty("created_at") val createdAt: LocalDateTime,
    /** Updated at most once a minute while the token is in use; null until its first call. */
    @JsonProperty("last_used_at") val lastUsedAt: LocalDateTime?,
    @JsonProperty("revoked_at") val revokedAt: LocalDateTime?,
) {
    companion object {
        fun from(token: ServiceTokenEntity) = ServiceTokenResponse(
            id = token.id,
            name = token.name,
            tokenPrefix = token.tokenPrefix,
            enabled = token.enabled,
            createdBy = token.createdBy,
            createdAt = token.createdAt,
            lastUsedAt = token.lastUsedAt,
            revokedAt = token.revokedAt,
        )
    }
}

data class IssueServiceTokenRequest(
    @JsonProperty("name") val name: String,
)

/** The only moment the value exists outside the integrator's config: the server keeps its hash. */
data class IssuedServiceTokenResponse(
    @JsonProperty("token") val token: ServiceTokenResponse,
    @JsonProperty("value") val value: String,
)
