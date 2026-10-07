// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.audit.AuditActor
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority

/** Authentication of a request that carried the operator backend's shared token (service API only). */
class ServiceAuthentication : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_$ROLE"))) {

    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): AuditActor = AuditActor.OPERATOR_BACKEND

    override fun getCredentials(): Any? = null

    override fun getName(): String = AuditActor.OPERATOR_BACKEND.login

    companion object {
        /** Spring Security role of the operator's backend; URL rules use hasRole(ROLE). */
        const val ROLE = "SERVICE"
    }
}
