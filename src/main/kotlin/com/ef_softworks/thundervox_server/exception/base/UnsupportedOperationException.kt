// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/** The operation is not supported in this context, e.g. rotating the password of a blocked account (512). */
class UnsupportedOperationException(
    message: String,
    localMessage: String = "Операция не поддерживается"
) : ApiException(message, localMessage, 512)
