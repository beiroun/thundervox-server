// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.registration.RegistrationStateStore
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * SIP numbers with their registration journal (online now, last seen), newest first. Filtering and search happen
 * in the console. The journal is the presence sampler's view of the core's table, at most a tick (10 s) behind.
 */
@Component
class ListSipAccounts(
    private val sipAccountRepository: SipAccountRepository,
    private val registrationStateStore: RegistrationStateStore,
    private val defaultTenant: DefaultTenant,
) {

    @Transactional(readOnly = true)
    fun all(): List<SipAccountResponse> {
        val registrations = registrationStateStore.byUsername()
        return sipAccountRepository.findAllByTenantIdOrderByCreatedAtDesc(defaultTenant.id)
            .map { SipAccountResponse.from(it, registrations[it.username]) }
    }

    @Transactional(readOnly = true)
    fun one(id: Long): SipAccountResponse {
        val account = sipAccountRepository.findSipAccount(id)
        return SipAccountResponse.from(account, registrationStateStore.one(account.username))
    }
}

internal fun SipAccountRepository.findSipAccount(id: Long): SipAccountEntity =
    findById(id).orElseThrow { NotFoundException("SIP account $id not found", "Номер не найден") }
