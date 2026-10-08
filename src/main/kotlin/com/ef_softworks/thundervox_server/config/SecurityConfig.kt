// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import com.ef_softworks.thundervox_server.access.AuthenticateConsoleToken
import com.ef_softworks.thundervox_server.consoleuser.ConsoleRole
import com.ef_softworks.thundervox_server.internal.CoreAuthentication
import com.ef_softworks.thundervox_server.internal.CoreTokenAuthenticationFilter
import com.ef_softworks.thundervox_server.service.ServiceAuthentication
import com.ef_softworks.thundervox_server.service.ServiceTokenAuthenticationFilter
import com.ef_softworks.thundervox_server.servicetoken.AuthenticateServiceToken
import com.nimbusds.jose.jwk.source.ImmutableSecret
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter
import org.springframework.security.web.SecurityFilterChain
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Who may call what.
 *
 * Stateless bearer tokens (no cookies, hence no CSRF). Public: login, server identity, health and the OpenAPI
 * document. Any role may read; changing SIP numbers takes an administrator; the console-user API is for
 * administrators and the super administrator, with the finer "who may manage whom" rules in the use cases.
 * The Integration page is for administrators, and its changes (push settings, tokens) for the super administrator.
 * Paths under `/service/` are the operator backend's API: only a live service token (X-SERVICE-TOKEN) opens them.
 * Paths under `/internal/` are the SIP core's: only its shared token (X-CORE-TOKEN) opens them, and the proxies in
 * front of the server never forward them. Everything not listed is denied, so a new endpoint is closed until a
 * rule names it.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ConsoleAccessProperties::class, SipProperties::class, CoreAccessProperties::class)
class SecurityConfig(
    private val consoleAccessProperties: ConsoleAccessProperties,
    private val coreAccessProperties: CoreAccessProperties,
) {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticateConsoleToken: AuthenticateConsoleToken,
        authenticateServiceToken: AuthenticateServiceToken,
        apiSecurityErrorResponses: ApiSecurityErrorResponses,
    ): SecurityFilterChain {
        val administrators = arrayOf(ConsoleRole.ADMINISTRATOR.name, ConsoleRole.SUPER_ADMINISTRATOR.name)
        val superAdministrator = ConsoleRole.SUPER_ADMINISTRATOR.name
        val serviceTokenFilter = ServiceTokenAuthenticationFilter(authenticateServiceToken, apiSecurityErrorResponses)
        val coreTokenFilter = CoreTokenAuthenticationFilter(coreAccessProperties, apiSecurityErrorResponses)
        http {
            csrf { disable() }
            httpBasic { disable() }
            formLogin { disable() }
            logout { disable() }
            sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
            authorizeHttpRequests {
                // CORS preflight carries no token; the MVC CORS policy (CorsConfig) answers it
                authorize(HttpMethod.OPTIONS, "/**", permitAll)
                authorize(HttpMethod.POST, "/auth/login", permitAll)
                authorize("/info", permitAll)
                authorize("/system/**", permitAll)
                authorize("/openapi/**", permitAll)
                authorize("/docs/**", permitAll)
                authorize("/swagger-ui/**", permitAll)
                authorize("/error", permitAll)

                // Before the GET-for-everyone rule: neither a console user nor the core may read the other APIs
                authorize("/service/**", hasRole(ServiceAuthentication.ROLE))
                authorize("/internal/**", hasRole(CoreAuthentication.ROLE))
                // The Integration page: readers never, administrators read and test, the super administrator changes
                authorize(HttpMethod.PUT, "/integration/push", hasRole(superAdministrator))
                authorize(HttpMethod.POST, "/integration/tokens", hasRole(superAdministrator))
                authorize(HttpMethod.DELETE, "/integration/tokens/*", hasRole(superAdministrator))
                authorize("/integration/**", hasAnyRole(*administrators))
                authorize("/console-users/**", hasAnyRole(*administrators))
                authorize(HttpMethod.GET, "/**", authenticated)
                authorize("/sip-accounts/**", hasAnyRole(*administrators))
                authorize(anyRequest, denyAll)
            }
            addFilterBefore<BearerTokenAuthenticationFilter>(serviceTokenFilter)
            addFilterBefore<BearerTokenAuthenticationFilter>(coreTokenFilter)
            oauth2ResourceServer {
                jwt { jwtAuthenticationConverter = authenticateConsoleToken }
                authenticationEntryPoint = apiSecurityErrorResponses
            }
            exceptionHandling {
                authenticationEntryPoint = apiSecurityErrorResponses
                accessDeniedHandler = apiSecurityErrorResponses
            }
        }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun consoleTokenKey(): SecretKey =
        SecretKeySpec(consoleAccessProperties.tokenSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")

    @Bean
    fun jwtEncoder(consoleTokenKey: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret(consoleTokenKey))

    @Bean
    fun jwtDecoder(consoleTokenKey: SecretKey): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(consoleTokenKey).macAlgorithm(MacAlgorithm.HS256).build()
}
