// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import com.ef_softworks.thundervox_server.service.ServiceAuthentication
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * Resolves a presented service token to the authentication of the operator's backend. Lookup is by hash, so the
 * comparison never touches the value itself and timing says nothing about it. A revoked token is a wrong token.
 */
@Component
class AuthenticateServiceToken(
    private val serviceTokenRepository: ServiceTokenRepository,
    private val jdbcClient: JdbcClient,
) {

    fun authenticate(presented: String): ServiceAuthentication? {
        if (!ServiceTokenValue.looksLikeToken(presented)) {
            return null
        }
        val token = serviceTokenRepository.findByTokenHash(ServiceTokenValue.hash(presented)) ?: return null
        if (!token.enabled) {
            return null
        }
        touchLastUsed(token.id)
        return ServiceAuthentication(token.name)
    }

    // At most one write per minute per token: "last used" is for the console, not an access log
    private fun touchLastUsed(id: Long) {
        jdbcClient.sql(
            """
            UPDATE service_token SET last_used_at = now()
            WHERE id = :id AND (last_used_at IS NULL OR last_used_at < now() - interval '1 minute')
            """.trimIndent()
        )
            .param("id", id)
            .update()
    }
}
