// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.exception.base

/** The caller is not allowed to do this: missing or wrong token, foreign tenant (413). */
class InsufficientPrivilegesException(
    message: String,
    localMessage: String = "Недостаточно прав"
) : ApiException(message, localMessage, 413)
