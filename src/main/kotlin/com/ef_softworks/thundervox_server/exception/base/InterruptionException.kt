// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/** Deliberate interruption of a flow; the client gets a generic body, the detail stays in the log (514). */
class InterruptionException(
    message: String,
    localMessage: String = "Операция прервана"
) : ApiException(message, localMessage, 514)
