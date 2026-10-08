// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.exception.base.InsufficientPrivilegesException
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

/** Authentication of a request that carried a live service token (service API only); the actor is named after the token. */
class ServiceAuthentication(tokenName: String) : AbstractAuthenticationToken(listOf(SimpleGrantedAuthority("ROLE_$ROLE"))) {

    private val actor = AuditActor.serviceToken(tokenName)

    init {
        isAuthenticated = true
    }

    override fun getPrincipal(): AuditActor = actor

    override fun getCredentials(): Any? = null

    override fun getName(): String = actor.login

    companion object {
        /** Spring Security role of the operator's backend; URL rules use hasRole(ROLE). */
        const val ROLE = "SERVICE"
        const val HEADER = "X-SERVICE-TOKEN"
    }
}

/**
 * The service token behind the current request, as the actor of the audit trail. Only reachable through the
 * service URL rules, so an empty context means a rule was loosened by mistake - refused, not guessed.
 */
fun currentServiceActor(): AuditActor =
    (SecurityContextHolder.getContext().authentication as? ServiceAuthentication)?.principal
        ?: throw InsufficientPrivilegesException("No service token in the security context")
