// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Access of the SIP core to the internal API (every path under `/internal/`): one shared secret in the X-CORE-TOKEN
 * header, the same value the core carries as TVX_CORE_TOKEN in its local.cfg. Empty = the internal API is switched
 * off (every call is refused), which only matters once the core runs with TVX_PUSH_WAIT.
 */
@ConfigurationProperties(prefix = "tvx.core")
data class CoreAccessProperties(
    val token: String = "",
) {
    val enabled: Boolean get() = token.isNotEmpty()

    init {
        require(token.isEmpty() || token.length >= MIN_TOKEN_LENGTH) {
            "TVX_CORE_TOKEN must be empty (internal API off) or at least $MIN_TOKEN_LENGTH characters"
        }
    }

    companion object {
        const val HEADER = "X-CORE-TOKEN"
        private const val MIN_TOKEN_LENGTH = 32
    }
}
