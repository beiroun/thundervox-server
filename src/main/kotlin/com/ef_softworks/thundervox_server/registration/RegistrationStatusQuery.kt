// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.registration

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** Latest registration of a SIP username as the core stored it in `location`. */
data class RegistrationStatus(
    val contact: String,
    /** Real source socket of a NATed device (the core's received_avp); null when it registered directly. */
    val received: String?,
    val userAgent: String,
    val expiresAt: LocalDateTime,
    val lastSeenAt: LocalDateTime,
) {
    fun isOnlineAt(moment: LocalDateTime): Boolean = expiresAt.isAfter(moment)
}

/**
 * Who is registered right now, read from the core's `location` table (usrloc write-through, core v0.10 with
 * TVX_PROVISIONING). Without that switch the core keeps registrations in memory only and this query sees nobody.
 *
 * Point of failure: usrloc writes expires/last_modified in the core container's local time and the server compares
 * them with its own local time, so both containers must run in the same time zone (TZ in the compose file).
 */
@Component
class RegistrationStatusQuery(private val jdbcClient: JdbcClient) {

    fun latestByUsername(): Map<String, RegistrationStatus> =
        jdbcClient.sql(
            """
            SELECT DISTINCT ON (username) username, contact, received, user_agent, expires, last_modified
            FROM location
            ORDER BY username, last_modified DESC
            """.trimIndent()
        )
            .query { resultSet, _ ->
                resultSet.getString("username") to RegistrationStatus(
                    contact = resultSet.getString("contact"),
                    received = resultSet.getString("received"),
                    userAgent = resultSet.getString("user_agent"),
                    expiresAt = resultSet.getObject("expires", LocalDateTime::class.java),
                    lastSeenAt = resultSet.getObject("last_modified", LocalDateTime::class.java),
                )
            }
            .list()
            .toMap()
}
