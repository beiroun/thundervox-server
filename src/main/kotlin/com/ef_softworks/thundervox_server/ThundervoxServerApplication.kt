// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Provisioning server of the ThunderVox platform: owns the schema the SIP core authenticates against. */
@SpringBootApplication
class ThundervoxServerApplication

fun main(args: Array<String>) {
    runApplication<ThundervoxServerApplication>(*args)
}
