// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.registration

import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * The registration journal: registration_state (one row per number, keyed by username) and registration_event
 * (one row per transition). Only the presence sampler writes here; every reader of "is this number online, when
 * was it seen" goes through [byUsername] - nobody scans `location` on a request any more.
 */
@Component
class RegistrationStateStore(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) {

    fun byUsername(): Map<String, RegistrationState> =
        jdbcClient.sql("$SELECT_STATE WHERE tenant_id = :tenantId")
            .param("tenantId", defaultTenant.id)
            .query { resultSet, _ -> resultSet.getString("username") to resultSet.toRegistrationState() }
            .list()
            .toMap()

    fun one(username: String): RegistrationState? =
        jdbcClient.sql("$SELECT_STATE WHERE tenant_id = :tenantId AND username = :username")
            .param("tenantId", defaultTenant.id)
            .param("username", username)
            .query { resultSet, _ -> resultSet.toRegistrationState() }
            .optional()
            .orElse(null)

    private fun java.sql.ResultSet.toRegistrationState() = RegistrationState(
        online = getBoolean("online"),
        onlineSince = getObject("online_since", LocalDateTime::class.java),
        lastOnlineAt = getObject("last_online_at", LocalDateTime::class.java),
        lastOfflineAt = getObject("last_offline_at", LocalDateTime::class.java),
        contact = getString("contact"),
        received = getString("received"),
        userAgent = getString("user_agent"),
        contacts = getInt("contacts"),
        registrationsCount = getInt("registrations_count"),
    )

    /** The number came online (or is seen for the first time): the state row and an ONLINE event, one transaction. */
    @Transactional
    fun markOnline(username: String, live: LiveRegistration, at: LocalDateTime, previous: RegistrationState?): RegistrationState {
        val next = RegistrationState(
            online = true,
            onlineSince = at,
            lastOnlineAt = at,
            lastOfflineAt = previous?.lastOfflineAt,
            contact = live.contact,
            received = live.received,
            userAgent = live.userAgent,
            contacts = live.contacts,
            registrationsCount = (previous?.registrationsCount ?: 0) + 1,
        )
        upsert(username, next)
        recordEvent(username, RegistrationEventKind.ONLINE, live, at)
        return next
    }

    /** The number is online and its devices changed (another phone, a new socket): the row only, no event. */
    @Transactional
    fun updateDevices(username: String, live: LiveRegistration, previous: RegistrationState): RegistrationState {
        val next = previous.copy(contact = live.contact, received = live.received, userAgent = live.userAgent, contacts = live.contacts)
        upsert(username, next)
        return next
    }

    /** Every contact of the number is gone: the state row and an OFFLINE event, one transaction. */
    @Transactional
    fun markOffline(username: String, at: LocalDateTime, previous: RegistrationState): RegistrationState {
        val next = previous.copy(online = false, onlineSince = null, lastOfflineAt = at, contacts = 0)
        upsert(username, next)
        recordEvent(username, RegistrationEventKind.OFFLINE, null, at)
        return next
    }

    private fun upsert(username: String, state: RegistrationState) {
        jdbcClient.sql(
            """
            INSERT INTO registration_state (tenant_id, username, online, online_since, last_online_at, last_offline_at,
                                            contact, received, user_agent, contacts, registrations_count, updated_at)
            VALUES (:tenantId, :username, :online, :onlineSince, :lastOnlineAt, :lastOfflineAt,
                    :contact, :received, :userAgent, :contacts, :registrationsCount, now())
            ON CONFLICT (tenant_id, username) DO UPDATE SET
                online = EXCLUDED.online,
                online_since = EXCLUDED.online_since,
                last_online_at = EXCLUDED.last_online_at,
                last_offline_at = EXCLUDED.last_offline_at,
                contact = EXCLUDED.contact,
                received = EXCLUDED.received,
                user_agent = EXCLUDED.user_agent,
                contacts = EXCLUDED.contacts,
                registrations_count = EXCLUDED.registrations_count,
                updated_at = now()
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("username", username)
            .param("online", state.online)
            .param("onlineSince", state.onlineSince)
            .param("lastOnlineAt", state.lastOnlineAt)
            .param("lastOfflineAt", state.lastOfflineAt)
            .param("contact", state.contact?.take(CONTACT_LENGTH))
            .param("received", state.received?.take(RECEIVED_LENGTH))
            .param("userAgent", state.userAgent?.take(USER_AGENT_LENGTH))
            .param("contacts", state.contacts)
            .param("registrationsCount", state.registrationsCount)
            .update()
    }

    private fun recordEvent(username: String, kind: RegistrationEventKind, live: LiveRegistration?, at: LocalDateTime) {
        jdbcClient.sql(
            """
            INSERT INTO registration_event (tenant_id, username, kind, contact, received, user_agent, occurred_at)
            VALUES (:tenantId, :username, :kind, :contact, :received, :userAgent, :occurredAt)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("username", username)
            .param("kind", kind.name)
            .param("contact", live?.contact?.take(CONTACT_LENGTH))
            .param("received", live?.received?.take(RECEIVED_LENGTH))
            .param("userAgent", live?.userAgent?.take(USER_AGENT_LENGTH))
            .param("occurredAt", at)
            .update()
    }

    /** A half-minute picture of how many numbers of each kind are online, for the dashboard charts. */
    fun recordSample(sample: PresenceSample) {
        jdbcClient.sql(
            """
            INSERT INTO presence_sample (tenant_id, sampled_at, panels_total, panels_online, clients_total, clients_online, other_online)
            VALUES (:tenantId, :sampledAt, :panelsTotal, :panelsOnline, :clientsTotal, :clientsOnline, :otherOnline)
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("sampledAt", sample.sampledAt)
            .param("panelsTotal", sample.panelsTotal)
            .param("panelsOnline", sample.panelsOnline)
            .param("clientsTotal", sample.clientsTotal)
            .param("clientsOnline", sample.clientsOnline)
            .param("otherOnline", sample.otherOnline)
            .update()
    }

    fun deleteEventsOlderThan(cutoff: LocalDateTime): Int =
        jdbcClient.sql("DELETE FROM registration_event WHERE occurred_at < :cutoff").param("cutoff", cutoff).update()

    fun deleteSamplesOlderThan(cutoff: LocalDateTime): Int =
        jdbcClient.sql("DELETE FROM presence_sample WHERE sampled_at < :cutoff").param("cutoff", cutoff).update()

    companion object {
        private const val SELECT_STATE = """
            SELECT username, online, online_since, last_online_at, last_offline_at, contact, received, user_agent,
                   contacts, registrations_count
            FROM registration_state"""
        private const val CONTACT_LENGTH = 512
        private const val RECEIVED_LENGTH = 128
        private const val USER_AGENT_LENGTH = 255
    }
}

/** One row of presence_sample. "Other" = registered numbers without an account in sip_account (an open core in tests). */
data class PresenceSample(
    val sampledAt: LocalDateTime,
    val panelsTotal: Int,
    val panelsOnline: Int,
    val clientsTotal: Int,
    val clientsOnline: Int,
    val otherOnline: Int,
)
