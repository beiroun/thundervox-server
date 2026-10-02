// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/** Data is in a state the code considers impossible, e.g. a SIP account without an owner (510). */
class InternalInconsistencyException(
    message: String,
    localMessage: String = "Внутреннее несогласование данных"
) : ApiException(message, localMessage, 510)
