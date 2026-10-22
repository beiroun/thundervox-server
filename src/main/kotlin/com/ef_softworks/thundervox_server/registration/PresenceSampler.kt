// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.registration

import com.ef_softworks.thundervox_server.calls.CallLog
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logDebug
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
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
 * Only panels get lines in registration_event: an app client registers on every wake push, and a line per
 * transition of 300k subscribers would be tens of millions of rows a year nobody reads. Every number keeps its
 * registration_state; a subscriber coming online (or registering a new device) while a WAKE call to it waits is
 * written onto that call instead - the one fact about a subscriber's registration the operator needs.
 *
 * A number is OFFLINE only after it has been missing for [OFFLINE_GRACE]: a softphone whose TCP connection the
 * network drops every twenty minutes reconnects within a minute or two, and without the grace every drop was two
 * lines in the event log and a blink of the dashboard. A number back within the grace never left; the OFFLINE that
 * does get written carries the tick it went missing at, not the tick the grace ran out. ONLINE carries the moment
 * the core stored the registration (the contact's last_modified), not the tick that noticed it.
 *
 * The memory of the previous tick lives in this bean and is loaded from registration_state on the first tick, so a
 * restart of the server resumes the journal where it left it (transitions during the downtime collapse into one).
 */
@Component
class PresenceSampler(
    private val locationSnapshotQuery: LocationSnapshotQuery,
    private val registrationStateStore: RegistrationStateStore,
    private val callLog: CallLog,
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) {

    private val known = HashMap<String, RegistrationState>()
    /** Numbers still online in the journal but missing from `location`, with the tick they went missing at. */
    private val missingSince = HashMap<String, LocalDateTime>()
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
            missingSince.remove(username)?.let { logDebug("Number $username is back within the offline grace (missing since $it)") }
            known[username] = when {
                previous == null || !previous.online -> {
                    logInfo("Number $username is ONLINE: ${registration.contacts} device(s), ${registration.userAgent} from ${registration.received ?: registration.contact}")
                    val at = onlineAt(registration, previous, now)
                    markWakeCall(username, at)
                    registrationStateStore.markOnline(username, registration, at, previous, journaled = isPanel(username))
                }
                !previous.sameDevicesAs(registration) -> {
                    // A different newest contact is a device that has just registered: a woken phone whose
                    // previous registration had not expired yet comes this way, not as a transition
                    if (registration.contact != previous.contact) {
                        markWakeCall(username, minOf(registration.latestRegisteredAt, now))
                    }
                    registrationStateStore.updateDevices(username, registration, previous)
                }
                else -> previous
            }
        }
        val gone = known.filter { (username, state) -> state.online && username !in live }
        for ((username, previous) in gone) {
            val missingAt = missingSince.getOrPut(username) { now }
            if (Duration.between(missingAt, now) < OFFLINE_GRACE) {
                continue
            }
            missingSince.remove(username)
            logInfo("Number $username is OFFLINE since $missingAt (was online since ${previous.onlineSince})")
            known[username] = registrationStateStore.markOffline(username, missingAt, previous, journaled = isPanel(username))
        }

        if (++ticks % SAMPLE_EVERY_TICKS == 0L) {
            // Online as the journal has it: a number within the grace is still online on the charts too
            val onlineUsernames = known.filterValues { it.online }.keys
            registrationStateStore.recordSample(countPresence(onlineUsernames, now))
        }
    }

    private fun isPanel(username: String): Boolean =
        jdbcClient.sql("SELECT kind FROM sip_account WHERE tenant_id = :tenantId AND username = :username")
            .param("tenantId", defaultTenant.id)
            .param("username", username)
            .query(String::class.java)
            .optional()
            .map { it == SipAccountKind.PANEL.name }
            .orElse(false)

    private fun markWakeCall(username: String, at: LocalDateTime) {
        if (callLog.markCalleeRegistered(username, at) > 0) {
            logInfo("Number $username registered at $at during a WAKE call to it: the wake push reached it")
        }
    }

    // The registration's own time, kept between the number's last OFFLINE and now: the core's clock running ahead
    // of the server's must neither put it in the future nor before the line it follows
    private fun onlineAt(registration: LiveRegistration, previous: RegistrationState?, now: LocalDateTime): LocalDateTime {
        val notBefore = previous?.lastOfflineAt ?: registration.registeredAt
        return minOf(maxOf(registration.registeredAt, notBefore), now)
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

        /** How long a number may be missing from `location` before it counts as offline (decided 2026-10-22). */
        private val OFFLINE_GRACE: Duration = Duration.ofSeconds(60)
    }
}
