// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.config.PublicUrlProperties
import com.ef_softworks.thundervox_server.config.SipProperties
import com.ef_softworks.thundervox_server.push.PushDeliveryLog
import com.ef_softworks.thundervox_server.push.PushDeliveryPageResponse
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The Integration page: push gateway settings, the public address for the endpoint links, the delivery log.
 * Administrators read and may send a test push; changing the settings takes the super administrator (URL rules and
 * the use case both say so). Service tokens have their own controller under /integration/tokens.
 */
@RestController
@RequestMapping("/integration")
@EnableConfigurationProperties(PublicUrlProperties::class)
class IntegrationController(
    private val pushSettings: PushSettings,
    private val updatePushSettings: UpdatePushSettings,
    private val sendTestPush: SendTestPush,
    private val pushDeliveryLog: PushDeliveryLog,
    private val publicUrlProperties: PublicUrlProperties,
    private val sipProperties: SipProperties,
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun overview(): ResponseEntity<BaseApiResponse<IntegrationResponse>> =
        ResponseEntity.ok(
            BaseApiResponse.ok(
                IntegrationResponse(
                    apiBaseUrl = publicUrlProperties.apiBaseUrl,
                    sipDomain = sipProperties.realm,
                    push = PushSettingsResponse.from(pushSettings.current()),
                )
            )
        )

    @PutMapping("/push")
    fun updatePush(@RequestBody request: UpdatePushSettingsRequest): ResponseEntity<BaseApiResponse<PushSettingsResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(updatePushSettings.update(request)))

    @PostMapping("/push/test")
    fun testPush(@RequestBody request: PushTestRequest): ResponseEntity<BaseApiResponse<PushTestResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(sendTestPush.send(request)))

    @GetMapping("/push/deliveries")
    fun deliveries(
        @RequestParam("page", required = false, defaultValue = "0") page: Int,
        @RequestParam("size", required = false, defaultValue = "50") size: Int,
    ): ResponseEntity<BaseApiResponse<PushDeliveryPageResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(pushDeliveryLog.page(page, size)))
}
