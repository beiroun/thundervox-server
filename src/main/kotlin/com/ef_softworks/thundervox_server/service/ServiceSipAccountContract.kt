// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.service

import com.ef_softworks.thundervox_server.sipaccount.SipAccountKind
import com.ef_softworks.thundervox_server.sipaccount.SipAccountResponse
import com.ef_softworks.thundervox_server.sipaccount.SipRegistrationResponse
import com.fasterxml.jackson.annotation.JsonProperty

/** Body of PUT /service/sip-accounts/{kind}/{external_id}; every field is optional, an empty body is valid. */
data class EnsureSipAccountRequest(
    /** Human label of the number. On creation it defaults to the external id; on an existing number it changes only when present. */
    @JsonProperty("name") val name: String? = null,
    /** Existing number only: issue a new password. The device registered now logs out at its next registration. */
    @JsonProperty("rotate_password") val rotatePassword: Boolean = false,
)

/** What the operator's backend needs to put a SIP account into a device or an app. */
data class ServiceSipAccountResponse(
    /** The SIP number: username and authentication username on the device. */
    @JsonProperty("username") val username: String,
    @JsonProperty("kind") val kind: SipAccountKind,
    @JsonProperty("external_id") val externalId: String?,
    @JsonProperty("name") val name: String,
    @JsonProperty("enabled") val enabled: Boolean,
    /** Digest realm the password is hashed with; entered on the device next to the number and the password. */
    @JsonProperty("realm") val realm: String,
    /** The SIP domain devices register to and dial through (equals the realm in this deployment). */
    @JsonProperty("sip_domain") val sipDomain: String,
    /** Only when this call made a password up: on creation and on rotate_password. Never stored, never returned again. */
    @JsonProperty("password") val password: String?,
    /** True when this call created the number, false when the endpoint already had one. */
    @JsonProperty("created") val created: Boolean,
    @JsonProperty("registration") val registration: SipRegistrationResponse?,
) {
    companion object {
        fun from(account: SipAccountResponse, sipDomain: String, password: String?, created: Boolean) = ServiceSipAccountResponse(
            username = account.username,
            kind = account.kind,
            externalId = account.externalId,
            name = account.name,
            enabled = account.enabled,
            realm = sipDomain,
            sipDomain = sipDomain,
            password = password,
            created = created,
            registration = account.registration,
        )
    }
}

/** GET /service/sip-accounts/{kind}/{external_id}/registration: whether to wake the endpoint with a push at all. */
data class ServiceRegistrationResponse(
    @JsonProperty("username") val username: String,
    @JsonProperty("online") val online: Boolean,
    @JsonProperty("registration") val registration: SipRegistrationResponse?,
)
