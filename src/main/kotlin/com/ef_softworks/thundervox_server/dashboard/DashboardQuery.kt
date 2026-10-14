// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.dashboard

import com.ef_softworks.thundervox_server.tenant.DefaultTenant
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
        val from = to.minus(range.span)
        val presence = presenceSeries(range, from)
        val pushes = countSeries("push_delivery", "created_at", "kind = 'LIVE'", range, from)
        val registrations = countSeries("registration_event", "occurred_at", "kind = 'ONLINE'", range, from)
        val calls = countSeries("call_log", "started_at", null, range, from)

        val points = (0 until range.points).map { index ->
            val at = from.plus(range.bucket.multipliedBy(index.toLong()))
            val sample = presence[at]
            DashboardPointResponse(
                at = at,
                online = sample?.online,
                offline = sample?.offline,
                subscribers = sample?.subscribers,
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
        val presence = jdbcClient.sql(
            """
            SELECT count(*) FILTER (WHERE s.online) AS online,
                   count(*) FILTER (WHERE NOT coalesce(s.online, false)) AS offline,
                   count(*) FILTER (WHERE a.kind = 'CLIENT') AS subscribers
            FROM sip_account a
            LEFT JOIN registration_state s ON s.tenant_id = a.tenant_id AND s.username = a.username
            WHERE a.tenant_id = :tenantId AND a.enabled
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .query { resultSet, _ -> Triple(resultSet.getInt("online"), resultSet.getInt("offline"), resultSet.getInt("subscribers")) }
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
            online = presence.first,
            offline = presence.second,
            subscribers = presence.third,
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
                   round(avg(panels_online + clients_online + other_online)) AS online,
                   round(avg((panels_total - panels_online) + (clients_total - clients_online))) AS offline,
                   round(avg(clients_total)) AS subscribers
            FROM presence_sample
            WHERE tenant_id = :tenantId AND sampled_at >= :origin
            GROUP BY 1
            """.trimIndent()
        )
            .param("tenantId", defaultTenant.id)
            .param("bucket", range.bucketSql)
            .param("origin", from)
            .query { resultSet, _ ->
                resultSet.getObject("bucket", LocalDateTime::class.java) to PresenceBucket(
                    online = resultSet.getInt("online"),
                    offline = resultSet.getInt("offline"),
                    subscribers = resultSet.getInt("subscribers"),
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

    private data class PresenceBucket(val online: Int, val offline: Int, val subscribers: Int)
}
