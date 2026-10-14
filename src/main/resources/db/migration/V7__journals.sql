-- SPDX-License-Identifier: BUSL-1.1
-- Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
--
-- Journals behind the dashboard and the real online/offline status of numbers:
--   registration_state - one row per SIP number ever seen registered: online now or not, since when, last seen
--                        (the core's location table is live state only - a number that expired is simply gone
--                        from it, so "never registered" and "was online an hour ago" were indistinguishable)
--   registration_event - one row per transition online <-> offline, never one per REGISTER (a panel refreshes
--                        every few minutes; thousands of panels would flood the database for nothing)
--   presence_sample    - how many numbers of each kind are online, sampled every half-minute, for the charts
--   call_log           - one row per call the core saw, fed by its call events (started / answered / ended)
-- All of them are written by the server only: the sampler reads location, the core posts call events.

-- ---- registration_state ----
CREATE TABLE registration_state (
    tenant_id           BIGINT NOT NULL,
    username            VARCHAR(64) NOT NULL,
    online              BOOLEAN NOT NULL,
    online_since        TIMESTAMP WITHOUT TIME ZONE,
    last_online_at      TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    last_offline_at     TIMESTAMP WITHOUT TIME ZONE,
    contact             VARCHAR(512),
    received            VARCHAR(128),
    user_agent          VARCHAR(255),
    contacts            SMALLINT NOT NULL DEFAULT 0,
    registrations_count INTEGER NOT NULL DEFAULT 0,
    updated_at          TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_registration_state PRIMARY KEY (tenant_id, username),
    CONSTRAINT fk_registration_state_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);

-- ---- registration_event ----
CREATE SEQUENCE registration_event_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE registration_event (
    id          BIGINT NOT NULL DEFAULT nextval('registration_event_seq'),
    tenant_id   BIGINT NOT NULL,
    username    VARCHAR(64) NOT NULL,
    kind        VARCHAR(8) NOT NULL,
    contact     VARCHAR(512),
    received    VARCHAR(128),
    user_agent  VARCHAR(255),
    occurred_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    CONSTRAINT pk_registration_event PRIMARY KEY (id),
    CONSTRAINT ck_registration_event_kind CHECK (kind IN ('ONLINE', 'OFFLINE')),
    CONSTRAINT fk_registration_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_registration_event_tenant_time ON registration_event (tenant_id, occurred_at DESC);
CREATE INDEX ix_registration_event_username ON registration_event (tenant_id, username, occurred_at DESC);

-- ---- presence_sample ----
-- "other" = numbers registered in the core without an account in sip_account (an open core during tests).
CREATE SEQUENCE presence_sample_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE presence_sample (
    id             BIGINT NOT NULL DEFAULT nextval('presence_sample_seq'),
    tenant_id      BIGINT NOT NULL,
    sampled_at     TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    panels_total   INTEGER NOT NULL,
    panels_online  INTEGER NOT NULL,
    clients_total  INTEGER NOT NULL,
    clients_online INTEGER NOT NULL,
    other_online   INTEGER NOT NULL,
    CONSTRAINT pk_presence_sample PRIMARY KEY (id),
    CONSTRAINT fk_presence_sample_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_presence_sample_tenant_time ON presence_sample (tenant_id, sampled_at);

-- ---- call_log ----
-- Keyed by the SIP Call-ID the core reports; the three events of a call update the same row. The outcome is
-- RINGING until the call ends, then ANSWERED / CANCELED / NO_ANSWER / REJECTED / FAILED.
CREATE SEQUENCE call_log_seq START WITH 1 INCREMENT BY 50;
CREATE TABLE call_log (
    id                 BIGINT NOT NULL DEFAULT nextval('call_log_seq'),
    tenant_id          BIGINT NOT NULL,
    sip_call_id        VARCHAR(256) NOT NULL,
    mode               VARCHAR(8),
    caller_number      VARCHAR(64),
    caller_external_id VARCHAR(128),
    caller_name        VARCHAR(128),
    callee_number      VARCHAR(64),
    callee_external_id VARCHAR(128),
    callee_name        VARCHAR(128),
    started_at         TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    answered_at        TIMESTAMP WITHOUT TIME ZONE,
    ended_at           TIMESTAMP WITHOUT TIME ZONE,
    end_reason         VARCHAR(16),
    end_code           INTEGER,
    outcome            VARCHAR(16) NOT NULL,
    CONSTRAINT pk_call_log PRIMARY KEY (id),
    CONSTRAINT uq_call_log_sip_call_id UNIQUE (tenant_id, sip_call_id),
    CONSTRAINT ck_call_log_mode CHECK (mode IS NULL OR mode IN ('RELAY', 'WAKE')),
    CONSTRAINT ck_call_log_outcome CHECK (outcome IN ('RINGING', 'ANSWERED', 'CANCELED', 'NO_ANSWER', 'REJECTED', 'FAILED')),
    CONSTRAINT fk_call_log_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id) ON DELETE RESTRICT
);
CREATE INDEX ix_call_log_tenant_started ON call_log (tenant_id, started_at DESC);
