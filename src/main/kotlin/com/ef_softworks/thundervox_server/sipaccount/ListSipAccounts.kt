// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.registration.RegistrationStatusQuery
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/** SIP numbers with their live registration, newest first. Filtering and search happen in the console. */
@Component
class ListSipAccounts(
    private val sipAccountRepository: SipAccountRepository,
    private val registrationStatusQuery: RegistrationStatusQuery,
    private val defaultTenant: DefaultTenant,
) {

    @Transactional(readOnly = true)
    fun all(): List<SipAccountResponse> {
        val registrations = registrationStatusQuery.latestByUsername()
        val now = LocalDateTime.now()
        return sipAccountRepository.findAllByTenantIdOrderByCreatedAtDesc(defaultTenant.id)
            .map { SipAccountResponse.from(it, registrations[it.username], now) }
    }

    @Transactional(readOnly = true)
    fun one(id: Long): SipAccountResponse {
        val account = sipAccountRepository.findSipAccount(id)
        return SipAccountResponse.from(account, registrationStatusQuery.latestByUsername()[account.username], LocalDateTime.now())
    }
}

internal fun SipAccountRepository.findSipAccount(id: Long): SipAccountEntity =
    findById(id).orElseThrow { NotFoundException("SIP account $id not found", "Номер не найден") }
