// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Takes a number out of service and back, without touching its password.
 *
 * Blocking removes the subscriber row: the next REGISTER and every INVITE from the number fail authentication at
 * once. The current registration is not evicted in this version (no RPC to the core yet) - the number can still be
 * called until its registration expires, at most an hour (max_expires), in practice until the next re-REGISTER.
 */
@Component
class BlockSipAccount(
    private val sipAccountRepository: SipAccountRepository,
    private val subscriberReadModel: SubscriberReadModel,
    private val listSipAccounts: ListSipAccounts,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun block(id: Long, actor: AuditActor): SipAccountResponse = changeAvailability(id, enabled = false, actor)

    @Transactional
    fun unblock(id: Long, actor: AuditActor): SipAccountResponse = changeAvailability(id, enabled = true, actor)

    private fun changeAvailability(id: Long, enabled: Boolean, actor: AuditActor): SipAccountResponse {
        val account = sipAccountRepository.findSipAccount(id)
        if (account.enabled != enabled) {
            account.enabled = enabled
            sipAccountRepository.save(account)
            if (enabled) subscriberReadModel.publish(account) else subscriberReadModel.withdraw(account.username)
            auditLog.record(
                actor,
                if (enabled) AuditAction.SIP_ACCOUNT_UNBLOCKED else AuditAction.SIP_ACCOUNT_BLOCKED,
                AuditTargetType.SIP_ACCOUNT, account.id,
                mapOf("username" to account.username)
            )
            logInfo("SIP account ${if (enabled) "unblocked" else "blocked"}: username=${account.username}, by=${actor.login}")
        }
        return listSipAccounts.one(account.id)
    }
}
