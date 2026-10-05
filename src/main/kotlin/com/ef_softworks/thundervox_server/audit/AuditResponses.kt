// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.audit

import com.fasterxml.jackson.annotation.JsonProperty
import tools.jackson.databind.JsonNode
import java.time.LocalDateTime

data class AuditEntryResponse(
    @JsonProperty("id") val id: Long,
    @JsonProperty("created_at") val createdAt: LocalDateTime,
    @JsonProperty("actor_type") val actorType: String,
    @JsonProperty("actor_login") val actorLogin: String,
    @JsonProperty("action") val action: String,
    @JsonProperty("target_type") val targetType: String,
    @JsonProperty("target_id") val targetId: Long?,
    @JsonProperty("details") val details: JsonNode?,
)

data class AuditPageResponse(
    @JsonProperty("items") val items: List<AuditEntryResponse>,
    @JsonProperty("total") val total: Long,
    @JsonProperty("page") val page: Int,
    @JsonProperty("size") val size: Int,
)
