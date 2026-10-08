// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import org.springframework.data.jpa.repository.JpaRepository

interface PushSettingsRepository : JpaRepository<PushSettingsEntity, Long>
