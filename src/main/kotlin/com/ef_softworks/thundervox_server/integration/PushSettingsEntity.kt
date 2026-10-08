// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.push.PushTarget
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Duration
import java.time.LocalDateTime

/**
 * Where the wake push goes (one row per tenant, seeded switched off by V6). [authHeaderValue] is the operator
 * backend's secret: written through the API, never read back by it.
 */
@Entity
@Table(name = "integration_push_settings")
class PushSettingsEntity(
    @Id
    @Column(name = "tenant_id") var tenantId: Long,
    @Column(name = "enabled") var enabled: Boolean = false,
    @Column(name = "url") var url: String = "",
    @Column(name = "auth_header_name") var authHeaderName: String = DEFAULT_AUTH_HEADER_NAME,
    @Column(name = "auth_header_value") var authHeaderValue: String? = null,
    @Column(name = "connect_timeout_ms") var connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
    @Column(name = "read_timeout_ms") var readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
    @Column(name = "updated_at") var updatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "updated_by") var updatedBy: String? = null,
) {
    fun toTarget() = PushTarget(
        url = url,
        authHeaderName = authHeaderName,
        authHeaderValue = authHeaderValue,
        connectTimeout = Duration.ofMillis(connectTimeoutMs.toLong()),
        readTimeout = Duration.ofMillis(readTimeoutMs.toLong()),
    )

    companion object {
        const val DEFAULT_AUTH_HEADER_NAME = "X-SERVICE-TOKEN"
        const val DEFAULT_CONNECT_TIMEOUT_MS = 2000
        const val DEFAULT_READ_TIMEOUT_MS = 3000
    }
}
