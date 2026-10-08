// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.exception.base.InternalInconsistencyException
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.stereotype.Component

/** The push settings of the single tenant. V6 seeds the row, so its absence is a broken schema, not "not configured yet". */
@Component
class PushSettings(
    private val pushSettingsRepository: PushSettingsRepository,
    private val defaultTenant: DefaultTenant,
) {

    fun current(): PushSettingsEntity =
        pushSettingsRepository.findById(defaultTenant.id).orElseThrow {
            InternalInconsistencyException("integration_push_settings has no row for tenant ${defaultTenant.id}: the V6 seed was not applied")
        }
}
