// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.audit.AuditAction
import com.ef_softworks.thundervox_server.audit.AuditActor
import com.ef_softworks.thundervox_server.audit.AuditLog
import com.ef_softworks.thundervox_server.audit.AuditTargetType
import com.ef_softworks.thundervox_server.consoleuser.currentConsoleUser
import com.ef_softworks.thundervox_server.exception.base.NotFoundException
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.ef_softworks.thundervox_server.push.PushDeliveryClient
import com.ef_softworks.thundervox_server.push.PushDeliveryKind
import com.ef_softworks.thundervox_server.push.PushDeliveryLog
import com.ef_softworks.thundervox_server.push.PushDeliveryOutcome
import com.ef_softworks.thundervox_server.push.WakeCallee
import com.ef_softworks.thundervox_server.push.WakeCalleeCall
import com.ef_softworks.thundervox_server.sipaccount.SipAccountInputRules
import com.ef_softworks.thundervox_server.sipaccount.SipAccountRepository
import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper

/**
 * A wake push from the Integration page: the same payload and the same HTTP call a live call would make, sent on
 * the request thread so the operator sees the answer. Recorded as TEST in the delivery log and in the audit trail.
 * The settings must be complete, but the switch may be off: testing before switching on is the point.
 */
@Component
class SendTestPush(
    private val sipAccountRepository: SipAccountRepository,
    private val pushSettings: PushSettings,
    private val wakeCallee: WakeCallee,
    private val pushDeliveryClient: PushDeliveryClient,
    private val pushDeliveryLog: PushDeliveryLog,
    private val testPushAudit: TestPushAudit,
    private val jsonMapper: JsonMapper,
) {

    fun send(request: PushTestRequest): PushTestResponse {
        val actor = currentConsoleUser()
        val caller = requireNumber(request.callerUsername)
        val callee = requireNumber(request.calleeUsername)
        val settings = pushSettings.current()
        if (settings.url.isBlank()) {
            throw WrongTypeException("Test push without a URL", "Сначала задайте адрес пуша")
        }
        val payload = wakeCallee.buildPayload(WakeCalleeCall(sipCallId = "test-${System.currentTimeMillis()}", callerNumber = caller, calleeNumber = callee))
        val result = pushDeliveryClient.deliver(settings.toTarget(), payload)
        pushDeliveryLog.record(PushDeliveryKind.TEST, payload, settings.url, result)
        testPushAudit.record(actor.auditActor, settings.url, payload.callId, result.outcome, result.httpStatus)
        logInfo("Test push: call_id=${payload.callId} ${caller} -> ${callee} ${result.outcome} status=${result.httpStatus} in ${result.durationMs} ms, by=${actor.login}")
        return PushTestResponse(
            callId = payload.callId,
            url = settings.url,
            outcome = result.outcome,
            httpStatus = result.httpStatus,
            attempts = result.attempts,
            durationMs = result.durationMs,
            responseExcerpt = result.responseExcerpt,
            error = result.error,
            sentBody = jsonMapper.writeValueAsString(payload),
        )
    }

    private fun requireNumber(username: String): String {
        val number = SipAccountInputRules.requireValidNumber(username)
        sipAccountRepository.findByUsername(number) ?: throw NotFoundException("SIP number $number not found", "Номер $number не найден")
        return number
    }
}

/** The audit entry of a test push in its own transaction: the push itself runs outside any. */
@Component
class TestPushAudit(private val auditLog: AuditLog) {

    @Transactional
    fun record(actor: AuditActor, url: String, callId: String, outcome: PushDeliveryOutcome, httpStatus: Int?) {
        auditLog.record(
            actor, AuditAction.PUSH_TEST_SENT, AuditTargetType.PUSH_SETTINGS, null,
            mapOf("url" to url, "call_id" to callId, "outcome" to outcome.name, "http_status" to httpStatus)
        )
    }
}
