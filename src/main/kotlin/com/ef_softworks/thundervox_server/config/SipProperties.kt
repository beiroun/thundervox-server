// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** SIP-side settings the server needs to issue credentials the core accepts. */
@ConfigurationProperties(prefix = "tvx.sip")
data class SipProperties(
    /**
     * Digest realm = the SIP domain devices register to (TVX_SIP_DOMAIN in the core's local.cfg). It is hashed into
     * every HA1, so the core rejects every password issued under another realm.
     */
    val realm: String,
) {
    init {
        require(realm.isNotBlank()) { "TVX_SIP_REALM is empty: set it to the SIP domain (TVX_SIP_DOMAIN of the core)" }
    }
}
