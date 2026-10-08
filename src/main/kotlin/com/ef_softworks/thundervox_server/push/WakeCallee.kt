// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.ef_softworks.thundervox_server.config.SipProperties
import com.ef_softworks.thundervox_server.integration.PushSettings
import com.ef_softworks.thundervox_server.sipaccount.SipAccountEntity
import com.ef_softworks.thundervox_server.sipaccount.SipAccountRepository
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/** What the core tells the server about a call to a sleeping callee. */
data class WakeCalleeCall(
    val sipCallId: String?,
    val callerNumber: String?,
    val calleeNumber: String?,
)

/** What the core gets back at once: the call id the push will carry, and whether a push is on its way. */
data class WakeCalleeOutcome(
    val callId: String,
    val accepted: Boolean,
    val reason: String?,
)

/**
 * "This callee is asleep, wake it" from the core. Turns two SIP numbers into the operator's own identifiers, makes
 * up the call id, and hands the push to the worker. Returns before anything leaves this host: the core's SIP worker
 * is waiting for the answer.
 *
 * Not transactional on purpose: the reads need none, and a SKIPPED row must land even though nothing else changes.
 */
@Component
class WakeCallee(
    private val sipAccountRepository: SipAccountRepository,
    private val pushSettings: PushSettings,
    private val sipProperties: SipProperties,
    private val pushDeliveryWorker: PushDeliveryWorker,
    private val pushDeliveryLog: PushDeliveryLog,
) {

    fun wake(call: WakeCalleeCall): WakeCalleeOutcome {
        val payload = buildPayload(call)
        val settings = pushSettings.current()
        if (!settings.enabled || settings.url.isBlank()) {
            // Data-loss point: the callee stays asleep until the push is configured; visible in the delivery log
            logWarn("Wake request for ${payload.calleeNumber} skipped: the push gateway is ${if (settings.enabled) "without a URL" else "switched off"}")
            pushDeliveryLog.record(
                PushDeliveryKind.LIVE, payload, settings.url.takeIf { it.isNotBlank() },
                PushDeliveryResult(PushDeliveryOutcome.SKIPPED, null, 0, 0, null, "push_disabled")
            )
            return WakeCalleeOutcome(payload.callId, accepted = false, reason = "push_disabled")
        }
        val queued = pushDeliveryWorker.submit(settings.toTarget(), payload)
        logInfo("Wake request: call_id=${payload.callId} caller=${payload.callerNumber}/${payload.callerId} callee=${payload.calleeNumber}/${payload.calleeId} ${if (queued) "queued" else "dropped (queue full)"}")
        return WakeCalleeOutcome(payload.callId, accepted = queued, reason = if (queued) null else "queue_full")
    }

    fun buildPayload(call: WakeCalleeCall): WakePushPayload {
        val caller = findByNumber(call.callerNumber)
        val callee = findByNumber(call.calleeNumber)
        return WakePushPayload(
            callId = UUID.randomUUID().toString(),
            sipCallId = call.sipCallId?.takeIf { it.isNotBlank() },
            callerId = caller?.externalId,
            callerNumber = call.callerNumber?.takeIf { it.isNotBlank() },
            callerName = caller?.name,
            calleeId = callee?.externalId,
            calleeNumber = call.calleeNumber?.takeIf { it.isNotBlank() },
            calleeName = callee?.name,
            sipDomain = sipProperties.realm,
            occurredAt = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
        )
    }

    // The core sends "<null>" for an unset variable and a NATed panel may show up under an unknown number: both
    // are "no such number", not an error - the push goes out with what is known
    private fun findByNumber(number: String?): SipAccountEntity? =
        number?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }?.let(sipAccountRepository::findByUsername)
}
