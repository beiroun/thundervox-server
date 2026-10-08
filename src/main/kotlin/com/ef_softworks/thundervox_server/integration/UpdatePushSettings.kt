// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.servicetoken.requireSuperAdministrator
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** Changes where the wake push goes. Super administrator only; the secret is written, never echoed, not even to the audit trail. */
@Component
class UpdatePushSettings(
    private val pushSettings: PushSettings,
    private val pushSettingsRepository: PushSettingsRepository,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun update(request: UpdatePushSettingsRequest): PushSettingsResponse {
        val actor = requireSuperAdministrator()
        val settings = pushSettings.current()

        settings.enabled = request.enabled
        settings.url = PushSettingsInputRules.requireValidUrl(request.url)
        settings.authHeaderName = PushSettingsInputRules.requireValidHeaderName(request.authHeaderName)
        val headerValue = PushSettingsInputRules.requireValidHeaderValue(request.authHeaderValue)
        val secretChange = when {
            headerValue == null -> "unchanged"
            headerValue.isEmpty() -> "cleared"
            else -> "set"
        }
        if (headerValue != null) {
            settings.authHeaderValue = headerValue.takeIf { it.isNotEmpty() }
        }
        settings.connectTimeoutMs = PushSettingsInputRules.requireValidTimeout(request.connectTimeoutMs, "connect")
        settings.readTimeoutMs = PushSettingsInputRules.requireValidTimeout(request.readTimeoutMs, "read")
        settings.updatedAt = LocalDateTime.now()
        settings.updatedBy = actor.login
        pushSettingsRepository.save(settings)

        auditLog.record(
            actor.auditActor, AuditAction.PUSH_SETTINGS_UPDATED, AuditTargetType.PUSH_SETTINGS, settings.tenantId,
            mapOf(
                "enabled" to settings.enabled,
                "url" to settings.url,
                "auth_header_name" to settings.authHeaderName,
                "auth_header_value" to secretChange,
                "connect_timeout_ms" to settings.connectTimeoutMs,
                "read_timeout_ms" to settings.readTimeoutMs,
            )
        )
        logInfo("Push settings updated: enabled=${settings.enabled}, url=${settings.url}, header=${settings.authHeaderName} ($secretChange), by=${actor.login}")
        return PushSettingsResponse.from(settings)
    }
}
