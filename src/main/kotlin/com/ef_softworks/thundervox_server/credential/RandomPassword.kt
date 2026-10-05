// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.credential

import java.security.SecureRandom

/**
 * Passwords the server makes up when the operator leaves the field empty.
 *
 * Letters and digits only, without look-alikes (0/O, 1/l/I): intercom panels take the password through a web
 * form or a keypad and people retype it from the screen. 16 characters of a 57-symbol alphabet is ~93 bits.
 */
object RandomPassword {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789"
    private const val DEFAULT_LENGTH = 16

    private val random = SecureRandom()

    fun generate(length: Int = DEFAULT_LENGTH): String = buildString(length) {
        repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }
}
