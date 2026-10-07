// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.config.SipProperties
import com.ef_softworks.thundervox_server.sipaccount.BlockSipAccount
import com.ef_softworks.thundervox_server.sipaccount.ListSipAccounts
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * The operator's backend takes an endpoint out of service (subscriber left, panel dismantled). The number is
 * blocked, never deleted: it stays bound to the external id, and the next PUT for that id puts it back. Idempotent.
 */
@Component
class DisableSipAccount(
    private val sipAccountByExternalId: SipAccountByExternalId,
    private val blockSipAccount: BlockSipAccount,
    private val listSipAccounts: ListSipAccounts,
    private val sipProperties: SipProperties,
) {

    @Transactional
    fun disable(kind: SipAccountKind, externalId: String): ServiceSipAccountResponse {
        val account = sipAccountByExternalId.require(kind, externalId)
        if (account.enabled) {
            blockSipAccount.block(account.id, AuditActor.OPERATOR_BACKEND)
        }
        return ServiceSipAccountResponse.from(listSipAccounts.one(account.id), sipProperties.realm, password = null, created = false)
    }
}
