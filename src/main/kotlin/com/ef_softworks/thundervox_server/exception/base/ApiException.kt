// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/**
 * Root of the typed error taxonomy. Every API error carries a technical [message], an operator-facing
 * [localMessage] and the HTTP [statusCode] of its category; the global handler turns it into a response body.
 * Thrown directly for business errors that fit no narrower category (513).
 */
open class ApiException(
    message: String,
    val localMessage: String = "Ошибка обработки запроса",
    val statusCode: Int = 513
) : RuntimeException(message)
