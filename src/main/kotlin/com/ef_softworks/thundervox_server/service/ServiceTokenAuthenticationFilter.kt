// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.config.ApiSecurityErrorResponses
import com.ef_softworks.thundervox_server.servicecall.ServiceCaller
import com.ef_softworks.thundervox_server.servicecall.ServiceCallerAuth
import com.ef_softworks.thundervox_server.servicetoken.AuthenticateServiceToken
import com.ef_softworks.thundervox_server.servicetoken.ServiceTokenValue
import com.ef_softworks.thundervox_server.util.logWarn
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Lets the operator's backend into the paths under `/service/` with a live token from the Integration page in the
 * X-SERVICE-TOKEN header.
 *
 * Only service paths are looked at. A request without the header passes through unauthenticated and is refused
 * by the URL rule (401); an unknown or revoked token is refused here with the same status, so a probe cannot tell
 * one case from the other by anything but the message. Either way the request carries a [ServiceCaller] for the
 * call journal. Not a Spring bean on purpose: a Filter bean would also be registered with the servlet container and
 * run outside the security chain.
 */
class ServiceTokenAuthenticationFilter(
    private val authenticateServiceToken: AuthenticateServiceToken,
    private val apiSecurityErrorResponses: ApiSecurityErrorResponses,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("${request.contextPath}/service/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val presentedToken = request.getHeader(ServiceAuthentication.HEADER)
        if (presentedToken == null) {
            // No token at all: the URL rule answers with 401
            request.setAttribute(ServiceCaller.ATTRIBUTE, ServiceCaller.NO_TOKEN)
            filterChain.doFilter(request, response)
            return
        }
        val trimmedToken = presentedToken.trim()
        val authentication = authenticateServiceToken.authenticate(trimmedToken)
        if (authentication == null) {
            logWarn("Service API call ${request.method} ${request.requestURI} from ${request.remoteAddr} with an unknown or revoked token")
            // Only a token-shaped value leaves its prefix in the journal: anything else may be somebody's secret
            val prefix = trimmedToken.takeIf(ServiceTokenValue::looksLikeToken)?.let(ServiceTokenValue::visiblePrefix)
            request.setAttribute(ServiceCaller.ATTRIBUTE, ServiceCaller(ServiceCallerAuth.UNKNOWN_TOKEN, null, null, prefix))
            apiSecurityErrorResponses.refuseServiceCall(response, "Unknown or revoked service token")
            return
        }
        request.setAttribute(
            ServiceCaller.ATTRIBUTE,
            ServiceCaller(ServiceCallerAuth.TOKEN, authentication.tokenId, authentication.tokenName, ServiceTokenValue.visiblePrefix(trimmedToken)),
        )
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
    }
}
