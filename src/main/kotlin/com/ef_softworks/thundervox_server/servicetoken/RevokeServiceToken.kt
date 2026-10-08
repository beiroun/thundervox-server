// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** Revokes a token: the next call with it is refused, the row stays for the audit trail. Idempotent. */
@Component
class RevokeServiceToken(
    private val serviceTokenRepository: ServiceTokenRepository,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun revoke(id: Long): ServiceTokenResponse {
        val actor = requireSuperAdministrator()
        val token = serviceTokenRepository.findById(id)
            .orElseThrow { NotFoundException("Service token $id not found", "Токен не найден") }
        if (token.enabled) {
            token.enabled = false
            token.revokedAt = LocalDateTime.now()
            serviceTokenRepository.save(token)
            auditLog.record(
                actor.auditActor, AuditAction.SERVICE_TOKEN_REVOKED, AuditTargetType.SERVICE_TOKEN, token.id,
                mapOf("name" to token.name, "token_prefix" to token.tokenPrefix)
            )
            logInfo("Service token revoked: name=${token.name}, prefix=${token.tokenPrefix}, by=${actor.login}")
        }
        return ServiceTokenResponse.from(token)
    }
}
