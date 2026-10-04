// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Browser origins allowed to call this API cross-site.
 *
 * The console reaches the server through its own nginx (same-origin `/api/v1`), so cross-site access is NOT
 * the normal path - it is for a browser client served from another name: the console on its own host
 * (`console.<domain>` calling `server.<domain>` directly), the Swagger UI opened on the server's own name,
 * or a developer's vite server. Empty list = no cross-site browser access at all, which is the safe default
 * and breaks nothing in the same-origin deployment.
 */
@ConfigurationProperties(prefix = "tvx.cors")
data class CorsProperties(
    /** Exact origins, scheme included (`https://console.example.com`); never a wildcard, see [CorsConfig]. */
    val allowedOrigins: List<String> = emptyList(),
)

/**
 * Global CORS policy.
 *
 * Credentials are allowed because the token refresh of the console will ride in a cookie, and that rules out
 * the `*` origin: the browser rejects a wildcard together with credentials. So the origins stay an explicit
 * allowlist from the environment - a deployment that forgets to set it loses cross-site access, not its
 * isolation.
 */
@Configuration
@EnableConfigurationProperties(CorsProperties::class)
class CorsConfig(private val corsProperties: CorsProperties) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        if (corsProperties.allowedOrigins.isEmpty()) {
            return
        }
        registry.addMapping("/**")
            .allowedOrigins(*corsProperties.allowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With")
            .allowCredentials(true)
            // Preflight answers are cached for an hour; shorter means a preflight per unique request shape
            .maxAge(3600)
    }
}
