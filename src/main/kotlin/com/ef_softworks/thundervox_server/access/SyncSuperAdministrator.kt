// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.access

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.config.ConsoleAccessProperties
import com.ef_softworks.thundervox_server.consoleuser.ConsoleRole
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserEntity
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserRepository
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Keeps the one super administrator equal to TVX_SUPERADMIN_LOGIN / TVX_SUPERADMIN_PASSWORD on every start.
 *
 * The environment is the source of truth: a new login renames the existing row (its id and audit history stay), a
 * new password replaces the hash and logs out the sessions issued under the old one, a blocked row is unblocked.
 * A login already held by another console user is a configuration error and stops the start.
 */
@Component
class SyncSuperAdministrator(
    private val consoleUserRepository: ConsoleUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val consoleAccessProperties: ConsoleAccessProperties,
    private val defaultTenant: DefaultTenant,
    private val auditLog: AuditLog,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val desired = consoleAccessProperties.superAdministrator
        val holderOfLogin = consoleUserRepository.findByLogin(desired.login)
        check(holderOfLogin == null || holderOfLogin.role == ConsoleRole.SUPER_ADMINISTRATOR) {
            "TVX_SUPERADMIN_LOGIN '${desired.login}' already belongs to a ${holderOfLogin?.role} console user - choose another login"
        }

        val current = consoleUserRepository.findFirstByRole(ConsoleRole.SUPER_ADMINISTRATOR)
        if (current == null) {
            val created = consoleUserRepository.save(
                ConsoleUserEntity(
                    tenantId = defaultTenant.id,
                    login = desired.login,
                    passwordHash = passwordEncoder.encode(desired.password)!!,
                    role = ConsoleRole.SUPER_ADMINISTRATOR,
                )
            )
            auditLog.record(
                AuditActor.SYSTEM, AuditAction.SUPER_ADMINISTRATOR_SYNCED, AuditTargetType.CONSOLE_USER, created.id,
                mapOf("login" to desired.login, "change" to "created")
            )
            logInfo("Super administrator created from the environment: login=${desired.login}")
            return
        }

        val changes = mutableListOf<String>()
        if (current.login != desired.login) {
            changes += "login"
            current.login = desired.login
        }
        if (!passwordEncoder.matches(desired.password, current.passwordHash)) {
            changes += "password"
            current.passwordHash = passwordEncoder.encode(desired.password)!!
            current.passwordChangedAt = LocalDateTime.now()
        }
        if (!current.enabled) {
            changes += "enabled"
            current.enabled = true
        }
        if (changes.isEmpty()) {
            return
        }
        consoleUserRepository.save(current)
        auditLog.record(
            AuditActor.SYSTEM, AuditAction.SUPER_ADMINISTRATOR_SYNCED, AuditTargetType.CONSOLE_USER, current.id,
            mapOf("login" to current.login, "change" to changes)
        )
        logInfo("Super administrator synchronised with the environment: login=${current.login}, changed=$changes")
    }
}
