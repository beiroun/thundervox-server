// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

import org.springframework.data.jpa.repository.JpaRepository

interface SipAccountRepository : JpaRepository<SipAccountEntity, Long> {

    fun existsByUsername(username: String): Boolean

    /** The number itself is globally unique (uq_sip_account_username): the core names callers and callees by it. */
    fun findByUsername(username: String): SipAccountEntity?

    fun findAllByTenantIdOrderByCreatedAtDesc(tenantId: Long): List<SipAccountEntity>

    /** The number of an endpoint under the operator's own id; unique per kind (index uq_sip_account_external_id). */
    fun findByTenantIdAndKindAndExternalId(tenantId: Long, kind: SipAccountKind, externalId: String): SipAccountEntity?
}
