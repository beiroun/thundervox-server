// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.platform

import com.fasterxml.jackson.annotation.JsonProperty

data class PlatformInfoResponse(
    @JsonProperty("name") val name: String,
    @JsonProperty("version") val version: String,
    @JsonProperty("license") val license: String
)
