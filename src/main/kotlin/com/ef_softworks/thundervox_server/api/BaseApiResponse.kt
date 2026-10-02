// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.api

/**
 * Envelope of every endpoint. Success carries [data] with message "OK"; failure carries [error] with message "FAIL"
 * and the HTTP status of the error category, so a client never gets a bodiless error.
 */
data class BaseApiResponse<T>(
    val data: T? = null,
    val message: String,
    val error: FieldErrorDto? = null
) {
    companion object {
        const val SUCCESS = "OK"
        const val FAILURE = "FAIL"

        fun <T> ok(data: T): BaseApiResponse<T> = BaseApiResponse(data = data, message = SUCCESS)

        fun fail(error: FieldErrorDto): BaseApiResponse<Nothing?> = BaseApiResponse(message = FAILURE, error = error)
    }
}

/** Error body: [message] is technical (for the log and the developer), [localizedMessage] is shown to the operator. */
data class FieldErrorDto(
    val message: String,
    val localizedMessage: String
)
