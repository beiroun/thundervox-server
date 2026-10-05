// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.access

import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserRepository
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserResponse
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Login and password -> console token.
 *
 * Every failure looks the same to the caller (unknown login, wrong password, blocked user), and an unknown login
 * still costs one BCrypt comparison, so neither the answer nor its timing tells which logins exist. Failures are
 * logged with the login for the operator to spot guessing; there is no lockout in this version.
 */
@Component
class LogInToConsole(
    private val consoleUserRepository: ConsoleUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val issueConsoleToken: IssueConsoleToken,
) {

    private val decoyPasswordHash: String by lazy { passwordEncoder.encode("decoy-password-of-no-account")!! }

    @Transactional(readOnly = true)
    fun logIn(request: LoginRequest): LoginResponse {
        val login = request.login.trim()
        val user = consoleUserRepository.findByLogin(login)
        val passwordMatches = passwordEncoder.matches(request.password, user?.passwordHash ?: decoyPasswordHash)
        if (user == null || !passwordMatches || !user.enabled) {
            logWarn("Console login failed: login=$login, known=${user != null}, enabled=${user?.enabled}")
            throw InsufficientPrivilegesException("Console login failed for '$login'", "Неверный логин или пароль")
        }
        val token = issueConsoleToken.issue(user)
        logInfo("Console login: login=${user.login}, role=${user.role}")
        return LoginResponse(token = token.value, expiresAt = token.expiresAt, user = ConsoleUserResponse.from(user))
    }
}
