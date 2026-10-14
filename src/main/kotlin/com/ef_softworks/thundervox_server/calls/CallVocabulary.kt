// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.calls

/** How the core handled the INVITE: rung the registered devices at once, or parked it and asked for a wake push. */
enum class CallMode { RELAY, WAKE }

/** Where a call stands; RINGING until the core reports its end. Stored by name - add values, never rename. */
enum class CallOutcome { RINGING, ANSWERED, CANCELED, NO_ANSWER, REJECTED, FAILED }

/** The three facts the core reports about a call (route[CALL_STARTED] / [CALL_ANSWERED] / [CALL_ENDED]). */
enum class CallEventKind { STARTED, ANSWERED, ENDED }

/** One call event as the core sends it, keyed by the SIP Call-ID. */
data class CallEvent(
    val kind: CallEventKind,
    val sipCallId: String,
    val callerNumber: String?,
    val calleeNumber: String?,
    val mode: CallMode?,
    /** ended only: bye | cancel | final | window */
    val reason: String?,
    /** ended only: the final SIP code the caller got */
    val code: Int?,
)
