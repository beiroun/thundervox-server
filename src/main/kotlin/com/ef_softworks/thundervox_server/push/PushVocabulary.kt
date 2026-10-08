// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import java.time.Duration

/** Where a wake push goes and how patiently; a snapshot of the settings taken when the push is queued. */
data class PushTarget(
    val url: String,
    val authHeaderName: String,
    val authHeaderValue: String?,
    val connectTimeout: Duration,
    val readTimeout: Duration,
)

/** A live call from the core or a test from the Integration page; stored by name in push_delivery.kind. */
enum class PushDeliveryKind { LIVE, TEST }

/** What happened to a push; stored by name in push_delivery.outcome. */
enum class PushDeliveryOutcome {
    /** The operator's backend answered 2xx. */
    DELIVERED,
    /** The operator's backend answered 4xx: it did not like the request, retrying would not help. */
    REJECTED,
    /** No usable answer: network error, timeout, or 5xx after the retry. */
    FAILED,
    /** Nothing was sent: the push is switched off or has no URL. */
    SKIPPED,
}
