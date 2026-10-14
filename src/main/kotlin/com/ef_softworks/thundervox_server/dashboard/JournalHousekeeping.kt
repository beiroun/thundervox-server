// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.dashboard

import com.ef_softworks.thundervox_server.calls.CallLog
import com.ef_softworks.thundervox_server.config.JournalProperties
import com.ef_softworks.thundervox_server.registration.RegistrationStateStore
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** Drops journal rows older than their retention. registration_state is never purged: it is state, not a log. */
@Component
@EnableConfigurationProperties(JournalProperties::class)
class JournalHousekeeping(
    private val registrationStateStore: RegistrationStateStore,
    private val callLog: CallLog,
    private val journalProperties: JournalProperties,
) {

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT10M")
    fun purge() {
        val now = LocalDateTime.now()
        purge("registration events") { registrationStateStore.deleteEventsOlderThan(now.minus(journalProperties.registrationEventRetention)) }
        purge("presence samples") { registrationStateStore.deleteSamplesOlderThan(now.minus(journalProperties.presenceSampleRetention)) }
        purge("calls") { callLog.deleteOlderThan(now.minus(journalProperties.callLogRetention)) }
    }

    private fun purge(what: String, delete: () -> Int) {
        try {
            val deleted = delete()
            if (deleted > 0) {
                logInfo("Journal housekeeping: $deleted $what deleted")
            }
        } catch (ex: Exception) {
            logWarn("Journal housekeeping of $what failed; next run in an hour", ex)
        }
    }
}
