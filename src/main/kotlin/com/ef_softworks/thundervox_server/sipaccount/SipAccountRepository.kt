// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import org.springframework.data.jpa.repository.JpaRepository

interface SipAccountRepository : JpaRepository<SipAccountEntity, Long> {

    fun existsByUsername(username: String): Boolean

    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: Long): List<SipAccountEntity>
}
