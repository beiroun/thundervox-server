// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.dashboard

import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import com.ef_softworks.thundervox_server.util.logDebug
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Reads the journals into what the dashboard draws. Series are summed into the range's buckets by PostgreSQL
 * (date_bin), the bucket axis is built here so a bucket without rows still shows up as zero (or null for presence,
 * where "no sample" and "nobody online" are different things).
 */
@Component
class DashboardQuery(
    private val jdbcClient: JdbcClient,
    private val defaultTenant: DefaultTenant,
) {

    @Transactional(readOnly = true)
    fun build(range: DashboardRange): DashboardResponse {
        val to = LocalDateTime.now()
        // The axis starts on the bucket grid, not at a raw now() - span: the series below are joined to the
        // date_bin() keys from Postgres by exact timestamp equality, and a raw origin carries the JVM's nanoseconds
        // that Postgres (microseconds) drops - every key missed and every bucket came back empty (v0.7.0).
        val from = range.bucketStart(to.minus(range.span))
        val bucketStarts = generateSequence(from) { it.plus(range.bucket) }.takeWhile { !it.isAfter(to) }.toList()
        val presence = presenceSeries(range, from)
        val pushes = countSeries("push_delivery", "created_at", "kind = 'LIVE'", range, from)
        val registrations = countSeries("registration_event", "occurred_at", "kind = 'ONLINE'", range, from)
        val calls = countSeries("call_log", "started_at", null, range, from)
        logDebug(
            "Dashboard ${range.code}: ${bucketStarts.size} buckets from $from; rows per series - presence ${presence.size}, " +
                "pushes ${pushes.size}, registrations ${registrations.size}, calls ${calls.size}"
        )

        val points = bucketStarts.map { at ->
            val sample = presence[at]
            DashboardPointResponse(
                at = at,
                subscribers = sample?.subscribers,
                panels = sample?.panels,
                total = sample?.total,
                online = sample?.online,
                offline = sample?.offline,
                pushes = pushes[at] ?: 0,
                registrations = registrations[at] ?: 0,
                calls = calls[at] ?: 0,
            )
        }
        return DashboardResponse(
            range = range.code,
            bucketSeconds = range.bucket.toSeconds(),
            from = from,
            to = to,
            counters = counters(from),
            points = points,
        )
    }

    private fun counters(from: LocalDateTime): DashboardCountersResponse {
        val fleet = jdbcClient.sql(
            """
            SELECT count(*) FILTER (WHERE a.kind = 'CLIENT') AS subscribers,
                   count(*) FILTER (WHERE a.kind = 'PANEL') AS panels,
                   count(*) AS total,
                   count(*) FILTER (WHERE s.online) AS online,
                   count(*) FILTER (WHERE NOT coalesce(s.online, false)) AS offline
            FROM sip_account a
            LEFT JOIN registration_state s ON s.tenant_id = a.tenant_id AND s.username = a.username
            WHERE a.tenant_id = :tenantId AND a.enabled
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .query { resultSet, _ ->
                FleetNow(
                    subscribers = resultSet.getInt("subscribers"),
                    panels = resultSet.getInt("panels"),
                    total = resultSet.getInt("total"),
                    online = resultSet.getInt("online"),
                    offline = resultSet.getInt("offline"),
                )
            }
            .single()
        val calls = jdbcClient.sql(
            """
            SELECT count(*) AS calls, count(*) FILTER (WHERE outcome = 'ANSWERED') AS answered
            FROM call_log WHERE tenant_id = :tenantId AND started_at >= :from
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("from", from)
            .query { resultSet, _ -> resultSet.getLong("calls") to resultSet.getLong("answered") }
            .single()
        return DashboardCountersResponse(
            subscribers = fleet.subscribers,
            panels = fleet.panels,
            total = fleet.total,
            online = fleet.online,
            offline = fleet.offline,
            pushes = countSince("push_delivery", "created_at", "kind = 'LIVE'", from),
            registrations = countSince("registration_event", "occurred_at", "kind = 'ONLINE'", from),
            calls = calls.first,
            callsAnswered = calls.second,
        )
    }

    private fun countSince(table: String, timeColumn: String, filter: String?, from: LocalDateTime): Long =
        jdbcClient.sql(
            "SELECT count(*) FROM $table WHERE tenant_id = :tenantId AND $timeColumn >= :from" + (filter?.let { " AND $it" } ?: "")
        )
            .param("tenantId", defaultTenant.id)
            .param("from", from)
            .query(Long::class.javaObjectType)
            .single()

    private fun presenceSeries(range: DashboardRange, from: LocalDateTime): Map<LocalDateTime, PresenceBucket> =
        jdbcClient.sql(
            """
            SELECT date_bin(CAST(:bucket AS interval), sampled_at, CAST(:origin AS timestamp)) AS bucket,
                   round(avg(clients_total)) AS subscribers,
                   round(avg(panels_total + clients_total)) AS total,
                   round(avg(panels_online + clients_online)) AS online
            FROM presence_sample
            WHERE tenant_id = :tenantId AND sampled_at >= :origin
            GROUP BY 1
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("bucket", range.bucketSql)
            .param("origin", from)
            .query { resultSet, _ ->
                // Each sum is closed by deriving its second part from the rounded total, so the rounding of a bucket
                // where the fleet changed mid-way lands on panels / offline instead of breaking the sum. other_online
                // (a registration whose number is gone or blocked) has no total to belong to and is left out.
                val subscribers = resultSet.getInt("subscribers")
                val total = resultSet.getInt("total")
                val online = minOf(resultSet.getInt("online"), total)
                resultSet.getObject("bucket", LocalDateTime::class.java) to PresenceBucket(
                    subscribers = subscribers,
                    panels = total - subscribers,
                    total = total,
                    online = online,
                    offline = total - online,
                )
            }
            .list()
            .toMap()

    // table / column names come from this class, never from a request
    private fun countSeries(table: String, timeColumn: String, filter: String?, range: DashboardRange, from: LocalDateTime): Map<LocalDateTime, Long> =
        jdbcClient.sql(
            """
            SELECT date_bin(CAST(:bucket AS interval), $timeColumn, CAST(:origin AS timestamp)) AS bucket, count(*) AS total
            FROM $table
            WHERE tenant_id = :tenantId AND $timeColumn >= :origin${filter?.let { " AND $it" } ?: ""}
            GROUP BY 1
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("bucket", range.bucketSql)
            .param("origin", from)
            .query { resultSet, _ -> resultSet.getObject("bucket", LocalDateTime::class.java) to resultSet.getLong("total") }
            .list()
            .toMap()

    /** The fleet by kind and by presence, right now: both sums equal `total`. */
    private data class FleetNow(val subscribers: Int, val panels: Int, val total: Int, val online: Int, val offline: Int)

    /** The same five numbers averaged over one bucket of samples. */
    private data class PresenceBucket(val subscribers: Int, val panels: Int, val total: Int, val online: Int, val offline: Int)
}
