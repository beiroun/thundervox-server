// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException

/** What a console login and password may look like. The same rules guard the API and the environment. */
object ConsoleUserInputRules {
    const val MIN_PASSWORD_LENGTH = 10
    const val MAX_PASSWORD_LENGTH = 128
    /** The super administrator is the most powerful account and it lives in a file on the host: stricter. */
    const val MIN_SUPER_ADMINISTRATOR_PASSWORD_LENGTH = 12

    private val loginPattern = Regex("^[A-Za-z0-9._-]{3,64}$")

    fun isValidLogin(login: String): Boolean = loginPattern.matches(login)

    fun requireValidLogin(login: String): String {
        val trimmed = login.trim()
        if (!isValidLogin(trimmed)) {
            throw WrongTypeException(
                "Console login '$trimmed' does not match ${loginPattern.pattern}",
                "Логин: от 3 до 64 символов – латинские буквы, цифры, точка, '_' или '-'"
            )
        }
        return trimmed
    }

    fun requireValidPassword(password: String): String {
        if (password.length !in MIN_PASSWORD_LENGTH..MAX_PASSWORD_LENGTH || password.isBlank()) {
            throw WrongTypeException(
                "Console password length ${password.length} is outside $MIN_PASSWORD_LENGTH..$MAX_PASSWORD_LENGTH",
                "Пароль: от $MIN_PASSWORD_LENGTH до $MAX_PASSWORD_LENGTH символов"
            )
        }
        return password
    }
}
