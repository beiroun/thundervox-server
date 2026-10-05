// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.credential.RandomPassword
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Adds a console user. Administrators add readers; the super administrator adds readers and administrators. */
@Component
class CreateConsoleUser(
    private val consoleUserRepository: ConsoleUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val defaultTenant: DefaultTenant,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun create(request: CreateConsoleUserRequest): ConsoleUserCredentialsResponse {
        val actor = currentConsoleUser()
        if (!actor.role.mayManage(request.role)) {
            throw InsufficientPrivilegesException(
                "${actor.login} (${actor.role}) may not create a ${request.role}",
                "Недостаточно прав, чтобы создать пользователя с ролью «${request.role.title}»"
            )
        }
        val login = ConsoleUserInputRules.requireValidLogin(request.login)
        if (consoleUserRepository.existsByLogin(login)) {
            throw loginTaken(login)
        }
        val requestedPassword = request.password?.takeIf { it.isNotEmpty() }
        val generatedPassword = requestedPassword == null
        val password = requestedPassword?.let(ConsoleUserInputRules::requireValidPassword) ?: RandomPassword.generate()

        val user = try {
            consoleUserRepository.saveAndFlush(
                ConsoleUserEntity(
                    tenantId = defaultTenant.id,
                    login = login,
                    passwordHash = passwordEncoder.encode(password)!!,
                    role = request.role,
                )
            )
        } catch (ex: DataIntegrityViolationException) {
            // Two operators took the same login at the same moment: the unique constraint decided
            throw loginTaken(login)
        }

        auditLog.record(
            actor.auditActor, AuditAction.CONSOLE_USER_CREATED, AuditTargetType.CONSOLE_USER, user.id,
            mapOf("login" to login, "role" to request.role.name, "password" to passwordOrigin(generatedPassword))
        )
        logInfo("Console user created: login=$login, role=${request.role}, by=${actor.login}")
        return ConsoleUserCredentialsResponse(
            user = ConsoleUserResponse.from(user),
            generatedPassword = password.takeIf { generatedPassword },
        )
    }

    private fun loginTaken(login: String) =
        WrongTypeException("Console login '$login' is taken", "Логин «$login» уже занят")
}

/** How a password came to be, for the audit trail: the value itself is never recorded. */
internal fun passwordOrigin(generated: Boolean): String = if (generated) "generated" else "set_by_operator"
