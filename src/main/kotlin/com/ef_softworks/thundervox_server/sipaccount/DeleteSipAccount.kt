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
 * Removes a number for good - console only; the service API never deletes, it disables (the number stays bound
 * to its external id). Its generated value is not handed out again (sequences never go back); a hand-typed value
 * becomes free. The audit entry keeps the number, name and external id, since the row itself is gone. As with
 * blocking, a live registration stays in the core until it expires.
 */
@Component
class DeleteSipAccount(
    private val sipAccountRepository: SipAccountRepository,
    private val subscriberReadModel: SubscriberReadModel,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun delete(id: Long, actor: AuditActor) {
        val account = sipAccountRepository.findSipAccount(id)
        subscriberReadModel.withdraw(account.username)
        sipAccountRepository.delete(account)
        auditLog.record(
            actor, AuditAction.SIP_ACCOUNT_DELETED, AuditTargetType.SIP_ACCOUNT, account.id,
            mapOf("username" to account.username, "name" to account.name, "kind" to account.kind.name, "external_id" to account.externalId)
        )
        logInfo("SIP account deleted: username=${account.username}, by=${actor.login}")
    }
}
