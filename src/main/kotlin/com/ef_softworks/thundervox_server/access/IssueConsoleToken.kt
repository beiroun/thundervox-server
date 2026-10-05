// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.access

import com.ef_softworks.thundervox_server.config.ConsoleAccessProperties
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserEntity
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

data class IssuedConsoleToken(val value: String, val expiresAt: LocalDateTime)

/**
 * Bearer token of a console session. The subject is the user id; login and role ride along only for the console to
 * show - every request re-reads the user (AuthenticateConsoleToken), so a role change or a block applies at once.
 */
@Component
class IssueConsoleToken(
    private val jwtEncoder: JwtEncoder,
    private val consoleAccessProperties: ConsoleAccessProperties,
) {

    fun issue(user: ConsoleUserEntity): IssuedConsoleToken {
        val issuedAt = Instant.now()
        val expiresAt = issuedAt.plus(consoleAccessProperties.tokenTtl)
        val claims = JwtClaimsSet.builder()
            .issuer(TOKEN_ISSUER)
            .subject(user.id.toString())
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("login", user.login)
            .claim("role", user.role.name)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue
        return IssuedConsoleToken(value, LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault()))
    }

    companion object {
        const val TOKEN_ISSUER = "thundervox-server"
    }
}
