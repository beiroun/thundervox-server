// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.platform

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.info.BuildProperties
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Which server and version answers: the console shows it, deploys verify it. */
@RestController
@RequestMapping("/info")
class PlatformInfoController(private val buildProperties: ObjectProvider<BuildProperties>) {

    @GetMapping
    fun info(): ResponseEntity<BaseApiResponse<PlatformInfoResponse>> =
        ResponseEntity.ok(
            BaseApiResponse.ok(
                PlatformInfoResponse(
                    name = "thundervox-server",
                    version = buildProperties.ifAvailable?.version ?: "dev",
                    license = "BUSL-1.1"
                )
            )
        )
}
