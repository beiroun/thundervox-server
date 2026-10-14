// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.internal

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.calls.CallEvent
import com.ef_softworks.thundervox_server.calls.CallEventKind
import com.ef_softworks.thundervox_server.calls.CallEventQueue
import com.ef_softworks.thundervox_server.calls.CallMode
import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Body the core's route[CALL_EVENT] sends; only `event` and `sip_call_id` are always there. */
data class CallEventRequest(
    /** started | answered | ended */
    @JsonProperty("event") val event: String?,
    @JsonProperty("sip_call_id") val sipCallId: String?,
    @JsonProperty("caller_number") val callerNumber: String?,
    @JsonProperty("callee_number") val calleeNumber: String?,
    /** started only: relay | wake */
    @JsonProperty("mode") val mode: String?,
    /** ended only: bye | cancel | final | window */
    @JsonProperty("reason") val reason: String?,
    /** ended only: the final SIP code */
    @JsonProperty("code") val code: Int?,
)

data class CallEventAcceptedResponse(
    @JsonProperty("queued") val queued: Boolean,
)

/**
 * Internal API for the SIP core (X-CORE-TOKEN, loopback only): the call journal. Answers as soon as the event is
 * queued - the core's SIP worker is waiting on this request. Hidden from the OpenAPI document.
 */
@Hidden
@RestController
@RequestMapping("/internal/call-events")
class InternalCallEventController(private val callEventQueue: CallEventQueue) {

    @PostMapping
    fun record(@RequestBody request: CallEventRequest): ResponseEntity<BaseApiResponse<CallEventAcceptedResponse>> {
        val event = request.toCallEvent()
        val queued = callEventQueue.submit(event)
        return ResponseEntity.status(if (queued) HttpStatus.ACCEPTED else HttpStatus.OK)
            .body(BaseApiResponse.ok(CallEventAcceptedResponse(queued)))
    }

    private fun CallEventRequest.toCallEvent(): CallEvent {
        val kind = event?.uppercase()?.let { name -> CallEventKind.entries.firstOrNull { it.name == name } }
            ?: throw WrongTypeException("Unknown call event '$event'", "Неизвестное событие звонка")
        val callId = sipCallId?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw WrongTypeException("Call event without sip_call_id", "Событие звонка без sip_call_id")
        return CallEvent(
            kind = kind,
            sipCallId = callId,
            callerNumber = callerNumber,
            calleeNumber = calleeNumber,
            mode = mode?.uppercase()?.let { name -> CallMode.entries.firstOrNull { it.name == name } },
            reason = reason,
            code = code,
        )
    }
}
