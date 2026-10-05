// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.consoleuser

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.SequenceGenerator
import jakarta.persistence.Table
import java.time.LocalDateTime

/** Person who logs in to the console. The password is stored only as a BCrypt hash. */
@Entity
@Table(name = "console_user")
class ConsoleUserEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "console_user_seq")
    @SequenceGenerator(name = "console_user_seq", sequenceName = "console_user_seq", allocationSize = 50)
    var id: Long = 0,
    @Column(name = "tenant_id") var tenantId: Long,
    @Column(name = "login") var login: String,
    @Column(name = "password_hash") var passwordHash: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "role") var role: ConsoleRole,
    @Column(name = "enabled") var enabled: Boolean = true,
    @Column(name = "created_at") var createdAt: LocalDateTime = LocalDateTime.now(),
    /** Tokens issued before this moment are rejected (see AuthenticateConsoleToken). */
    @Column(name = "password_changed_at") var passwordChangedAt: LocalDateTime = LocalDateTime.now(),
)
