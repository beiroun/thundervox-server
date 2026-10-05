-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- Console MVP: the operator manages SIP numbers directly. A number is a panel or an app client and its username
-- (the number itself) is the identity of the endpoint for now; devices / app clients / sites wait for later.
-- Console users get three roles: READER (view), ADMINISTRATOR (manage numbers and readers) and the single
-- SUPER_ADMINISTRATOR, who comes from the environment and is the only one allowed to manage administrators.
-- All tables touched here are empty when this runs (no API wrote to them before V4).

-- ---- sip_account: kind instead of owner type, a human name, HA1 as VARCHAR ----

ALTER TABLE sip_account DROP CONSTRAINT ck_sip_account_owner_type;
ALTER TABLE sip_account RENAME COLUMN owner_type TO kind;
ALTER TABLE sip_account ADD CONSTRAINT ck_sip_account_kind CHECK (kind IN ('PANEL', 'CLIENT'));

-- Free-form name of the endpoint (Russian and Latin alike): shown in the console, later the caller name in pushes
ALTER TABLE sip_account ADD COLUMN name VARCHAR(128) NOT NULL DEFAULT '';
ALTER TABLE sip_account ALTER COLUMN name DROP DEFAULT;

-- An MD5 hex digest is always 32 characters, but CHAR would make Hibernate's schema validation see a different
-- column type than the String mapping expects
ALTER TABLE sip_account ALTER COLUMN ha1 TYPE VARCHAR(32);
ALTER TABLE sip_account ALTER COLUMN ha1b TYPE VARCHAR(32);

-- Generated numbers: 8 digits, the first one names the kind (1 = app client, 2 = panel). Each kind draws from its
-- own sequence and never reuses a value; a number typed in by the operator may fall into a range, the allocator
-- skips taken values. NO CYCLE: an exhausted range fails loudly instead of wrapping into used numbers.
CREATE SEQUENCE sip_number_client_seq START WITH 10000001 MINVALUE 10000001 MAXVALUE 19999999 NO CYCLE;
CREATE SEQUENCE sip_number_panel_seq START WITH 20000001 MINVALUE 20000001 MAXVALUE 29999999 NO CYCLE;

-- ---- admin_user -> console_user: three roles, password change timestamp for token revocation ----

ALTER TABLE admin_user RENAME TO console_user;
-- The id default references the sequence by OID, so the rename keeps it working
ALTER SEQUENCE admin_user_seq RENAME TO console_user_seq;
ALTER TABLE console_user RENAME CONSTRAINT pk_admin_user TO pk_console_user;
ALTER TABLE console_user RENAME CONSTRAINT uq_admin_user_login TO uq_console_user_login;
ALTER TABLE console_user RENAME CONSTRAINT fk_admin_user_tenant TO fk_console_user_tenant;

ALTER TABLE console_user ALTER COLUMN role DROP DEFAULT;
ALTER TABLE console_user ALTER COLUMN role TYPE VARCHAR(32);
UPDATE console_user SET role = 'ADMINISTRATOR' WHERE role = 'ADMIN';
ALTER TABLE console_user ADD CONSTRAINT ck_console_user_role
    CHECK (role IN ('READER', 'ADMINISTRATOR', 'SUPER_ADMINISTRATOR'));

-- Tokens issued before this moment are rejected: a reset password or a disabled user logs out every session
ALTER TABLE console_user ADD COLUMN password_changed_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now();

-- At most one super administrator: the row the server keeps in step with the environment
CREATE UNIQUE INDEX uq_console_user_single_super_administrator ON console_user (role)
    WHERE role = 'SUPER_ADMINISTRATOR';
