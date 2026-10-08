// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.push

import com.ef_softworks.thundervox_server.config.IntegrationProperties
import com.ef_softworks.thundervox_server.util.logInfo
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/** Drops delivery log rows older than the retention; the log is a debugging aid, not an archive. */
@Component
@EnableConfigurationProperties(IntegrationProperties::class)
class PushDeliveryHousekeeping(
    private val pushDeliveryLog: PushDeliveryLog,
    private val integrationProperties: IntegrationProperties,
) {

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    fun purge() {
        try {
            val deleted = pushDeliveryLog.deleteOlderThan(LocalDateTime.now().minus(integrationProperties.pushLogRetention))
            if (deleted > 0) {
                logInfo("Push delivery log housekeeping: $deleted rows older than ${integrationProperties.pushLogRetention} deleted")
            }
        } catch (ex: Exception) {
            logWarn("Push delivery log housekeeping failed; next run in an hour", ex)
        }
    }
}
