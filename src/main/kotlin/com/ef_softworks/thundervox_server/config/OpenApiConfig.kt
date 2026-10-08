// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import com.ef_softworks.thundervox_server.service.ServiceAuthentication
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAPI document metadata; the document itself is the source of the console's TypeScript types. */
@Configuration
class OpenApiConfig(private val buildProperties: ObjectProvider<BuildProperties>) {

    @Bean
    fun thundervoxOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("ThunderVox Server")
                .description("Provisioning API of the ThunderVox SIP endpoint platform: console access, SIP numbers of panels and app clients, registrations, audit trail, the integration settings (service tokens, wake push), and the service API the operator's backend provisions endpoints through.")
                .version(buildProperties.ifAvailable?.version ?: "dev")
                .license(License().name("BUSL-1.1").url("https://github.com/beiroun/thundervox-server/blob/release/LICENSE"))
        )
        // The console token from POST /auth/login ("Authorize" in the Swagger UI takes it as is) or, for
        // /service/**, a service token issued on the Integration page, in the X-SERVICE-TOKEN header
        .components(
            Components()
                .addSecuritySchemes(
                    CONSOLE_TOKEN_SCHEME,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                )
                .addSecuritySchemes(
                    SERVICE_TOKEN_SCHEME,
                    SecurityScheme().type(SecurityScheme.Type.APIKEY).`in`(SecurityScheme.In.HEADER).name(ServiceAuthentication.HEADER)
                )
        )
        .addSecurityItem(SecurityRequirement().addList(CONSOLE_TOKEN_SCHEME))
        .addSecurityItem(SecurityRequirement().addList(SERVICE_TOKEN_SCHEME))

    companion object {
        private const val CONSOLE_TOKEN_SCHEME = "console-token"
        private const val SERVICE_TOKEN_SCHEME = "service-token"
    }
}
