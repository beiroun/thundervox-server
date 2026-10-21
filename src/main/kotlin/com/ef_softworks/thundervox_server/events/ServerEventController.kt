// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.events

import com.ef_softworks.thundervox_server.api.BaseApiResponse
import com.ef_softworks.thundervox_server.consoleuser.currentConsoleUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** The event log of the console; every role may read it, readers without the Integration side. */
@RestController
@RequestMapping("/events")
class ServerEventController(private val serverEventLog: ServerEventLog) {

    @GetMapping
    fun page(
        @RequestParam("page", required = false, defaultValue = "0") page: Int,
        @RequestParam("size", required = false, defaultValue = "50") size: Int,
        /** One journal; absent – every journal the viewer may see. */
        @RequestParam("category", required = false) category: ServerEventCategory?,
        /** Only what did not get through. */
        @RequestParam("problems", required = false, defaultValue = "false") problemsOnly: Boolean,
        /** Smart search over every journal's own fields; absent – the whole log. */
        @RequestParam("query", required = false) query: String?,
    ): ResponseEntity<BaseApiResponse<ServerEventPageResponse>> =
        ResponseEntity.ok(
            BaseApiResponse.ok(
                serverEventLog.page(ServerEventFilter(category, problemsOnly, query, page, size), currentConsoleUser().role)
            )
        )
}
