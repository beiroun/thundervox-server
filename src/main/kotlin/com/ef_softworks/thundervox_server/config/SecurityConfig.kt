// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import com.ef_softworks.thundervox_server.access.AuthenticateConsoleToken
import com.ef_softworks.thundervox_server.consoleuser.ConsoleRole
import com.ef_softworks.thundervox_server.service.ServiceAuthentication
import com.ef_softworks.thundervox_server.service.ServiceTokenAuthenticationFilter
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
 * Paths under `/service/` are the operator backend's API: only the shared X-SERVICE-TOKEN opens them, a console token does not.
 * Everything not listed is denied, so a new endpoint is closed until a rule names it.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(ConsoleAccessProperties::class, SipProperties::class, ServiceAccessProperties::class)
class SecurityConfig(
    private val consoleAccessProperties: ConsoleAccessProperties,
    private val serviceAccessProperties: ServiceAccessProperties,
) {

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        authenticateConsoleToken: AuthenticateConsoleToken,
        apiSecurityErrorResponses: ApiSecurityErrorResponses,
    ): SecurityFilterChain {
        val administrators = arrayOf(ConsoleRole.ADMINISTRATOR.name, ConsoleRole.SUPER_ADMINISTRATOR.name)
        val serviceTokenFilter = ServiceTokenAuthenticationFilter(serviceAccessProperties, apiSecurityErrorResponses)
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

                // Before the GET-for-everyone rule: a console user must not read the service API either
                authorize("/service/**", hasRole(ServiceAuthentication.ROLE))
                authorize("/console-users/**", hasAnyRole(*administrators))
                authorize(HttpMethod.GET, "/**", authenticated)
                authorize("/sip-accounts/**", hasAnyRole(*administrators))
                authorize(anyRequest, denyAll)
            }
            addFilterBefore<BearerTokenAuthenticationFilter>(serviceTokenFilter)
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
