// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/**
 * A named token of the service API, issued by the super administrator on the Integration page. The value itself is
 * never stored: [tokenHash] is its SHA-256, [tokenPrefix] the first characters shown in the console to tell tokens
 * apart. Revoking keeps the row: the audit trail signs service calls with the token's name.
 */
@Entity
@Table(name = "service_token")
class ServiceTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "service_token_seq")
    @SequenceGenerator(name = "service_token_seq", sequenceName = "service_token_seq", allocationSize = 50)
    var id: Long = 0,
    @Column(name = "tenant_id") var tenantId: Long,
    @Column(name = "name") var name: String,
    @Column(name = "token_hash") var tokenHash: String,
    @Column(name = "token_prefix") var tokenPrefix: String,
    @Column(name = "enabled") var enabled: Boolean = true,
    @Column(name = "created_by") var createdBy: String,
    @Column(name = "created_at") var createdAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "last_used_at") var lastUsedAt: LocalDateTime? = null,
    @Column(name = "revoked_at") var revokedAt: LocalDateTime? = null,
)
