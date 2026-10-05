// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

/**
 * Console roles, weakest first.
 *
 * READER sees everything and changes nothing. ADMINISTRATOR manages SIP numbers and readers. SUPER_ADMINISTRATOR
 * (exactly one, defined by the environment) additionally manages administrators. Nobody manages the super
 * administrator through the API.
 */
enum class ConsoleRole(val title: String) {
    READER("Наблюдатель"),
    ADMINISTRATOR("Администратор"),
    SUPER_ADMINISTRATOR("Суперадминистратор");

    /** Spring Security authority name: URL rules use hasRole(name). */
    val authority: String get() = "ROLE_$name"

    /** Whether a user with this role may create, edit or reset the password of a user with [target] role. */
    fun mayManage(target: ConsoleRole): Boolean = when (this) {
        READER -> false
        ADMINISTRATOR -> target == READER
        SUPER_ADMINISTRATOR -> target == READER || target == ADMINISTRATOR
    }
}
