// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.registration

import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Turns the core's live `location` table into the registration journal.
 *
 * Every tick it reads who is online now and compares with what it remembered: a number that appeared comes ONLINE,
 * a number that vanished goes OFFLINE, each transition is one row in registration_event and an update of its
 * registration_state row. A REGISTER refresh changes nothing and writes nothing - that is the point: thousands of
 * panels refreshing every few minutes cost the database nothing, and the "registrations" chart counts devices
 * coming up, not keepalives. Every third tick a presence sample is taken for the charts.
 *
 * The memory of the previous tick lives in this bean and is loaded from registration_state on the first tick, so a
 * restart of the server resumes the journal where it left it (transitions during the downtime collapse into one).
 */
@Component
class PresenceSampler(
    private val locationSnapshotQuery: LocationSnapshotQuery,
    private val registrationStateStore: RegistrationStateStore,
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) {

    private val known = HashMap<String, RegistrationState>()
    private var loaded = false
    private var ticks = 0L

    @Scheduled(fixedDelayString = "PT10S", initialDelayString = "PT15S")
    fun tick() {
        try {
            sample()
        } catch (ex: Exception) {
            // Data-loss point: a tick that fails leaves the journal where it was; the next tick catches up,
            // but a transition that happened and reverted in between is never recorded
            logWarn("Presence sampling failed; retrying in 10 s", ex)
        }
    }

    @Synchronized
    fun sample() {
        if (!loaded) {
            known.putAll(registrationStateStore.byUsername())
            loaded = true
            logInfo("Presence sampler: ${known.size} numbers known from the journal, ${known.values.count { it.online }} of them online")
        }
        val now = LocalDateTime.now()
        val live = locationSnapshotQuery.onlineByUsername(now)

        for ((username, registration) in live) {
            val previous = known[username]
            known[username] = when {
                previous == null || !previous.online -> {
                    logInfo("Number $username is ONLINE: ${registration.contacts} device(s), ${registration.userAgent} from ${registration.received ?: registration.contact}")
                    registrationStateStore.markOnline(username, registration, now, previous)
                }
                !previous.sameDevicesAs(registration) -> registrationStateStore.updateDevices(username, registration, previous)
                else -> previous
            }
        }
        val gone = known.filter { (username, state) -> state.online && username !in live }
        for ((username, previous) in gone) {
            logInfo("Number $username is OFFLINE (was online since ${previous.onlineSince})")
            known[username] = registrationStateStore.markOffline(username, now, previous)
        }

        if (++ticks % SAMPLE_EVERY_TICKS == 0L) {
            registrationStateStore.recordSample(countPresence(live.keys, now))
        }
    }

    private fun countPresence(onlineUsernames: Set<String>, at: LocalDateTime): PresenceSample {
        val kindByUsername = jdbcClient.sql("SELECT username, kind FROM sip_account WHERE tenant_id = :tenantId AND enabled")
            .param("tenantId", defaultTenant.id)
            .query { resultSet, _ -> resultSet.getString("username") to SipAccountKind.valueOf(resultSet.getString("kind")) }
            .list()
            .toMap()
        val onlineKinds = onlineUsernames.map { kindByUsername[it] }
        return PresenceSample(
            sampledAt = at,
            panelsTotal = kindByUsername.values.count { it == SipAccountKind.PANEL },
            panelsOnline = onlineKinds.count { it == SipAccountKind.PANEL },
            clientsTotal = kindByUsername.values.count { it == SipAccountKind.CLIENT },
            clientsOnline = onlineKinds.count { it == SipAccountKind.CLIENT },
            otherOnline = onlineKinds.count { it == null },
        )
    }

    companion object {
        private const val SAMPLE_EVERY_TICKS = 3L
    }
}
