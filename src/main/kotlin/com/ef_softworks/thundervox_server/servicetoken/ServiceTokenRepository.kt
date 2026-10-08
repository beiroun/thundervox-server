// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import org.springframework.data.jpa.repository.JpaRepository

interface ServiceTokenRepository : JpaRepository<ServiceTokenEntity, Long> {

    fun findByTokenHash(tokenHash: String): ServiceTokenEntity?

    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: Long): List<ServiceTokenEntity>

    /** A name is taken only by a live token (index uq_service_token_live_name): a revoked one frees it. */
    fun existsByTenantIdAndNameAndEnabledTrue(tenantId: Long, name: String): Boolean
}
