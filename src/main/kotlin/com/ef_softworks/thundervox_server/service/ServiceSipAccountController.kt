// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.sipaccount.ListSipAccounts
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Service API for the operator's backend (X-SERVICE-TOKEN, see SecurityConfig). Numbers are addressed by the
 * endpoint's id in the operator's own system: a panel by its device id, an app client by its subscriber account.
 */
@RestController
@RequestMapping("/service/sip-accounts")
class ServiceSipAccountController(
    private val ensureSipAccount: EnsureSipAccount,
    private val disableSipAccount: DisableSipAccount,
    private val sipAccountByExternalId: SipAccountByExternalId,
    private val listSipAccounts: ListSipAccounts,
) {

    /** Same external id -> same number, always. Creates on the first call, returns the existing account afterwards. */
    @PutMapping("/{kind}/{externalId}")
    fun ensure(
        @PathVariable("kind") kind: SipAccountKind,
        @PathVariable("externalId") externalId: String,
        @RequestBody(required = false) request: EnsureSipAccountRequest?,
    ): ResponseEntity<BaseApiResponse<ServiceSipAccountResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(ensureSipAccount.ensure(kind, externalId, request ?: EnsureSipAccountRequest())))

    /** Out of service, not deleted: the number stays with the external id and a later PUT brings it back. */
    @DeleteMapping("/{kind}/{externalId}")
    fun disable(
        @PathVariable("kind") kind: SipAccountKind,
        @PathVariable("externalId") externalId: String,
    ): ResponseEntity<BaseApiResponse<ServiceSipAccountResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(disableSipAccount.disable(kind, externalId)))

    /** Is the endpoint registered right now - the push gateway asks before waking a device that is already up. */
    @GetMapping("/{kind}/{externalId}/registration")
    fun registration(
        @PathVariable("kind") kind: SipAccountKind,
        @PathVariable("externalId") externalId: String,
    ): ResponseEntity<BaseApiResponse<ServiceRegistrationResponse>> {
        val account = listSipAccounts.one(sipAccountByExternalId.require(kind, externalId).id)
        return ResponseEntity.ok(
            BaseApiResponse.ok(
                ServiceRegistrationResponse(
                    username = account.username,
                    online = account.registration?.online ?: false,
                    registration = account.registration,
                )
            )
        )
    }
}
