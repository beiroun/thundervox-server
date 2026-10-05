// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/** The console user behind the current request, as loaded from the database for this very request. */
data class AuthenticatedConsoleUser(
    val id: Long,
    val login: String,
    val role: ConsoleRole,
) {
    val auditActor: AuditActor get() = AuditActor.consoleUser(login)
}

/** Authentication of a request that carried a valid console token. */
class ConsoleUserAuthentication(
    private val consoleUser: AuthenticatedConsoleUser,
    private val token: String,
) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority(consoleUser.role.authority))) {

    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): AuthenticatedConsoleUser = consoleUser

    override fun getCredentials(): String = token

    override fun getName(): String = consoleUser.login
}

/**
 * The console user of the current request. URL rules let only authenticated requests reach the controllers, so an
 * empty context here means a rule was loosened by mistake - refused, not guessed.
 */
fun currentConsoleUser(): AuthenticatedConsoleUser =
    SecurityContextHolder.getContext().authentication?.principal as? AuthenticatedConsoleUser
        ?: throw InsufficientPrivilegesException("No authenticated console user in the security context")
