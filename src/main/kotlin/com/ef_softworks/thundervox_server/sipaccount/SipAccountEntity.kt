// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

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

/**
 * SIP number of a panel or an app client. [username] is the number itself and, for now, the identity of the
 * endpoint. The password is never stored: only the two digests the core compares against.
 */
@Entity
@Table(name = "sip_account")
class SipAccountEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sip_account_seq")
    @SequenceGenerator(name = "sip_account_seq", sequenceName = "sip_account_seq", allocationSize = 50)
    var id: Long = 0,
    @Column(name = "tenant_id") var tenantId: Long,
    @Column(name = "username") var username: String,
    /** Realm the digests were computed with; must equal the core's TVX_SIP_DOMAIN for the password to work. */
    @Column(name = "realm") var realm: String,
    @Column(name = "ha1") var ha1: String,
    @Column(name = "ha1b") var ha1b: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "kind") var kind: SipAccountKind,
    @Column(name = "name") var name: String,
    /** A blocked account keeps its digests (unblocking needs no new password) but has no row in subscriber. */
    @Column(name = "enabled") var enabled: Boolean = true,
    @Column(name = "password_rotated_at") var passwordRotatedAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "created_at") var createdAt: LocalDateTime = LocalDateTime.now(),
) {
    fun setPassword(password: String, realm: String) {
        this.realm = realm
        ha1 = Ha1Digest.ha1(username, realm, password)
        ha1b = Ha1Digest.ha1b(username, realm, password)
        passwordRotatedAt = LocalDateTime.now()
    }
}
