-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- Provisioning domain: who may register with the core and under which SIP username.
-- Conventions: BIGINT ids from a sequence with INCREMENT BY 50 (Hibernate pooled allocation), snake_case,
-- created_at on every table, tenant_id everywhere from day one (one tenant in v1, no tenant UI yet).

-- Operator / customer company (B2B2B). v1 runs with the single seeded tenant.
CREATE SEQUENCE tenant_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE tenant (
    id         BIGINT NOT NULL DEFAULT nextval('tenant_seq'),
    name       VARCHAR(128) NOT NULL,
    slug       VARCHAR(64) NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_slug UNIQUE (slug)
);

-- Physical object: a building, an entrance, a parking lot. Groups devices; external_ref is the operator's own id.
CREATE SEQUENCE site_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE site (
    id           BIGINT NOT NULL DEFAULT nextval('site_seq'),
    tenant_id    BIGINT NOT NULL,
    name         VARCHAR(128) NOT NULL,
    address      VARCHAR(256),
    external_ref VARCHAR(64),
    created_at   TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_site PRIMARY KEY (id),
    CONSTRAINT fk_site_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_site_tenant ON site (tenant_id);

-- SIP credentials. One account per owner (device or app client). The password is never stored: only the digest
-- hashes the core compares against (ha1 = md5(username:realm:password), ha1b = md5(username@realm:realm:password)).
-- username is globally unique because the core matches by user part only (usrloc / auth_db use_domain=0).
CREATE SEQUENCE sip_account_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE sip_account (
    id                  BIGINT NOT NULL DEFAULT nextval('sip_account_seq'),
    tenant_id           BIGINT NOT NULL,
    username            VARCHAR(64) NOT NULL,
    realm               VARCHAR(128) NOT NULL,
    ha1                 CHAR(32) NOT NULL,
    ha1b                CHAR(32) NOT NULL,
    owner_type          VARCHAR(16) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    password_rotated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    created_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_sip_account PRIMARY KEY (id),
    CONSTRAINT uq_sip_account_username UNIQUE (username),
    CONSTRAINT ck_sip_account_owner_type CHECK (owner_type IN ('DEVICE', 'APP_CLIENT')),
    CONSTRAINT fk_sip_account_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_sip_account_tenant ON sip_account (tenant_id);

-- Physical SIP endpoint: intercom panel today, elevator / gate / SOS point later. vendor selects the adapter.
-- Deleting an owner is not allowed while its account exists: accounts are disabled, not dropped.
CREATE SEQUENCE device_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE device (
    id             BIGINT NOT NULL DEFAULT nextval('device_seq'),
    tenant_id      BIGINT NOT NULL,
    site_id        BIGINT,
    label          VARCHAR(128) NOT NULL,
    vendor         VARCHAR(32) NOT NULL,
    model          VARCHAR(64),
    firmware       VARCHAR(64),
    note           VARCHAR(512),
    sip_account_id BIGINT,
    created_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_device PRIMARY KEY (id),
    CONSTRAINT uq_device_sip_account UNIQUE (sip_account_id),
    CONSTRAINT fk_device_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_site FOREIGN KEY (site_id) REFERENCES site (id) ON DELETE RESTRICT,
    CONSTRAINT fk_device_sip_account FOREIGN KEY (sip_account_id) REFERENCES sip_account (id) ON DELETE RESTRICT
);
CREATE INDEX ix_device_tenant ON device (tenant_id);
CREATE INDEX ix_device_site ON device (site_id);

-- Mobile app user. external_client_id is the operator backend's client id (callee_id of the push contract).
CREATE SEQUENCE app_client_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE app_client (
    id                 BIGINT NOT NULL DEFAULT nextval('app_client_seq'),
    tenant_id          BIGINT NOT NULL,
    external_client_id VARCHAR(64) NOT NULL,
    label              VARCHAR(128),
    sip_account_id     BIGINT,
    created_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_app_client PRIMARY KEY (id),
    CONSTRAINT uq_app_client_external UNIQUE (tenant_id, external_client_id),
    CONSTRAINT uq_app_client_sip_account UNIQUE (sip_account_id),
    CONSTRAINT fk_app_client_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT,
    CONSTRAINT fk_app_client_sip_account FOREIGN KEY (sip_account_id) REFERENCES sip_account (id) ON DELETE RESTRICT
);

-- Console user. Password as BCrypt hash. The first admin is seeded on start from the environment (T5).
CREATE SEQUENCE admin_user_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE admin_user (
    id            BIGINT NOT NULL DEFAULT nextval('admin_user_seq'),
    tenant_id     BIGINT NOT NULL,
    login         VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role          VARCHAR(16) NOT NULL DEFAULT 'ADMIN',
    enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_admin_user PRIMARY KEY (id),
    CONSTRAINT uq_admin_user_login UNIQUE (login),
    CONSTRAINT fk_admin_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);

-- Audit trail of everything that changes who may register: who issued or rotated a password, blocked a device,
-- evicted a registration, terminated a call. Append-only; shown in the console. actor_type SERVICE = the operator's
-- backend through the service API, SYSTEM = the server itself (seeding, housekeeping).
CREATE SEQUENCE admin_action_log_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE admin_action_log (
    id          BIGINT NOT NULL DEFAULT nextval('admin_action_log_seq'),
    tenant_id   BIGINT NOT NULL,
    actor_type  VARCHAR(16) NOT NULL,
    actor_login VARCHAR(64) NOT NULL,
    action      VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id   BIGINT,
    details     JSONB,
    created_at  TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_admin_action_log PRIMARY KEY (id),
    CONSTRAINT ck_admin_action_log_actor_type CHECK (actor_type IN ('ADMIN', 'SERVICE', 'SYSTEM')),
    CONSTRAINT fk_admin_action_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_admin_action_log_tenant_created ON admin_action_log (tenant_id, created_at DESC);
CREATE INDEX ix_admin_action_log_target ON admin_action_log (target_type, target_id);

-- The single tenant of v1. Everything created through the API lands here until tenant management exists.
INSERT INTO tenant (name, slug) VALUES ('Default', 'default');
