-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- service_call_log - one row per request to the service API (/service/**), refused ones included: which operator
-- backend called (the token it presented), what it asked for, about which endpoint, and what it got back. Together
-- with registration_event, call_log and push_delivery it makes the console's event log. Written by the server
-- only, off the request thread; kept for a month (housekeeping in the server).

CREATE SEQUENCE service_call_log_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE service_call_log (
    id             BIGINT NOT NULL DEFAULT nextval('service_call_log_seq'),
    tenant_id      BIGINT NOT NULL,
    occurred_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    -- TOKEN: a live token; UNKNOWN_TOKEN: a token that is wrong or revoked; NO_TOKEN: no X-SERVICE-TOKEN header
    caller_auth    VARCHAR(16) NOT NULL,
    -- The live token's row and its name at the time of the call (a revoked token keeps its row, so the name stays)
    token_id       BIGINT,
    token_name     VARCHAR(64),
    -- First characters of whatever was presented: tells an old revoked token from garbage without storing a secret
    token_prefix   VARCHAR(16),
    method         VARCHAR(8) NOT NULL,
    path           VARCHAR(512) NOT NULL,
    operation      VARCHAR(16) NOT NULL,
    -- The endpoint the call was about, from the path; username is its SIP number when one exists
    sip_kind       VARCHAR(8),
    external_id    VARCHAR(128),
    username       VARCHAR(64),
    http_status    INTEGER NOT NULL,
    duration_ms    INTEGER NOT NULL,
    remote_address VARCHAR(64),
    -- The error of a refused or failed call, as the response said it; never the body of a success (it may carry a password)
    error          VARCHAR(512),
    CONSTRAINT pk_service_call_log PRIMARY KEY (id),
    CONSTRAINT ck_service_call_log_caller_auth CHECK (caller_auth IN ('TOKEN', 'UNKNOWN_TOKEN', 'NO_TOKEN')),
    CONSTRAINT ck_service_call_log_operation CHECK (operation IN ('READ', 'ENSURE', 'DISABLE', 'REGISTRATION', 'OTHER')),
    CONSTRAINT fk_service_call_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_service_call_log_tenant_time ON service_call_log (tenant_id, occurred_at DESC);
