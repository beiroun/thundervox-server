// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.registration.RegistrationStatus
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class SipAccountResponse(
    @JsonProperty("id") val id: Long,
    /** The SIP number: what the device registers as and what a panel dials. */
    @JsonProperty("username") val username: String,
    @JsonProperty("name") val name: String,
    @JsonProperty("kind") val kind: SipAccountKind,
    @JsonProperty("enabled") val enabled: Boolean,
    @JsonProperty("created_at") val createdAt: LocalDateTime,
    @JsonProperty("password_rotated_at") val passwordRotatedAt: LocalDateTime,
    /** Last registration the core stored; null when the number never registered or the core runs without a DB. */
    @JsonProperty("registration") val registration: SipRegistrationResponse?,
) {
    companion object {
        fun from(account: SipAccountEntity, registration: RegistrationStatus?, now: LocalDateTime) = SipAccountResponse(
            id = account.id,
            username = account.username,
            name = account.name,
            kind = account.kind,
            enabled = account.enabled,
            createdAt = account.createdAt,
            passwordRotatedAt = account.passwordRotatedAt,
            registration = registration?.let { SipRegistrationResponse.from(it, now) },
        )
    }
}

data class SipRegistrationResponse(
    @JsonProperty("online") val online: Boolean,
    @JsonProperty("contact") val contact: String,
    @JsonProperty("received") val received: String?,
    @JsonProperty("user_agent") val userAgent: String,
    @JsonProperty("expires_at") val expiresAt: LocalDateTime,
    @JsonProperty("last_seen_at") val lastSeenAt: LocalDateTime,
) {
    companion object {
        fun from(status: RegistrationStatus, now: LocalDateTime) = SipRegistrationResponse(
            online = status.isOnlineAt(now),
            contact = status.contact,
            received = status.received,
            userAgent = status.userAgent,
            expiresAt = status.expiresAt,
            lastSeenAt = status.lastSeenAt,
        )
    }
}

data class CreateSipAccountRequest(
    @JsonProperty("kind") val kind: SipAccountKind,
    @JsonProperty("name") val name: String,
    /** Empty or absent: the server generates the next number of the kind (the normal way). */
    @JsonProperty("username") val username: String?,
    /** Empty or absent: the server generates one and returns it once. */
    @JsonProperty("password") val password: String?,
)

data class RenameSipAccountRequest(
    @JsonProperty("name") val name: String,
)

data class RotateSipAccountPasswordRequest(
    /** Empty or absent: the server generates one and returns it once. */
    @JsonProperty("password") val password: String?,
)

/** Result of creating a number or replacing its password. */
data class SipAccountCredentialsResponse(
    @JsonProperty("account") val account: SipAccountResponse,
    /** Realm to enter on the device next to the number and password (= the SIP domain). */
    @JsonProperty("realm") val realm: String,
    /** Only when the server generated the password; never stored, never shown again. */
    @JsonProperty("generated_password") val generatedPassword: String?,
)
