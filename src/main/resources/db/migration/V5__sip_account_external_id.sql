-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- external_id: the endpoint's identifier in the operator's own system - the key the service API finds a number by,
-- so the same endpoint always gets the same number. A panel carries the id the operator's backend knows it by
-- (Modus: "ip:port" - which video to show when it calls); an app client carries the subscriber account - whom to
-- wake with a push when the number is called. `name` stays a human label and is never a key.
-- Numbers made by hand in the console may have no external id; the service API always sets one.

ALTER TABLE sip_account ADD COLUMN external_id VARCHAR(128);

-- One id per kind within a tenant. Partial: console-made numbers without an id do not collide with each other.
CREATE UNIQUE INDEX uq_sip_account_external_id ON sip_account (tenant_id, kind, external_id)
    WHERE external_id IS NOT NULL;
