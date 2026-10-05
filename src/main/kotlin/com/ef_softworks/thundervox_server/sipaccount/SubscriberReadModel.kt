// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import com.ef_softworks.thundervox_server.util.logInfo
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Kamailio's `subscriber` table - what the core's auth_db actually checks a REGISTER / INVITE against.
 *
 * One row per enabled account, nothing else. It is written in the same transaction as the account change
 * (MANDATORY): a password the console shows as issued is exactly the one the core accepts. A divergence would mean
 * "the password changed, the core still checks the old one" - a device locked out with a password that looks valid.
 * Rows are keyed by username alone (auth_db use_domain=0), so the old row goes first even if the realm changed.
 */
@Component
class SubscriberReadModel(private val jdbcClient: JdbcClient) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun publish(account: SipAccountEntity) {
        withdrawRow(account.username)
        jdbcClient.sql(
            """
            INSERT INTO subscriber (username, domain, password, ha1, ha1b)
            VALUES (:username, :domain, '', :ha1, :ha1b)
            """.trimIndent()
        )
            .param("username", account.username)
            .param("domain", account.realm)
            .param("ha1", account.ha1)
            .param("ha1b", account.ha1b)
            .update()
        logInfo("Subscriber published: username=${account.username}, realm=${account.realm}")
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun withdraw(username: String) {
        withdrawRow(username)
        logInfo("Subscriber withdrawn: username=$username")
    }

    private fun withdrawRow(username: String) {
        jdbcClient.sql("DELETE FROM subscriber WHERE username = :username")
            .param("username", username)
            .update()
    }
}
