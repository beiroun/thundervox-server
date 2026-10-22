-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- The registration journal keeps panels only (decision of 2026-10-22). An app client registers on every wake push
-- and a softphone on every reconnect: a line per transition of 300k subscribers is tens of millions of rows a
-- year that nobody reads. What matters about a subscriber is whether the wake push got it online during its call,
-- and that now lives on the call itself; registration_state (one row per number) keeps every number's state.

-- When the callee came online while its WAKE call waited: the wake push reached the phone
ALTER TABLE call_log ADD COLUMN callee_registered_at TIMESTAMP WITHOUT TIME ZONE;

-- Lines of numbers that are not panels (app clients, numbers without an account) leave the journal
DELETE FROM registration_event e
WHERE NOT EXISTS (
    SELECT 1 FROM sip_account a
    WHERE a.tenant_id = e.tenant_id AND a.username = e.username AND a.kind = 'PANEL'
);
