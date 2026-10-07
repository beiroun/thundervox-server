// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.config.SipProperties
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.sipaccount.CreateSipAccount
import com.ef_softworks.thundervox_server.sipaccount.CreateSipAccountRequest
import com.ef_softworks.thundervox_server.sipaccount.SipAccountInputRules
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.stereotype.Component

/**
 * "This endpoint needs a SIP account" from the operator's backend: the same external id always gets the same
 * number. A new id gets a fresh number of its kind and a generated password; a known id gets its existing number
 * back in service (re-enabled if it was disabled, renamed if asked, a new password only if asked).
 *
 * Deliberately not transactional: the two outcomes run in their own transactions, so when two requests for the
 * same new id arrive at once and the second one loses the unique index, this method can still read the row the
 * first one committed and serve it instead of failing.
 */
@Component
class EnsureSipAccount(
    private val sipAccountByExternalId: SipAccountByExternalId,
    private val createSipAccount: CreateSipAccount,
    private val reactivateSipAccount: ReactivateSipAccount,
    private val sipProperties: SipProperties,
) {

    fun ensure(kind: SipAccountKind, externalIdInput: String, request: EnsureSipAccountRequest): ServiceSipAccountResponse {
        val externalId = SipAccountInputRules.requireValidExternalId(externalIdInput)
        val existing = sipAccountByExternalId.find(kind, externalId)
        if (existing != null) {
            return reactivateSipAccount.reactivate(existing.id, request)
        }

        val name = request.name?.takeIf { it.isNotBlank() } ?: externalId
        val created = try {
            createSipAccount.create(
                CreateSipAccountRequest(kind = kind, name = name, externalId = externalId, username = null, password = null),
                AuditActor.OPERATOR_BACKEND,
            )
        } catch (ex: WrongTypeException) {
            // Either the input is bad, or another request created this very endpoint a moment ago - the index
            // decided, and that number is the one to serve
            val racedInto = sipAccountByExternalId.find(kind, externalId) ?: throw ex
            logWarn("Service API: $kind $externalId was created concurrently, serving ${racedInto.username}")
            return reactivateSipAccount.reactivate(racedInto.id, request)
        }
        logInfo("Service API: $kind $externalId got the new number ${created.account.username}")
        return ServiceSipAccountResponse.from(created.account, sipProperties.realm, created.generatedPassword, created = true)
    }
}
