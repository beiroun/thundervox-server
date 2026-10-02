// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/** The requested entity does not exist (410). */
class NotFoundException(
    message: String,
    localMessage: String = "Запись не найдена"
) : ApiException(message, localMessage, 410)
