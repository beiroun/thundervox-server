// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicecall

import org.springframework.web.util.UriUtils
import java.time.LocalDateTime

/** How the caller of the service API identified itself; stored by name in service_call_log.caller_auth. */
enum class ServiceCallerAuth {
    /** A live token: the call is the operator backend named by the token. */
    TOKEN,
    /** A token that is wrong or revoked - typically a backend still configured with an old one. */
    UNKNOWN_TOKEN,
    /** No X-SERVICE-TOKEN header at all. */
    NO_TOKEN,
}

/**
 * Who called, as the service token filter resolved it. Put on the request as an attribute, so the call journal
 * learns the backend without touching the security context.
 */
data class ServiceCaller(
    val auth: ServiceCallerAuth,
    val tokenId: Long?,
    val tokenName: String?,
    /** Visible prefix of a presented token-shaped value; null for anything that is not a token. */
    val tokenPrefix: String?,
) {
    companion object {
        val ATTRIBUTE: String = ServiceCaller::class.java.name

        val NO_TOKEN = ServiceCaller(ServiceCallerAuth.NO_TOKEN, null, null, null)
    }
}

/** What a service API call asked for, read off its method and path; stored by name in service_call_log.operation. */
enum class ServiceCallOperation {
    /** GET /service/sip-accounts/{kind}/{external_id} */
    READ,
    /** PUT /service/sip-accounts/{kind}/{external_id} */
    ENSURE,
    /** DELETE /service/sip-accounts/{kind}/{external_id} */
    DISABLE,
    /** GET /service/sip-accounts/{kind}/{external_id}/registration */
    REGISTRATION,
    /** Anything else under /service/ - a path the API does not have, or a method it does not take there. */
    OTHER,
}

/** The endpoint a call was about and what it asked for, parsed from `/service/sip-accounts/{kind}/{external_id}[/registration]`. */
data class ServiceCallTarget(
    val operation: ServiceCallOperation,
    val sipKind: String?,
    val externalId: String?,
) {
    companion object {
        private const val ACCOUNTS_PREFIX = "/service/sip-accounts/"
        private const val REGISTRATION_SEGMENT = "registration"

        /** [path] is the request path without the context path; segments are URL-decoded as the controller sees them. */
        fun of(method: String, path: String): ServiceCallTarget {
            if (!path.startsWith(ACCOUNTS_PREFIX)) {
                return ServiceCallTarget(ServiceCallOperation.OTHER, null, null)
            }
            val segments = path.removePrefix(ACCOUNTS_PREFIX).trimEnd('/').split('/')
                .map { UriUtils.decode(it, Charsets.UTF_8) }
            if (segments.size < 2 || segments[0].isEmpty() || segments[1].isEmpty()) {
                return ServiceCallTarget(ServiceCallOperation.OTHER, null, null)
            }
            val operation = when {
                segments.size == 2 && method == "GET" -> ServiceCallOperation.READ
                segments.size == 2 && method == "PUT" -> ServiceCallOperation.ENSURE
                segments.size == 2 && method == "DELETE" -> ServiceCallOperation.DISABLE
                segments.size == 3 && segments[2] == REGISTRATION_SEGMENT && method == "GET" -> ServiceCallOperation.REGISTRATION
                else -> ServiceCallOperation.OTHER
            }
            return ServiceCallTarget(operation, segments[0].uppercase(), segments[1])
        }
    }
}

/** One service API call as the journal filter saw it, waiting to be written. */
data class ServiceCall(
    val occurredAt: LocalDateTime,
    val caller: ServiceCaller,
    val method: String,
    val path: String,
    val target: ServiceCallTarget,
    val httpStatus: Int,
    val durationMs: Int,
    val remoteAddress: String?,
    /** The technical message of an error response; null for a success. */
    val error: String?,
)
