// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class ConsoleUserResponse(
    @JsonProperty("id") val id: Long,
    @JsonProperty("login") val login: String,
    @JsonProperty("role") val role: ConsoleRole,
    @JsonProperty("enabled") val enabled: Boolean,
    @JsonProperty("created_at") val createdAt: LocalDateTime,
    @JsonProperty("password_changed_at") val passwordChangedAt: LocalDateTime,
    /** True for the super administrator: its login and password come from the environment, the API cannot edit it. */
    @JsonProperty("managed_by_environment") val managedByEnvironment: Boolean,
) {
    companion object {
        fun from(user: ConsoleUserEntity) = ConsoleUserResponse(
            id = user.id,
            login = user.login,
            role = user.role,
            enabled = user.enabled,
            createdAt = user.createdAt,
            passwordChangedAt = user.passwordChangedAt,
            managedByEnvironment = user.role == ConsoleRole.SUPER_ADMINISTRATOR,
        )
    }
}

data class CreateConsoleUserRequest(
    @JsonProperty("login") val login: String,
    @JsonProperty("role") val role: ConsoleRole,
    /** Empty or absent: the server generates one and returns it once. */
    @JsonProperty("password") val password: String?,
)

/** Both fields optional: only what is present changes. */
data class UpdateConsoleUserRequest(
    @JsonProperty("role") val role: ConsoleRole?,
    @JsonProperty("enabled") val enabled: Boolean?,
)

data class ResetConsoleUserPasswordRequest(
    /** Empty or absent: the server generates one and returns it once. */
    @JsonProperty("password") val password: String?,
)

/** Result of creating a user or resetting a password. */
data class ConsoleUserCredentialsResponse(
    @JsonProperty("user") val user: ConsoleUserResponse,
    /** Only when the server generated the password; never stored, never shown again. */
    @JsonProperty("generated_password") val generatedPassword: String?,
)
