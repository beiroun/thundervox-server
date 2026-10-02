// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/** Input validation failed: wrong type or format of a value (511). */
class WrongTypeException(
    message: String,
    localMessage: String = "Неверный формат данных"
) : ApiException(message, localMessage, 511)
