// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.internal

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.push.WakeCallee
import com.ef_softworks.thundervox_server.push.WakeCalleeCall
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Body the core's route[PUSH] sends: numbers only, the server knows the rest. */
data class WakeCalleeRequest(
    @JsonProperty("sip_call_id") val sipCallId: String?,
    @JsonProperty("caller_number") val callerNumber: String?,
    @JsonProperty("callee_number") val calleeNumber: String?,
)

data class WakeCalleeResponse(
    @JsonProperty("call_id") val callId: String,
    @JsonProperty("accepted") val accepted: Boolean,
    /** Why nothing is on its way: "push_disabled", "queue_full". */
    @JsonProperty("reason") val reason: String?,
)

/**
 * Internal API for the SIP core (X-CORE-TOKEN, loopback only). Hidden from the OpenAPI document: it is not part of
 * what integrators or the console use.
 */
@Hidden
@RestController
@RequestMapping("/internal/push")
class InternalPushController(private val wakeCallee: WakeCallee) {

    /** 202 when a push is queued, 200 with accepted=false when nothing will be sent; never blocks on the network. */
    @PostMapping("/wake")
    fun wake(@RequestBody request: WakeCalleeRequest): ResponseEntity<BaseApiResponse<WakeCalleeResponse>> {
        val outcome = wakeCallee.wake(WakeCalleeCall(request.sipCallId, request.callerNumber, request.calleeNumber))
        val status = if (outcome.accepted) HttpStatus.ACCEPTED else HttpStatus.OK
        return ResponseEntity.status(status).body(BaseApiResponse.ok(WakeCalleeResponse(outcome.callId, outcome.accepted, outcome.reason)))
    }
}
