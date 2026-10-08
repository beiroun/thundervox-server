// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.integration

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException
import java.net.URI

/** What may be entered as the push gateway: an absolute http(s) URL, a header name as HTTP spells it, sane timeouts. */
object PushSettingsInputRules {
    const val MAX_URL_LENGTH = 512
    const val MAX_HEADER_VALUE_LENGTH = 512
    const val MIN_TIMEOUT_MS = 200
    const val MAX_TIMEOUT_MS = 30_000

    private val headerNamePattern = Regex("^[A-Za-z0-9-]{1,64}$")

    /** Empty is allowed: the push stays configured but switched off until a URL is set. */
    fun requireValidUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        val parsed = runCatching { URI(trimmed) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase()
        if (trimmed.length > MAX_URL_LENGTH || parsed == null || parsed.host.isNullOrEmpty() || (scheme != "http" && scheme != "https")) {
            throw WrongTypeException(
                "Push URL '$trimmed' is not an absolute http(s) URL of at most $MAX_URL_LENGTH characters",
                "Адрес пуша: полный URL, начинающийся с http:// или https://, не длиннее $MAX_URL_LENGTH символов"
            )
        }
        return trimmed
    }

    fun requireValidHeaderName(name: String): String {
        val trimmed = name.trim()
        if (!headerNamePattern.matches(trimmed)) {
            throw WrongTypeException(
                "Push auth header name '$trimmed' does not match ${headerNamePattern.pattern}",
                "Имя заголовка: латинские буквы, цифры и '-', до 64 символов"
            )
        }
        return trimmed
    }

    /** Null = unchanged, empty = cleared, otherwise the new secret as typed (no trimming: a secret is exact). */
    fun requireValidHeaderValue(value: String?): String? {
        if (value == null || value.isEmpty()) {
            return value
        }
        if (value.length > MAX_HEADER_VALUE_LENGTH || value.any { it.isISOControl() }) {
            throw WrongTypeException(
                "Push auth header value of length ${value.length} is too long or has control characters",
                "Значение заголовка: до $MAX_HEADER_VALUE_LENGTH печатных символов"
            )
        }
        return value
    }

    fun requireValidTimeout(timeoutMs: Int, what: String): Int {
        if (timeoutMs !in MIN_TIMEOUT_MS..MAX_TIMEOUT_MS) {
            throw WrongTypeException(
                "Push $what timeout $timeoutMs ms is outside $MIN_TIMEOUT_MS..$MAX_TIMEOUT_MS",
                "Таймаут: от $MIN_TIMEOUT_MS до $MAX_TIMEOUT_MS мс"
            )
        }
        return timeoutMs
    }
}
