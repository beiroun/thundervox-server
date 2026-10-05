// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Console users. The URL rules admit administrators and the super administrator; finer rules live in the use cases. */
@RestController
@RequestMapping("/console-users")
class ConsoleUserController(
    private val consoleUserRepository: ConsoleUserRepository,
    private val defaultTenant: DefaultTenant,
    private val createConsoleUser: CreateConsoleUser,
    private val updateConsoleUser: UpdateConsoleUser,
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): ResponseEntity<BaseApiResponse<List<ConsoleUserResponse>>> =
        ResponseEntity.ok(
            BaseApiResponse.ok(
                consoleUserRepository.findAllByTenantIdOrderByLoginAsc(defaultTenant.id).map(ConsoleUserResponse::from)
            )
        )

    @PostMapping
    fun create(@RequestBody request: CreateConsoleUserRequest): ResponseEntity<BaseApiResponse<ConsoleUserCredentialsResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(createConsoleUser.create(request)))

    @PutMapping("/{id}")
    fun update(
        @PathVariable("id") id: Long,
        @RequestBody request: UpdateConsoleUserRequest,
    ): ResponseEntity<BaseApiResponse<ConsoleUserResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(updateConsoleUser.update(id, request)))

    @PostMapping("/{id}/password")
    fun resetPassword(
        @PathVariable("id") id: Long,
        @RequestBody request: ResetConsoleUserPasswordRequest,
    ): ResponseEntity<BaseApiResponse<ConsoleUserCredentialsResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(updateConsoleUser.resetPassword(id, request)))
}
