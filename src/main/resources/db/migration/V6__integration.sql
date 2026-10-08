-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- Integration with the operator's backend, managed from the console's "Integration" page:
--   service_token             - named tokens of the service API (replaces the single TVX_SERVICE_TOKEN of the environment)
--   integration_push_settings - where and how the wake push goes (replaces TVX_PUSH_URL / TVX_PUSH_TOKEN of the core's local.cfg)
--   push_delivery             - what happened to every wake push, for the delivery log of the page

-- ---- service_token ----
-- The value is never stored: only its SHA-256 and a short visible prefix to tell tokens apart in the console.
-- A revoked token keeps its row (the audit trail names it) and frees its name for a new one.
CREATE SEQUENCE service_token_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE service_token (
    id           BIGINT NOT NULL DEFAULT nextval('service_token_seq'),
    tenant_id    BIGINT NOT NULL,
    name         VARCHAR(64) NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    token_prefix VARCHAR(16) NOT NULL,
    enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   VARCHAR(64) NOT NULL,
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    last_used_at TIMESTAMP WITHOUT TIME ZONE,
    revoked_at   TIMESTAMP WITHOUT TIME ZONE,
    CONSTRAINT pk_service_token PRIMARY KEY (id),
    CONSTRAINT uq_service_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_service_token_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE UNIQUE INDEX uq_service_token_live_name ON service_token (tenant_id, name) WHERE enabled;
CREATE INDEX ix_service_token_tenant ON service_token (tenant_id);

-- ---- integration_push_settings ----
-- One row per tenant, created here switched off. auth_header_value is the operator backend's secret: the API
-- writes it and never reads it back (only whether it is set and its last characters).
CREATE TABLE integration_push_settings (
    tenant_id          BIGINT NOT NULL,
    enabled            BOOLEAN NOT NULL DEFAULT FALSE,
    url                VARCHAR(512) NOT NULL DEFAULT '',
    auth_header_name   VARCHAR(64) NOT NULL DEFAULT 'X-SERVICE-TOKEN',
    auth_header_value  VARCHAR(512),
    connect_timeout_ms INTEGER NOT NULL DEFAULT 2000,
    read_timeout_ms    INTEGER NOT NULL DEFAULT 3000,
    updated_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_by         VARCHAR(64),
    CONSTRAINT pk_integration_push_settings PRIMARY KEY (tenant_id),
    CONSTRAINT fk_integration_push_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
INSERT INTO integration_push_settings (tenant_id) SELECT id FROM tenant WHERE slug = 'default';

-- ---- push_delivery ----
-- One row per wake push attempt (live or a test from the page). Kept for a few days (housekeeping in the server),
-- so the "why did the push not arrive" question is answered from the console, not from container logs.
CREATE SEQUENCE push_delivery_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE push_delivery (
    id                 BIGINT NOT NULL DEFAULT nextval('push_delivery_seq'),
    tenant_id          BIGINT NOT NULL,
    call_id            UUID NOT NULL,
    sip_call_id        VARCHAR(256),
    kind               VARCHAR(8) NOT NULL,
    caller_number      VARCHAR(64),
    caller_external_id VARCHAR(128),
    callee_number      VARCHAR(64),
    callee_external_id VARCHAR(128),
    url                VARCHAR(512),
    outcome            VARCHAR(16) NOT NULL,
    http_status        INTEGER,
    attempts           SMALLINT NOT NULL DEFAULT 0,
    duration_ms        INTEGER,
    response_excerpt   VARCHAR(512),
    error              VARCHAR(512),
    created_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_push_delivery PRIMARY KEY (id),
    CONSTRAINT ck_push_delivery_kind CHECK (kind IN ('LIVE', 'TEST')),
    CONSTRAINT ck_push_delivery_outcome CHECK (outcome IN ('DELIVERED', 'REJECTED', 'FAILED', 'SKIPPED')),
    CONSTRAINT fk_push_delivery_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_push_delivery_tenant_created ON push_delivery (tenant_id, created_at DESC);
