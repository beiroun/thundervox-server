// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.access

import com.ef_softworks.thundervox_server.consoleuser.AuthenticatedConsoleUser
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserAuthentication
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserRepository
import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Turns a token whose signature and expiry are already verified into the console user it names.
 *
 * The user is read from the database on every request, so the token itself grants nothing beyond identity: a
 * deleted, blocked or demoted user loses access with the next request, and a password reset revokes every token
 * issued before it. Any rejection here is an authentication failure (401), the console logs out.
 */
@Component
class AuthenticateConsoleToken(
    private val consoleUserRepository: ConsoleUserRepository,
) : Converter<Jwt, AbstractAuthenticationToken> {

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId = jwt.subject?.toLongOrNull()
            ?: throw BadCredentialsException("Console token without a user id")
        val user = consoleUserRepository.findById(userId).orElse(null)
            ?: throw BadCredentialsException("Console user $userId of the token no longer exists")
        if (!user.enabled) {
            throw DisabledException("Console user ${user.login} is blocked")
        }
        val issuedAt = jwt.issuedAt
            ?: throw BadCredentialsException("Console token of ${user.login} has no issue time")
        // iat has second precision: compare at the same precision, or a login right after a reset would bounce
        val passwordChangedAt = user.passwordChangedAt.atZone(ZoneId.systemDefault()).toInstant().truncatedTo(ChronoUnit.SECONDS)
        if (passwordChangedAt.isAfter(issuedAt)) {
            throw CredentialsExpiredException("Console token of ${user.login} predates the last password change")
        }
        return ConsoleUserAuthentication(AuthenticatedConsoleUser(user.id, user.login, user.role), jwt.tokenValue)
    }
}
