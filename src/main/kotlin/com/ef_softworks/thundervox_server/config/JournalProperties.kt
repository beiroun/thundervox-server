// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** How long the journals behind the dashboard and the event log are kept; the hourly housekeeping deletes older rows. */
@ConfigurationProperties(prefix = "tvx.journal")
data class JournalProperties(
    /** Transitions online/offline of numbers (registration_event). */
    val registrationEventRetention: Duration = Duration.ofDays(90),
    /** Calls (call_log). */
    val callLogRetention: Duration = Duration.ofDays(90),
    /** Presence samples for the charts (presence_sample); the month view needs at least 30 days. */
    val presenceSampleRetention: Duration = Duration.ofDays(400),
    /** Calls of the operator's backends to the service API (service_call_log). */
    val serviceCallLogRetention: Duration = Duration.ofDays(30),
) {
    init {
        require(registrationEventRetention > Duration.ZERO) { "TVX_REGISTRATION_EVENT_RETENTION must be positive" }
        require(callLogRetention > Duration.ZERO) { "TVX_CALL_LOG_RETENTION must be positive" }
        require(serviceCallLogRetention > Duration.ZERO) { "TVX_SERVICE_CALL_LOG_RETENTION must be positive" }
        require(presenceSampleRetention >= Duration.ofDays(31)) { "TVX_PRESENCE_SAMPLE_RETENTION must cover the month view (at least 31 days)" }
    }
}
