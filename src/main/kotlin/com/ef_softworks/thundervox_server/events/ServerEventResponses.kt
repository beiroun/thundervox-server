// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.calls.CallMode
import com.ef_softworks.thundervox_server.calls.CallOutcome
import com.ef_softworks.thundervox_server.push.PushDeliveryKind
import com.ef_softworks.thundervox_server.push.PushDeliveryOutcome
import com.ef_softworks.thundervox_server.registration.RegistrationEventKind
import com.ef_softworks.thundervox_server.servicecall.ServiceCallOperation
import com.ef_softworks.thundervox_server.servicecall.ServiceCallerAuth
import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/** The journals the event log is made of; also the `category` filter of GET /events. */
enum class ServerEventCategory {
    /** A number came online or went offline (registration_event). */
    REGISTRATION,
    /** A call the core saw, with where it ended up (call_log). */
    CALL,
    /** A wake push to the operator's backend and what came of it (push_delivery). */
    PUSH,
    /** A call of an operator's backend to the service API (service_call_log). */
    SERVICE_CALL,
}

/**
 * One line of the event log. Exactly one of the four bodies is set, the one of [category]; [key] is unique across
 * the whole log (ids are unique only inside their journal).
 */
data class ServerEventResponse(
    @JsonProperty("key") val key: String,
    @JsonProperty("category") val category: ServerEventCategory,
    @JsonProperty("id") val id: Long,
    @JsonProperty("occurred_at") val occurredAt: LocalDateTime,
    @JsonProperty("registration") val registration: RegistrationEventResponse? = null,
    @JsonProperty("call") val call: CallEventResponse? = null,
    @JsonProperty("push") val push: PushDeliveryResponse? = null,
    @JsonProperty("service_call") val serviceCall: ServiceCallResponse? = null,
) {
    companion object {
        fun keyOf(category: ServerEventCategory, id: Long) = "${category.name.lowercase()}-$id"
    }
}

data class ServerEventPageResponse(
    @JsonProperty("items") val items: List<ServerEventResponse>,
    @JsonProperty("total") val total: Long,
    @JsonProperty("page") val page: Int,
    @JsonProperty("size") val size: Int,
    /** The categories this console user may see: readers get registrations and calls only. */
    @JsonProperty("categories") val categories: List<ServerEventCategory>,
)

/** The SIP account behind a number, when the number has one (an open core in tests registers numbers without). */
data class EventSipAccountResponse(
    @JsonProperty("id") val id: Long,
    @JsonProperty("kind") val kind: SipAccountKind,
    @JsonProperty("name") val name: String,
    @JsonProperty("external_id") val externalId: String?,
)

data class RegistrationEventResponse(
    @JsonProperty("username") val username: String,
    @JsonProperty("kind") val kind: RegistrationEventKind,
    /** ONLINE only: the device that came up. */
    @JsonProperty("contact") val contact: String?,
    @JsonProperty("received") val received: String?,
    @JsonProperty("user_agent") val userAgent: String?,
    @JsonProperty("account") val account: EventSipAccountResponse?,
)

data class CallEventResponse(
    @JsonProperty("sip_call_id") val sipCallId: String,
    @JsonProperty("mode") val mode: CallMode?,
    @JsonProperty("caller_number") val callerNumber: String?,
    @JsonProperty("caller_external_id") val callerExternalId: String?,
    @JsonProperty("caller_name") val callerName: String?,
    @JsonProperty("caller_account_id") val callerAccountId: Long?,
    @JsonProperty("callee_number") val calleeNumber: String?,
    @JsonProperty("callee_external_id") val calleeExternalId: String?,
    @JsonProperty("callee_name") val calleeName: String?,
    @JsonProperty("callee_account_id") val calleeAccountId: Long?,
    @JsonProperty("started_at") val startedAt: LocalDateTime,
    @JsonProperty("answered_at") val answeredAt: LocalDateTime?,
    @JsonProperty("ended_at") val endedAt: LocalDateTime?,
    /** bye | cancel | final | window */
    @JsonProperty("end_reason") val endReason: String?,
    /** The final SIP code the caller got. */
    @JsonProperty("end_code") val endCode: Int?,
    @JsonProperty("outcome") val outcome: CallOutcome,
    /**
     * WAKE only: when the callee's number came online after the call started - the wake push worked. Null while
     * nobody has registered, and also when the number was already online and only re-registered (the journal
     * keeps transitions, not every REGISTER).
     */
    @JsonProperty("callee_registered_at") val calleeRegisteredAt: LocalDateTime?,
)

data class PushDeliveryResponse(
    @JsonProperty("call_id") val callId: String,
    @JsonProperty("sip_call_id") val sipCallId: String?,
    @JsonProperty("kind") val kind: PushDeliveryKind,
    @JsonProperty("caller_number") val callerNumber: String?,
    @JsonProperty("caller_external_id") val callerExternalId: String?,
    @JsonProperty("caller_account_id") val callerAccountId: Long?,
    @JsonProperty("callee_number") val calleeNumber: String?,
    @JsonProperty("callee_external_id") val calleeExternalId: String?,
    @JsonProperty("callee_account_id") val calleeAccountId: Long?,
    @JsonProperty("url") val url: String?,
    @JsonProperty("outcome") val outcome: PushDeliveryOutcome,
    @JsonProperty("http_status") val httpStatus: Int?,
    @JsonProperty("attempts") val attempts: Int,
    @JsonProperty("duration_ms") val durationMs: Int?,
    @JsonProperty("response_excerpt") val responseExcerpt: String?,
    @JsonProperty("error") val error: String?,
)

data class ServiceCallResponse(
    @JsonProperty("caller_auth") val callerAuth: ServiceCallerAuth,
    /** The backend: the live token it presented (TOKEN only). */
    @JsonProperty("token_id") val tokenId: Long?,
    @JsonProperty("token_name") val tokenName: String?,
    /** First characters of a presented token-shaped value, a revoked token included. */
    @JsonProperty("token_prefix") val tokenPrefix: String?,
    @JsonProperty("method") val method: String,
    @JsonProperty("path") val path: String,
    @JsonProperty("operation") val operation: ServiceCallOperation,
    @JsonProperty("sip_kind") val sipKind: String?,
    @JsonProperty("external_id") val externalId: String?,
    /** The number of the endpoint, when it has one. */
    @JsonProperty("username") val username: String?,
    @JsonProperty("account_id") val accountId: Long?,
    @JsonProperty("http_status") val httpStatus: Int,
    @JsonProperty("duration_ms") val durationMs: Int,
    @JsonProperty("remote_address") val remoteAddress: String?,
    @JsonProperty("error") val error: String?,
)
