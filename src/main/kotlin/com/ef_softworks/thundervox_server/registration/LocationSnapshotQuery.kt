// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.registration

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Who is registered right now, read from the core's `location` table (usrloc write-through, core with
 * TVX_PROVISIONING). Without that switch the core keeps registrations in memory only and this query sees nobody.
 * The core drops a TCP contact the moment its connection dies (handle_lost_tcp) and expires UDP ones, so a row
 * here is a reachable device, not a memory of one.
 *
 * Point of failure: usrloc writes expires/last_modified in the core container's local time and the server compares
 * them with its own local time, so both containers must run in the same time zone (TZ in the compose file).
 */
@Component
class LocationSnapshotQuery(private val jdbcClient: JdbcClient) {

    /** Numbers with at least one unexpired contact at [now], shown with their most recently refreshed contact. */
    fun onlineByUsername(now: LocalDateTime): Map<String, LiveRegistration> {
        val rows = jdbcClient.sql(
            """
            SELECT username, contact, received, user_agent, expires, last_modified
            FROM location
            """.trimIndent()
        )
            .query { resultSet, _ ->
                LocationRow(
                    username = resultSet.getString("username"),
                    contact = resultSet.getString("contact"),
                    received = resultSet.getString("received"),
                    userAgent = resultSet.getString("user_agent"),
                    expiresAt = resultSet.getObject("expires", LocalDateTime::class.java),
                    lastModified = resultSet.getObject("last_modified", LocalDateTime::class.java),
                )
            }
            .list()
        return rows
            .filter { it.expiresAt.isAfter(now) }
            .groupBy { it.username }
            .mapValues { (_, contacts) ->
                val newest = contacts.maxBy { it.lastModified }
                // The earliest live contact registered first: for a number that has just come up, that is the moment
                // it did, to the second (the sampler's own tick is up to 10 s later)
                val earliest = contacts.minOf { it.lastModified }
                LiveRegistration(newest.contact, newest.received, newest.userAgent, contacts.size, earliest, newest.lastModified)
            }
    }

    private data class LocationRow(
        val username: String,
        val contact: String,
        val received: String?,
        val userAgent: String,
        val expiresAt: LocalDateTime,
        val lastModified: LocalDateTime,
    )
}
