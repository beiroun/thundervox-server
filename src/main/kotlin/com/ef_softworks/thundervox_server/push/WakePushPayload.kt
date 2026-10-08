// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Body of the wake push, contract v2 (ThunderVox -> operator backend). The three v1 names (call_id, caller_id,
 * callee_id) are kept so the operator's existing test endpoint accepts it.
 *
 * call_id is a UUID made by this server: for Modus it keys call_history and names the CallKit call, so the SIP
 * Call-ID (free text) cannot play that role and travels separately. caller_id / callee_id are the external ids of
 * the numbers - the operator's own identifiers (a panel's device id, a subscriber account) - and null when a number
 * has none (test numbers made in the console).
 */
data class WakePushPayload(
    @JsonProperty("call_id") val callId: String,
    @JsonProperty("sip_call_id") val sipCallId: String?,
    @JsonProperty("caller_id") val callerId: String?,
    @JsonProperty("caller_number") val callerNumber: String?,
    @JsonProperty("caller_name") val callerName: String?,
    @JsonProperty("callee_id") val calleeId: String?,
    @JsonProperty("callee_number") val calleeNumber: String?,
    @JsonProperty("callee_name") val calleeName: String?,
    @JsonProperty("sip_domain") val sipDomain: String,
    @JsonProperty("occurred_at") val occurredAt: String,
)
