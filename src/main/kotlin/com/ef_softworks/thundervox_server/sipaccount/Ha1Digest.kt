// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import java.security.MessageDigest
import java.util.HexFormat

/**
 * Digest HA1 values exactly as the core's auth_db reads them with calculate_ha1=0 (RFC 2617, MD5).
 *
 * The realm is part of the hash: an account only authenticates against the realm it was hashed with, so a change of
 * the SIP domain makes every stored password useless until it is issued again.
 */
object Ha1Digest {

    /** Used when the device sends a bare username in its credentials (the normal case). */
    fun ha1(username: String, realm: String, password: String): String = md5Hex("$username:$realm:$password")

    /** Used when the device sends "user@domain" as its username (auth_db password_column_2). */
    fun ha1b(username: String, realm: String, password: String): String = md5Hex("$username@$realm:$realm:$password")

    private fun md5Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8)))
}
