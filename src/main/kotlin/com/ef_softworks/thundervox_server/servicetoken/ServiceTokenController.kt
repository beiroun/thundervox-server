// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.servicetoken

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.tenant.DefaultTenant
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Service API tokens for the Integration page. Administrators see the list; issuing and revoking take the super
 * administrator (the URL rules and the use cases both say so).
 */
@RestController
@RequestMapping("/integration/tokens")
class ServiceTokenController(
    private val serviceTokenRepository: ServiceTokenRepository,
    private val defaultTenant: DefaultTenant,
    private val issueServiceToken: IssueServiceToken,
    private val revokeServiceToken: RevokeServiceToken,
) {

    @GetMapping
    @Transactional(readOnly = true)
    fun list(): ResponseEntity<BaseApiResponse<List<ServiceTokenResponse>>> =
        ResponseEntity.ok(
            BaseApiResponse.ok(
                serviceTokenRepository.findAllByTenantIdOrderByCreatedAtDesc(defaultTenant.id).map(ServiceTokenResponse::from)
            )
        )

    @PostMapping
    fun issue(@RequestBody request: IssueServiceTokenRequest): ResponseEntity<BaseApiResponse<IssuedServiceTokenResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(issueServiceToken.issue(request)))

    @DeleteMapping("/{id}")
    fun revoke(@PathVariable("id") id: Long): ResponseEntity<BaseApiResponse<ServiceTokenResponse>> =
        ResponseEntity.ok(BaseApiResponse.ok(revokeServiceToken.revoke(id)))
}
