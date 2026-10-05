// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.consoleuser.currentConsoleUser
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * New name of a number. The number itself never changes: it is baked into the device configuration and into the
 * stored digests - a different number is a new account.
 */
@Component
class RenameSipAccount(
    private val sipAccountRepository: SipAccountRepository,
    private val listSipAccounts: ListSipAccounts,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun rename(id: Long, request: RenameSipAccountRequest): SipAccountResponse {
        val actor = currentConsoleUser()
        val account = sipAccountRepository.findSipAccount(id)
        val name = SipAccountInputRules.requireValidName(request.name)
        if (name != account.name) {
            val previousName = account.name
            account.name = name
            sipAccountRepository.save(account)
            auditLog.record(
                actor.auditActor, AuditAction.SIP_ACCOUNT_RENAMED, AuditTargetType.SIP_ACCOUNT, account.id,
                mapOf("username" to account.username, "from" to previousName, "to" to name)
            )
            logInfo("SIP account renamed: username=${account.username}, by=${actor.login}")
        }
        return listSipAccounts.one(account.id)
    }
}
