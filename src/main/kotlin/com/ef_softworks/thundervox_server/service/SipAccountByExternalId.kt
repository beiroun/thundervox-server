// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.sipaccount.SipAccountEntity
import com.ef_softworks.thundervox_server.sipaccount.SipAccountInputRules
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.ef_softworks.thundervox_server.sipaccount.SipAccountRepository
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.stereotype.Component

/** The number of an endpoint under the operator's own id - the only way the service API addresses numbers. */
@Component
class SipAccountByExternalId(
    private val sipAccountRepository: SipAccountRepository,
    private val defaultTenant: DefaultTenant,
) {

    fun find(kind: SipAccountKind, externalId: String): SipAccountEntity? =
        sipAccountRepository.findByTenantIdAndKindAndExternalId(
            defaultTenant.id, kind, SipAccountInputRules.requireValidExternalId(externalId)
        )

    fun require(kind: SipAccountKind, externalId: String): SipAccountEntity =
        find(kind, externalId)
            ?: throw NotFoundException("No $kind number with external id $externalId", "Номер с таким внешним id не найден")
}
