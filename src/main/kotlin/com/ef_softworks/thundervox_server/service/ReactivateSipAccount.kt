// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.config.SipProperties
import com.ef_softworks.thundervox_server.sipaccount.BlockSipAccount
import com.ef_softworks.thundervox_server.sipaccount.ListSipAccounts
import com.ef_softworks.thundervox_server.sipaccount.RotateSipAccountPassword
import com.ef_softworks.thundervox_server.sipaccount.RotateSipAccountPasswordRequest
import com.ef_softworks.thundervox_server.sipaccount.SipAccountRepository
import com.ef_softworks.thundervox_server.sipaccount.UpdateSipAccountDetails
import com.ef_softworks.thundervox_server.sipaccount.UpdateSipAccountDetailsRequest
import com.ef_softworks.thundervox_server.sipaccount.findSipAccount
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * An endpoint the operator's backend already provisioned asks for its account again (new phone, reinstalled app,
 * resident moved in under the same subscriber account): the existing number goes back in service, keeps its
 * value, and gets a new password only when asked. Every step is the same console operation, signed by the
 * operator backend in the audit trail, and all of them commit together.
 */
@Component
class ReactivateSipAccount(
    private val sipAccountRepository: SipAccountRepository,
    private val blockSipAccount: BlockSipAccount,
    private val updateSipAccountDetails: UpdateSipAccountDetails,
    private val rotateSipAccountPassword: RotateSipAccountPassword,
    private val listSipAccounts: ListSipAccounts,
    private val sipProperties: SipProperties,
) {

    @Transactional
    fun reactivate(id: Long, request: EnsureSipAccountRequest): ServiceSipAccountResponse {
        val account = sipAccountRepository.findSipAccount(id)
        val actor = currentServiceActor()

        if (!account.enabled) {
            blockSipAccount.unblock(account.id, actor)
        }
        val name = request.name?.trim()?.takeIf { it.isNotEmpty() }
        if (name != null && name != account.name) {
            updateSipAccountDetails.update(account.id, UpdateSipAccountDetailsRequest(name = name, externalId = account.externalId), actor)
        }
        val password = if (request.rotatePassword) {
            rotateSipAccountPassword.rotate(account.id, RotateSipAccountPasswordRequest(password = null), actor).generatedPassword
        } else {
            null
        }

        logInfo("Service API: ${account.kind} ${account.externalId} served the existing number ${account.username}, password ${if (password != null) "rotated" else "kept"}")
        return ServiceSipAccountResponse.from(listSipAccounts.one(account.id), sipProperties.realm, password, created = false)
    }
}
