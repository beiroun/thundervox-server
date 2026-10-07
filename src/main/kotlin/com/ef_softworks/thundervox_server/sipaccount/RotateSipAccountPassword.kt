// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.config.SipProperties
import com.ef_softworks.thundervox_server.credential.RandomPassword
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * New password for a number, hashed with the current realm. The core rejects the old password from the next
 * REGISTER on; the device keeps its current registration until then (a panel re-registers every few minutes).
 * A blocked number gets the new digests too and keeps them for the day it is unblocked.
 */
@Component
class RotateSipAccountPassword(
    private val sipAccountRepository: SipAccountRepository,
    private val subscriberReadModel: SubscriberReadModel,
    private val listSipAccounts: ListSipAccounts,
    private val sipProperties: SipProperties,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun rotate(id: Long, request: RotateSipAccountPasswordRequest, actor: AuditActor): SipAccountCredentialsResponse {
        val account = sipAccountRepository.findSipAccount(id)

        val requestedPassword = request.password?.takeIf { it.isNotEmpty() }
        val generatedPassword = requestedPassword == null
        val password = requestedPassword?.let(SipAccountInputRules::requireValidPassword) ?: RandomPassword.generate()

        account.setPassword(password, sipProperties.realm)
        sipAccountRepository.save(account)
        if (account.enabled) {
            subscriberReadModel.publish(account)
        }

        auditLog.record(
            actor, AuditAction.SIP_ACCOUNT_PASSWORD_ROTATED, AuditTargetType.SIP_ACCOUNT, account.id,
            mapOf("username" to account.username, "password" to if (generatedPassword) "generated" else "set_by_operator")
        )
        logInfo("SIP account password rotated: username=${account.username}, by=${actor.login}")
        return SipAccountCredentialsResponse(
            account = listSipAccounts.one(account.id),
            realm = account.realm,
            generatedPassword = password.takeIf { generatedPassword },
        )
    }
}
