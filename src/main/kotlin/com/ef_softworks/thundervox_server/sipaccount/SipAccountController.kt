// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.consoleuser.currentConsoleUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * SIP numbers of panels and app clients for the console. Every role reads; changes take an administrator
 * (SecurityConfig) and are signed in the audit trail by the console user behind the request.
 */
@RestController
@RequestMapping("/sip-accounts")
class SipAccountController(
    private val listSipAccounts: ListSipAccounts,
    private val createSipAccount: CreateSipAccount,
    private val updateSipAccountDetails: UpdateSipAccountDetails,
    private val rotateSipAccountPassword: RotateSipAccountPassword,
    private val blockSipAccount: BlockSipAccount,
    private val deleteSipAccount: DeleteSipAccount,
) {

    @GetMapping
    fun list(): ResponseEntity<BaseApiResponse<List<SipAccountResponse>>> =
        ResponseEntity.ok(BaseApiResponse.ok(listSipAccounts.all()))

    @GetMapping("/{id}")
    fun one(@PathVariable("id") id: Long): ResponseEntity<BaseApiResponse<SipAccountResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(listSipAccounts.one(id)))

    @PostMapping
    fun create(@RequestBody request: CreateSipAccountRequest): ResponseEntity<BaseApiResponse<SipAccountCredentialsResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(createSipAccount.create(request, currentConsoleUser().auditActor)))

    @PutMapping("/{id}")
    fun updateDetails(
        @PathVariable("id") id: Long,
        @RequestBody request: UpdateSipAccountDetailsRequest,
    ): ResponseEntity<BaseApiResponse<SipAccountResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(updateSipAccountDetails.update(id, request, currentConsoleUser().auditActor)))

    @PostMapping("/{id}/password")
    fun rotatePassword(
        @PathVariable("id") id: Long,
        @RequestBody request: RotateSipAccountPasswordRequest,
    ): ResponseEntity<BaseApiResponse<SipAccountCredentialsResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(rotateSipAccountPassword.rotate(id, request, currentConsoleUser().auditActor)))

    @PostMapping("/{id}/block")
    fun block(@PathVariable("id") id: Long): ResponseEntity<BaseApiResponse<SipAccountResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(blockSipAccount.block(id, currentConsoleUser().auditActor)))

    @PostMapping("/{id}/unblock")
    fun unblock(@PathVariable("id") id: Long): ResponseEntity<BaseApiResponse<SipAccountResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(blockSipAccount.unblock(id, currentConsoleUser().auditActor)))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable("id") id: Long): ResponseEntity<BaseApiResponse<Boolean>> {
        deleteSipAccount.delete(id, currentConsoleUser().auditActor)
        return ResponseEntity.ok(BaseApiResponse.ok(true))
    }
}
