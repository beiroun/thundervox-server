// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.access

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserRepository
import com.ef_softworks.thundervox_server.consoleuser.ConsoleUserResponse
import com.ef_softworks.thundervox_server.consoleuser.currentConsoleUser
import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Console session: log in (public), and who am I (any logged-in user). */
@RestController
@RequestMapping("/auth")
class AuthController(
    private val logInToConsole: LogInToConsole,
    private val consoleUserRepository: ConsoleUserRepository,
) {

    @PostMapping("/login")
    fun logIn(@RequestBody request: LoginRequest): ResponseEntity<BaseApiResponse<LoginResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(logInToConsole.logIn(request)))

    @GetMapping("/me")
    @Transactional(readOnly = true)
    fun me(): ResponseEntity<BaseApiResponse<ConsoleUserResponse>> {
        val consoleUser = currentConsoleUser()
        val user = consoleUserRepository.findById(consoleUser.id).orElseThrow {
            NotFoundException("Console user ${consoleUser.id} vanished during the request", "Пользователь консоли не найден")
        }
        return ResponseEntity.ok(BaseApiResponse.ok(ConsoleUserResponse.from(user)))
    }
}
