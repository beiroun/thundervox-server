// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.config.ApiSecurityErrorResponses
import com.ef_softworks.thundervox_server.config.ServiceAccessProperties
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Lets the operator's backend into the paths under `/service/` with the shared X-SERVICE-TOKEN header.
 *
 * Only service paths are looked at. A request without the header passes through unauthenticated and is refused
 * by the URL rule (401); a wrong token is refused here with the same status, so a probe cannot tell a wrong token
 * from a missing one by anything but the message. The comparison is constant-time. Not a Spring bean on purpose:
 * a Filter bean would also be registered with the servlet container and run outside the security chain.
 */
class ServiceTokenAuthenticationFilter(
    private val serviceAccessProperties: ServiceAccessProperties,
    private val apiSecurityErrorResponses: ApiSecurityErrorResponses,
) : OncePerRequestFilter() {

    init {
        if (serviceAccessProperties.enabled) {
            logInfo("Service API enabled: /service/** accepts the ${ServiceAccessProperties.HEADER} header")
        } else {
            logWarn("Service API disabled: TVX_SERVICE_TOKEN is not set, every /service/** call will be refused")
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("${request.contextPath}/service/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val presentedToken = request.getHeader(ServiceAccessProperties.HEADER)
        if (presentedToken == null) {
            // No token at all: the URL rule answers with 401
            filterChain.doFilter(request, response)
            return
        }
        if (!serviceAccessProperties.enabled) {
            logWarn("Service API call ${request.method} ${request.requestURI} from ${request.remoteAddr} while TVX_SERVICE_TOKEN is not set")
            apiSecurityErrorResponses.refuseServiceCall(response, "Service API is disabled: TVX_SERVICE_TOKEN is not set")
            return
        }
        val expected = serviceAccessProperties.token.toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(presentedToken.toByteArray(Charsets.UTF_8), expected)) {
            logWarn("Service API call ${request.method} ${request.requestURI} from ${request.remoteAddr} with a wrong token")
            apiSecurityErrorResponses.refuseServiceCall(response, "Wrong service token")
            return
        }
        SecurityContextHolder.getContext().authentication = ServiceAuthentication()
        filterChain.doFilter(request, response)
    }
}
