// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException

/** What a token may be called: it ends up as the actor "token:<name>" in the audit trail (a 64-character column). */
object ServiceTokenInputRules {
    const val MAX_NAME_LENGTH = 48

    private val namePattern = Regex("^[\\p{L}\\p{N} ._-]{2,$MAX_NAME_LENGTH}$")

    fun requireValidName(name: String): String {
        val trimmed = name.trim()
        if (!namePattern.matches(trimmed)) {
            throw WrongTypeException(
                "Service token name '$trimmed' does not match ${namePattern.pattern}",
                "Название токена: от 2 до $MAX_NAME_LENGTH символов – буквы, цифры, пробел, '.', '_' или '-'"
            )
        }
        return trimmed
    }
}
