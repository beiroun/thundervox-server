// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.sipaccount

/**
 * What stands behind a SIP number. The kind picks the range a generated number comes from, so the first digit of
 * a generated number tells the kind in any log line (1… = app client, 2… = panel).
 */
enum class SipAccountKind(val numberSequence: String) {
    /** Intercom panel (later: elevator, gate, SOS point) - a device that calls. */
    PANEL("sip_number_panel_seq"),
    /** Mobile app user - the one who gets called. */
    CLIENT("sip_number_client_seq"),
}
