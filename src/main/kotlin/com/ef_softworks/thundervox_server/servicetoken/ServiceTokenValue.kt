// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Shape of a service token: a fixed prefix that makes it recognisable in a config file or a log, then 40 random
 * characters of a 62-symbol alphabet (~238 bits). Only the SHA-256 of the whole value is stored, so a database
 * dump gives away nothing; presented tokens are looked up by that hash.
 */
object ServiceTokenValue {
    const val VISIBLE_PREFIX_LENGTH = 12

    private const val PREFIX = "tvx_"
    private const val RANDOM_LENGTH = 40
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    private val random = SecureRandom()

    fun generate(): String = buildString(PREFIX.length + RANDOM_LENGTH) {
        append(PREFIX)
        repeat(RANDOM_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun visiblePrefix(value: String): String = value.take(VISIBLE_PREFIX_LENGTH)

    /** Cheap shape check before touching the database: a console JWT or garbage in the header never gets a query. */
    fun looksLikeToken(value: String): Boolean =
        value.length == PREFIX.length + RANDOM_LENGTH && value.startsWith(PREFIX) && value.all { it in ALPHABET || it == '_' }
}
