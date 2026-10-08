// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.audit

/** What happened. Stored by name: renaming a constant rewrites history, add new ones instead. */
enum class AuditAction {
    SIP_ACCOUNT_CREATED,
    /** Historical (console 0.4): name changes only. Since 0.5 a change of name or external id is SIP_ACCOUNT_DETAILS_UPDATED. */
    SIP_ACCOUNT_RENAMED,
    SIP_ACCOUNT_DETAILS_UPDATED,
    SIP_ACCOUNT_PASSWORD_ROTATED,
    SIP_ACCOUNT_BLOCKED,
    SIP_ACCOUNT_UNBLOCKED,
    SIP_ACCOUNT_DELETED,
    CONSOLE_USER_CREATED,
    CONSOLE_USER_UPDATED,
    CONSOLE_USER_PASSWORD_RESET,
    SUPER_ADMINISTRATOR_SYNCED,
    SERVICE_TOKEN_CREATED,
    SERVICE_TOKEN_REVOKED,
    PUSH_SETTINGS_UPDATED,
    PUSH_TEST_SENT,
}

/** What the action was done to. */
enum class AuditTargetType {
    SIP_ACCOUNT,
    CONSOLE_USER,
    SERVICE_TOKEN,
    PUSH_SETTINGS,
}

/** Who did it, in the vocabulary of the admin_action_log.actor_type check constraint. */
enum class AuditActorType {
    /** A console user (administrator or super administrator; readers change nothing). */
    ADMIN,
    /** The operator's backend through the service API. */
    SERVICE,
    /** The server itself: start-up synchronisation, housekeeping. */
    SYSTEM,
}

data class AuditActor(val type: AuditActorType, val login: String) {
    companion object {
        val SYSTEM = AuditActor(AuditActorType.SYSTEM, "system")

        /** The operator's backend, named by the service token it presented: every service API change is signed by it. */
        fun serviceToken(tokenName: String) = AuditActor(AuditActorType.SERVICE, "token:$tokenName")

        fun consoleUser(login: String) = AuditActor(AuditActorType.ADMIN, login)
    }
}
