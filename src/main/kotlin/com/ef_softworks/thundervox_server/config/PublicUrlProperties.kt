// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * The address the operator's backend reaches this server at (https://server.example.com/api/v1): the console shows
 * it next to the ready-made endpoint links. It cannot be derived from a request - the console calls through its own
 * nginx, so the forwarded host would be the console's name, not the server's.
 */
@ConfigurationProperties(prefix = "tvx.public")
data class PublicUrlProperties(
    val apiUrl: String = "",
) {
    /** Without a trailing slash, so paths can be appended as "/service/..."; empty when not configured. */
    val apiBaseUrl: String get() = apiUrl.trim().trimEnd('/')
}
