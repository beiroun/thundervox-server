// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Access of the operator's backend to the service API (every path under `/service/`): one shared secret in the X-SERVICE-TOKEN
 * header. Empty = the service API is switched off (every call is refused), the console keeps working.
 */
@ConfigurationProperties(prefix = "tvx.service")
data class ServiceAccessProperties(
    val token: String = "",
) {
    val enabled: Boolean get() = token.isNotEmpty()

    init {
        require(token.isEmpty() || token.length >= MIN_TOKEN_LENGTH) {
            "TVX_SERVICE_TOKEN must be empty (service API off) or at least $MIN_TOKEN_LENGTH characters"
        }
    }

    companion object {
        const val HEADER = "X-SERVICE-TOKEN"
        private const val MIN_TOKEN_LENGTH = 32
    }
}
