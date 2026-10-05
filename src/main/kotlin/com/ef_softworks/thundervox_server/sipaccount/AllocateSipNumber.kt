// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.exception.base.ApiException
import com.ef_softworks.thundervox_server.util.logWarn
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * Next generated number of a kind: 8 digits from the kind's own sequence (V4), never reused.
 *
 * Operators may also type numbers by hand, and such a number can sit inside a generated range - the allocator
 * then simply draws again. Two creations at once draw different values, so only a hand-typed number can collide,
 * and the unique constraint catches the rest.
 */
@Component
class AllocateSipNumber(
    private val jdbcClient: JdbcClient,
    private val sipAccountRepository: SipAccountRepository,
) {

    fun next(kind: SipAccountKind): String {
        repeat(MAX_DRAWS) {
            val candidate = jdbcClient.sql("SELECT nextval('${kind.numberSequence}')")
                .query(Long::class.javaObjectType)
                .single()
                .toString()
            if (!sipAccountRepository.existsByUsername(candidate)) {
                return candidate
            }
            logWarn("Generated $kind number $candidate is already taken by a hand-typed account, drawing again")
        }
        throw ApiException(
            "No free $kind number after $MAX_DRAWS draws from ${kind.numberSequence}",
            "Не удалось подобрать свободный номер, повторите попытку"
        )
    }

    companion object {
        private const val MAX_DRAWS = 100
    }
}
