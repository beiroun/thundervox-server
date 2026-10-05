// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.credential.RandomPassword
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Changes of an existing console user: role, enabled flag, password. The acting user must be allowed to manage the
 * user's current role and, on a role change, the new one too; the super administrator is never editable here.
 * Role and enabled flag take effect on the user's very next request (tokens are re-checked against the database).
 */
@Component
class UpdateConsoleUser(
    private val consoleUserRepository: ConsoleUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun update(userId: Long, request: UpdateConsoleUserRequest): ConsoleUserResponse {
        val actor = currentConsoleUser()
        val user = loadManageable(userId, actor)
        val newRole = request.role
        if (newRole != null && !actor.role.mayManage(newRole)) {
            throw InsufficientPrivilegesException(
                "${actor.login} (${actor.role}) may not grant $newRole",
                "Недостаточно прав, чтобы назначить роль «${newRole.title}»"
            )
        }

        val changes = buildMap {
            if (newRole != null && newRole != user.role) {
                put("role", mapOf("from" to user.role.name, "to" to newRole.name))
                user.role = newRole
            }
            if (request.enabled != null && request.enabled != user.enabled) {
                put("enabled", mapOf("from" to user.enabled, "to" to request.enabled))
                user.enabled = request.enabled
            }
        }
        if (changes.isNotEmpty()) {
            consoleUserRepository.save(user)
            auditLog.record(
                actor.auditActor, AuditAction.CONSOLE_USER_UPDATED, AuditTargetType.CONSOLE_USER, user.id,
                mapOf("login" to user.login) + changes
            )
            logInfo("Console user updated: login=${user.login}, changes=${changes.keys}, by=${actor.login}")
        }
        return ConsoleUserResponse.from(user)
    }

    /** New password for the user; every token issued before it stops working. */
    @Transactional
    fun resetPassword(userId: Long, request: ResetConsoleUserPasswordRequest): ConsoleUserCredentialsResponse {
        val actor = currentConsoleUser()
        val user = loadManageable(userId, actor)
        val requestedPassword = request.password?.takeIf { it.isNotEmpty() }
        val generatedPassword = requestedPassword == null
        val password = requestedPassword?.let(ConsoleUserInputRules::requireValidPassword) ?: RandomPassword.generate()

        user.passwordHash = passwordEncoder.encode(password)!!
        user.passwordChangedAt = LocalDateTime.now()
        consoleUserRepository.save(user)

        auditLog.record(
            actor.auditActor, AuditAction.CONSOLE_USER_PASSWORD_RESET, AuditTargetType.CONSOLE_USER, user.id,
            mapOf("login" to user.login, "password" to passwordOrigin(generatedPassword))
        )
        logInfo("Console user password reset: login=${user.login}, by=${actor.login}")
        return ConsoleUserCredentialsResponse(
            user = ConsoleUserResponse.from(user),
            generatedPassword = password.takeIf { generatedPassword },
        )
    }

    private fun loadManageable(userId: Long, actor: AuthenticatedConsoleUser): ConsoleUserEntity {
        val user = consoleUserRepository.findById(userId).orElseThrow {
            NotFoundException("Console user $userId not found", "Пользователь консоли не найден")
        }
        if (user.role == ConsoleRole.SUPER_ADMINISTRATOR) {
            throw InsufficientPrivilegesException(
                "Super administrator ${user.login} is managed by the environment, not the API",
                "Суперадминистратор задаётся в окружении сервера и через консоль не меняется"
            )
        }
        if (!actor.role.mayManage(user.role)) {
            throw InsufficientPrivilegesException(
                "${actor.login} (${actor.role}) may not manage ${user.login} (${user.role})",
                "Недостаточно прав, чтобы менять пользователя с ролью «${user.role.title}»"
            )
        }
        return user
    }
}
