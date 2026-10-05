// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.audit

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Audit trail for the console; every role may read it. */
@RestController
@RequestMapping("/audit")
class AuditController(private val auditLogQuery: AuditLogQuery) {

    @GetMapping
    fun page(
        @RequestParam("page", required = false, defaultValue = "0") page: Int,
        @RequestParam("size", required = false, defaultValue = "50") size: Int,
    ): ResponseEntity<BaseApiResponse<AuditPageResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(auditLogQuery.page(page, size)))
}
