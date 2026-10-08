// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** Housekeeping of the integration data that grows by itself. */
@ConfigurationProperties(prefix = "tvx.integration")
data class IntegrationProperties(
    /** How long rows of the push delivery log are kept; older ones are deleted by the hourly housekeeping. */
    val pushLogRetention: Duration = Duration.ofDays(7),
) {
    init {
        require(!pushLogRetention.isNegative && !pushLogRetention.isZero) { "TVX_PUSH_LOG_RETENTION must be positive" }
    }
}
