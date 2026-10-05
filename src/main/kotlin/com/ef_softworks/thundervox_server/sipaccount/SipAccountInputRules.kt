// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException

/** What an operator may type for a SIP number, its name and its password. */
object SipAccountInputRules {
    const val MAX_NAME_LENGTH = 128
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 64

    /** Digits only: a panel dials the number from its "flat -> phone" table and keypads have no letters. */
    private val numberPattern = Regex("^[0-9]{2,16}$")

    /** Printable ASCII without the space: what every panel web form and keypad can take. */
    private val passwordPattern = Regex("^[\\x21-\\x7E]+$")

    fun requireValidNumber(number: String): String {
        val trimmed = number.trim()
        if (!numberPattern.matches(trimmed)) {
            throw WrongTypeException(
                "SIP number '$trimmed' does not match ${numberPattern.pattern}",
                "Номер: только цифры, от 2 до 16"
            )
        }
        return trimmed
    }

    fun requireValidName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH || trimmed.any { it.isISOControl() }) {
            throw WrongTypeException(
                "SIP account name of length ${trimmed.length} is empty, too long or has control characters",
                "Название: от 1 до $MAX_NAME_LENGTH символов"
            )
        }
        return trimmed
    }

    fun requireValidPassword(password: String): String {
        if (password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH || !passwordPattern.matches(password)) {
            throw WrongTypeException(
                "SIP password of length ${password.length} violates the policy",
                "Пароль: от $MIN_PASSWORD_LENGTH до $MAX_PASSWORD_LENGTH символов, латиница, цифры и знаки без пробелов"
            )
        }
        return password
    }
}
