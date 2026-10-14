// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.dashboard

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Duration
import java.time.LocalDateTime

/**
 * The periods the dashboard can show, each with the bucket its series are summed into: at most a couple of
 * hundred points per chart whatever the period.
 */
enum class DashboardRange(val code: String, val span: Duration, val bucket: Duration, val bucketSql: String) {
    HOUR("1h", Duration.ofHours(1), Duration.ofMinutes(1), "1 minute"),
    SIX_HOURS("6h", Duration.ofHours(6), Duration.ofMinutes(5), "5 minutes"),
    DAY("1d", Duration.ofDays(1), Duration.ofMinutes(15), "15 minutes"),
    WEEK("1w", Duration.ofDays(7), Duration.ofHours(1), "1 hour"),
    MONTH("1m", Duration.ofDays(30), Duration.ofHours(6), "6 hours");

    /** Start of the bucket holding `moment`. The grid runs from midnight; every bucket divides a day evenly. */
    fun bucketStart(moment: LocalDateTime): LocalDateTime {
        val midnight = moment.toLocalDate().atStartOfDay()
        val elapsedSeconds = Duration.between(midnight, moment).toSeconds()
        return midnight.plusSeconds(elapsedSeconds - elapsedSeconds % bucket.toSeconds())
    }

    companion object {
        fun parse(code: String?): DashboardRange =
            entries.firstOrNull { it.code == code?.trim()?.lowercase() }
                ?: throw WrongTypeException(
                    "Unknown dashboard range '$code'; expected one of ${entries.joinToString { it.code }}",
                    "Период: ожидается один из ${entries.joinToString { it.code }}"
                )
    }
}

/** The numbers at the top of the dashboard: presence right now, activity over the chosen period. */
data class DashboardCountersResponse(
    /** Enabled numbers registered right now (any kind). */
    @JsonProperty("online") val online: Int,
    /** Enabled numbers not registered right now (any kind). */
    @JsonProperty("offline") val offline: Int,
    /** Enabled app client numbers, registered or not. */
    @JsonProperty("subscribers") val subscribers: Int,
    @JsonProperty("pushes") val pushes: Long,
    @JsonProperty("registrations") val registrations: Long,
    @JsonProperty("calls") val calls: Long,
    @JsonProperty("calls_answered") val callsAnswered: Long,
)

/** One bucket of the charts. Presence values are null for a bucket without a sample (the server was down). */
data class DashboardPointResponse(
    @JsonProperty("at") val at: LocalDateTime,
    @JsonProperty("online") val online: Int?,
    @JsonProperty("offline") val offline: Int?,
    @JsonProperty("subscribers") val subscribers: Int?,
    @JsonProperty("pushes") val pushes: Long,
    @JsonProperty("registrations") val registrations: Long,
    @JsonProperty("calls") val calls: Long,
)

data class DashboardResponse(
    @JsonProperty("range") val range: String,
    @JsonProperty("bucket_seconds") val bucketSeconds: Long,
    @JsonProperty("from") val from: LocalDateTime,
    @JsonProperty("to") val to: LocalDateTime,
    @JsonProperty("counters") val counters: DashboardCountersResponse,
    @JsonProperty("points") val points: List<DashboardPointResponse>,
)
