// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.internal

import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/** Authentication of a request that carried the SIP core's shared token (internal API only). */
class CoreAuthentication : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_$ROLE"))) {

    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): String = NAME

    override fun getCredentials(): Any? = null

    override fun getName(): String = NAME

    companion object {
        /** Spring Security role of the core; URL rules use hasRole(ROLE). */
        const val ROLE = "CORE"
        private const val NAME = "sip-core"
    }
}
