// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.dashboard

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Counters and time series for the console's landing page. Any signed-in role may read it. */
@RestController
@RequestMapping("/dashboard")
class DashboardController(private val dashboardQuery: DashboardQuery) {

    /** `range` is one of 1h, 6h, 1d, 1w, 1m (default 1h); the bucket of the series follows from it. */
    @GetMapping
    fun dashboard(@RequestParam("range", required = false) range: String?): ResponseEntity<BaseApiResponse<DashboardResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(dashboardQuery.build(DashboardRange.parse(range ?: DashboardRange.HOUR.code))))
}
