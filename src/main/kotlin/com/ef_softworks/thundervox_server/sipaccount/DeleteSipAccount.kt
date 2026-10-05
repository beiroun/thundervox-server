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
 * Removes a number for good. Its generated value is not handed out again (sequences never go back); a hand-typed
 * value becomes free. The audit entry keeps the number and name, since the row itself is gone. As with blocking,
 * a live registration stays in the core until it expires.
 */
@Component
class DeleteSipAccount(
    private val sipAccountRepository: SipAccountRepository,
    private val subscriberReadModel: SubscriberReadModel,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun delete(id: Long) {
        val actor = currentConsoleUser()
        val account = sipAccountRepository.findSipAccount(id)
        subscriberReadModel.withdraw(account.username)
        sipAccountRepository.delete(account)
        auditLog.record(
            actor.auditActor, AuditAction.SIP_ACCOUNT_DELETED, AuditTargetType.SIP_ACCOUNT, account.id,
            mapOf("username" to account.username, "name" to account.name, "kind" to account.kind.name)
        )
        logInfo("SIP account deleted: username=${account.username}, by=${actor.login}")
    }
}
