// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.internal

import com.ef_softworks.thundervox_server.config.ApiSecurityErrorResponses
import com.ef_softworks.thundervox_server.config.CoreAccessProperties
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import java.security.MessageDigest

/**
 * Lets the SIP core into the paths under `/internal/` with the shared X-CORE-TOKEN header (TVX_CORE_TOKEN on both
 * sides). The edge proxy and the console's nginx answer 404 for these paths, so only loopback traffic gets this far;
 * the token is the second lock. Same shape as the service token filter and, like it, not a Spring bean.
 */
class CoreTokenAuthenticationFilter(
    private val coreAccessProperties: CoreAccessProperties,
    private val apiSecurityErrorResponses: ApiSecurityErrorResponses,
) : OncePerRequestFilter() {

    init {
        if (coreAccessProperties.enabled) {
            logInfo("Internal API enabled: /internal/** accepts the ${CoreAccessProperties.HEADER} header")
        } else {
            logWarn("Internal API disabled: TVX_CORE_TOKEN is not set, the core cannot request wake pushes")
        }
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("${request.contextPath}/internal/")

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val presentedToken = request.getHeader(CoreAccessProperties.HEADER)
        if (presentedToken == null) {
            // No token at all: the URL rule answers with 401
            filterChain.doFilter(request, response)
            return
        }
        if (!coreAccessProperties.enabled) {
            logWarn("Internal API call ${request.method} ${request.requestURI} from ${request.remoteAddr} while TVX_CORE_TOKEN is not set")
            apiSecurityErrorResponses.refuseCoreCall(response, "Internal API is disabled: TVX_CORE_TOKEN is not set")
            return
        }
        val expected = coreAccessProperties.token.toByteArray(Charsets.UTF_8)
        if (!MessageDigest.isEqual(presentedToken.trim().toByteArray(Charsets.UTF_8), expected)) {
            logWarn("Internal API call ${request.method} ${request.requestURI} from ${request.remoteAddr} with a wrong token")
            apiSecurityErrorResponses.refuseCoreCall(response, "Wrong core token")
            return
        }
        SecurityContextHolder.getContext().authentication = CoreAuthentication()
        filterChain.doFilter(request, response)
    }
}
