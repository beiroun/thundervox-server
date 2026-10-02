-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- Database role of the SIP core (kamailio.cfg: TVX_DB_URL = postgres://tvx_sip:<password>@127.0.0.1:5432/<db>).
-- Least privilege: auth_db reads subscriber and version, usrloc owns the rows of location. Nothing else.
-- The password comes from the Flyway placeholder tvx_sip_db_password (TVX_SIP_DB_PASSWORD in the environment).
-- Requires the migration user to have CREATEROLE; in the compose deployment it is the database superuser.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'tvx_sip') THEN
        CREATE ROLE tvx_sip LOGIN PASSWORD '${tvx_sip_db_password}';
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO tvx_sip', current_database());
END
$$;

GRANT USAGE ON SCHEMA public TO tvx_sip;
GRANT SELECT ON version, subscriber TO tvx_sip;
GRANT SELECT, INSERT, UPDATE, DELETE ON location TO tvx_sip;
GRANT USAGE, SELECT ON SEQUENCE location_id_seq TO tvx_sip;
