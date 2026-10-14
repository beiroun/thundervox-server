// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.registration

import java.time.LocalDateTime

/**
 * What the core's `location` table says about one number right now: at least one unexpired contact. The
 * displayed contact is the most recently refreshed one; [contacts] counts every live device of the number.
 */
data class LiveRegistration(
    val contact: String,
    val received: String?,
    val userAgent: String,
    val contacts: Int,
)

/**
 * The server's own memory of a number's registrations (registration_state): online now or not, and when it was
 * last seen either way. Unlike `location`, a row never disappears, so "never registered" (no row) and "was online
 * at ..." (a row with online = false) are different answers.
 */
data class RegistrationState(
    val online: Boolean,
    val onlineSince: LocalDateTime?,
    val lastOnlineAt: LocalDateTime,
    val lastOfflineAt: LocalDateTime?,
    val contact: String?,
    val received: String?,
    val userAgent: String?,
    val contacts: Int,
    val registrationsCount: Int,
) {
    fun sameDevicesAs(live: LiveRegistration): Boolean =
        contact == live.contact && received == live.received && userAgent == live.userAgent && contacts == live.contacts
}

enum class RegistrationEventKind { ONLINE, OFFLINE }
