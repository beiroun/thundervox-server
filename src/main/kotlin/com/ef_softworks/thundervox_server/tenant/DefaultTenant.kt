// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.tenant

import com.ef_softworks.thundervox_server.exception.base.InternalInconsistencyException
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/**
 * The single tenant of v1 (seeded by V2 with slug "default"). Every row the API creates belongs to it until tenant
 * management exists; the id is resolved once and never changes at runtime.
 */
@Component
class DefaultTenant(private val jdbcClient: JdbcClient) {

    val id: Long by lazy {
        jdbcClient.sql("SELECT id FROM tenant WHERE slug = 'default'")
            .query(Long::class.javaObjectType)
            .optional()
            .orElseThrow { InternalInconsistencyException("Default tenant is missing: the V2 seed was not applied") }
    }
}
