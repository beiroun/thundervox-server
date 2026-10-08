// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.consoleuser.ConsoleRole
import com.ef_softworks.thundervox_server.consoleuser.currentConsoleUser
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Issues a service token. Only the super administrator may: a token opens every endpoint the operator's backend has. */
@Component
class IssueServiceToken(
    private val serviceTokenRepository: ServiceTokenRepository,
    private val defaultTenant: DefaultTenant,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun issue(request: IssueServiceTokenRequest): IssuedServiceTokenResponse {
        val actor = requireSuperAdministrator()
        val name = ServiceTokenInputRules.requireValidName(request.name)
        if (serviceTokenRepository.existsByTenantIdAndNameAndEnabledTrue(defaultTenant.id, name)) {
            throw nameTaken(name)
        }
        val value = ServiceTokenValue.generate()
        val token = try {
            serviceTokenRepository.saveAndFlush(
                ServiceTokenEntity(
                    tenantId = defaultTenant.id,
                    name = name,
                    tokenHash = ServiceTokenValue.hash(value),
                    tokenPrefix = ServiceTokenValue.visiblePrefix(value),
                    createdBy = actor.login,
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            // Two super administrators cannot exist, but the unique index is still the authority on names
            throw nameTaken(name)
        }

        auditLog.record(
            actor.auditActor, AuditAction.SERVICE_TOKEN_CREATED, AuditTargetType.SERVICE_TOKEN, token.id,
            mapOf("name" to name, "token_prefix" to token.tokenPrefix)
        )
        logInfo("Service token issued: name=$name, prefix=${token.tokenPrefix}, by=${actor.login}")
        return IssuedServiceTokenResponse(token = ServiceTokenResponse.from(token), value = value)
    }

    private fun nameTaken(name: String) =
        WrongTypeException("Service token name '$name' is taken by a live token", "Токен с названием «$name» уже есть")
}

/** The super administrator behind the request; anybody else is refused with the taxonomy's 413. */
internal fun requireSuperAdministrator() = currentConsoleUser().also { user ->
    if (user.role != ConsoleRole.SUPER_ADMINISTRATOR) {
        throw InsufficientPrivilegesException(
            "${user.login} (${user.role}) may not manage service tokens or push settings",
            "Только суперадминистратор управляет токенами и настройками пуша"
        )
    }
}
