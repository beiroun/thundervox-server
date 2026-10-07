// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * New name and/or external id of a number. The number itself never changes: it is baked into the device
 * configuration and into the stored digests - a different number is a new account.
 *
 * Changing the external id re-keys the endpoint for the service API: the operator's backend will find this
 * number under the new id and no longer under the old one.
 */
@Component
class UpdateSipAccountDetails(
    private val sipAccountRepository: SipAccountRepository,
    private val listSipAccounts: ListSipAccounts,
    private val auditLog: AuditLog,
) {

    @Transactional
    fun update(id: Long, request: UpdateSipAccountDetailsRequest, actor: AuditActor): SipAccountResponse {
        val account = sipAccountRepository.findSipAccount(id)
        val name = SipAccountInputRules.requireValidName(request.name)
        val externalId = request.externalId?.takeIf { it.isNotBlank() }?.let(SipAccountInputRules::requireValidExternalId)
        if (name == account.name && externalId == account.externalId) {
            return listSipAccounts.one(account.id)
        }

        if (externalId != null && externalId != account.externalId) {
            val holder = sipAccountRepository.findByTenantIdAndKindAndExternalId(account.tenantId, account.kind, externalId)
            if (holder != null && holder.id != account.id) {
                throw externalIdTaken(account.kind, externalId)
            }
        }

        val changes = mutableMapOf<String, Any?>("username" to account.username)
        if (name != account.name) {
            changes["name_from"] = account.name
            changes["name_to"] = name
            account.name = name
        }
        if (externalId != account.externalId) {
            changes["external_id_from"] = account.externalId
            changes["external_id_to"] = externalId
            account.externalId = externalId
        }
        try {
            sipAccountRepository.saveAndFlush(account)
        } catch (ex: DataIntegrityViolationException) {
            // The same external id was given to another number at the same moment
            throw externalIdTaken(account.kind, externalId ?: "")
        }
        auditLog.record(actor, AuditAction.SIP_ACCOUNT_DETAILS_UPDATED, AuditTargetType.SIP_ACCOUNT, account.id, changes)
        logInfo("SIP account details updated: username=${account.username}, external_id=${account.externalId}, by=${actor.login}")
        return listSipAccounts.one(account.id)
    }
}
