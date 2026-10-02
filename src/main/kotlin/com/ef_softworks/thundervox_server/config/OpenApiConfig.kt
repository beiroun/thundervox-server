// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** OpenAPI document metadata; the document itself is the source of the console's TypeScript types. */
@Configuration
class OpenApiConfig(private val buildProperties: ObjectProvider<BuildProperties>) {

    @Bean
    fun thundervoxOpenApi(): OpenAPI = OpenAPI().info(
        Info()
            .title("ThunderVox Server")
            .description("Provisioning API of the ThunderVox SIP endpoint platform: devices, app clients, SIP accounts, registrations, live calls.")
            .version(buildProperties.ifAvailable?.version ?: "dev")
            .license(License().name("BUSL-1.1").url("https://github.com/beiroun/thundervox-server/blob/release/LICENSE"))
    )
}
