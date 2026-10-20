// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.search

import com.ef_softworks.thundervox_server.exception.base.WrongTypeException

/**
 * Smart search, one rule for every table of the console (thundervox-web src/shared/smartQuery.ts): lower case and
 * `ё → е`; a query of digits, `+ - ( ) .` and spaces is one number; otherwise words split by spaces, commas and `;`,
 * a word of digits with punctuation keeps only the digits; at most six words, at least one of them three characters
 * long, the whole query at most 100. A row matches when its document contains every word; a one-word query equal to
 * a key of the row puts that row first.
 */
data class SmartQuery(
    /** Normalized words; a row matches when its document contains every one of them. */
    val words: List<String>,
    /** The single word of a one-word query: rows whose key equals it go first. */
    val exactCandidate: String?,
) {
    companion object {
        const val MIN_WORD_LENGTH = 3
        const val MAX_LENGTH = 100
        private const val MAX_WORDS = 6

        private val phoneLikeQuery = Regex("^[\\d+\\-().\\s]+$")
        private val digitsWithPunctuation = Regex("^[\\d+\\-().]+$")
        private val wordSeparators = Regex("[\\s,;]+")
        private val nonDigits = Regex("\\D")

        fun normalize(text: String): String = text.lowercase().replace('ё', 'е')

        /**
         * The parsed query; null for an empty one (no condition). The console never sends a query that cannot search,
         * so one that does is refused rather than silently ignored.
         */
        fun parse(raw: String?): SmartQuery? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                return null
            }
            if (trimmed.length > MAX_LENGTH) {
                throw WrongTypeException(
                    "Search query is ${trimmed.length} characters long, at most $MAX_LENGTH",
                    "Запрос длиннее $MAX_LENGTH символов"
                )
            }
            val words = wordsOf(normalize(trimmed))
            if (words.none { it.length >= MIN_WORD_LENGTH }) {
                throw WrongTypeException(
                    "Search query has no word of $MIN_WORD_LENGTH characters: '$trimmed'",
                    "Для поиска введите не меньше $MIN_WORD_LENGTH символов"
                )
            }
            return SmartQuery(words, words.singleOrNull())
        }

        private fun wordsOf(normalized: String): List<String> {
            // A phone typed with spaces and brackets is one number, not five words
            if (phoneLikeQuery.matches(normalized)) {
                return listOf(normalized.replace(nonDigits, "")).filter { it.isNotEmpty() }
            }
            return normalized.split(wordSeparators)
                .map { word -> if (digitsWithPunctuation.matches(word)) word.replace(nonDigits, "") else word }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_WORDS)
        }
    }
}
