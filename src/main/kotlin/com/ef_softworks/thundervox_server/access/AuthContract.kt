// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.access

import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserResponse
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

data class LoginRequest(
    @JsonProperty("login") val login: String,
    @JsonProperty("password") val password: String,
)

data class LoginResponse(
    /** Bearer token for the Authorization header of every further request. */
    @JsonProperty("token") val token: String,
    @JsonProperty("expires_at") val expiresAt: LocalDateTime,
    @JsonProperty("user") val user: ConsoleUserResponse,
)
