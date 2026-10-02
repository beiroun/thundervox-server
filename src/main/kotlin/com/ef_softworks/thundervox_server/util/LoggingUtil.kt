// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.util

import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

// One logger per class, resolved from the receiver and cached, so callers never declare a logger field.
private val loggersByClassName = ConcurrentHashMap<String, KLogger>()

private fun Any.classLogger(): KLogger =
    loggersByClassName.computeIfAbsent(this::class.java.name) { KotlinLogging.logger(it) }

/** debug: parameters and intermediate state while tracing a flow. */
fun Any.logDebug(message: String) = classLogger().debug { message }

/** info: business events worth seeing on a dashboard (account issued, registration evicted, call terminated). */
fun Any.logInfo(message: String) = classLogger().info { message }

/** warn: suspicious input or a fallback path that has not lost data yet. */
fun Any.logWarn(message: String, throwable: Throwable? = null) = classLogger().warn(throwable) { message }

/** error: failures and every point where data may be lost. */
fun Any.logError(message: String, throwable: Throwable? = null) = classLogger().error(throwable) { message }
