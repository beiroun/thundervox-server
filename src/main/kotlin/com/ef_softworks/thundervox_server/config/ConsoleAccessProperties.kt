// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserInputRules
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Who may enter the console and for how long a login lasts. Checked at start-up: a weak setup never boots. */
@ConfigurationProperties(prefix = "tvx.console")
data class ConsoleAccessProperties(
    /** HMAC key of the bearer tokens. Changing it invalidates every issued token. */
    val tokenSecret: String,
    val tokenTtl: Duration,
    val superAdministrator: SuperAdministratorProperties,
) {
    init {
        require(tokenSecret.length >= MIN_TOKEN_SECRET_LENGTH) {
            "TVX_JWT_SECRET must be at least $MIN_TOKEN_SECRET_LENGTH characters (HS256 needs a 256-bit key)"
        }
        require(!tokenTtl.isNegative && !tokenTtl.isZero) { "TVX_CONSOLE_TOKEN_TTL must be positive" }
    }

    companion object {
        private const val MIN_TOKEN_SECRET_LENGTH = 32
    }
}

/** The one super administrator; the server keeps its account in step with these values on every start. */
data class SuperAdministratorProperties(
    val login: String,
    val password: String,
) {
    init {
        require(ConsoleUserInputRules.isValidLogin(login)) {
            "TVX_SUPERADMIN_LOGIN must be 3-64 characters of latin letters, digits, '.', '_' or '-'"
        }
        require(password.length >= ConsoleUserInputRules.MIN_SUPER_ADMINISTRATOR_PASSWORD_LENGTH) {
            "TVX_SUPERADMIN_PASSWORD must be at least ${ConsoleUserInputRules.MIN_SUPER_ADMINISTRATOR_PASSWORD_LENGTH} characters"
        }
    }
}
