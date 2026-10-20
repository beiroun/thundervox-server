// SPDX-License-Identifier: BUSL-1.1
// Copyright (c) 2026 Andrei Baranov (84softworks). Licensed under the Business Source License 1.1 - see LICENSE.
package com.ef_softworks.thundervox_server.search

import org.springframework.jdbc.core.simple.JdbcClient

/**
 * A smart search over a journal table in SQL: the document is the row's searchable fields glued and normalized
 * like the query, followed by its copy with everything but digits and spaces removed – the query turns
 * "178.74.67.30" into 178746730 (the rule is made for phones typed with dots), and the copy is what finds
 * 178.74.67.30:5060. Every word must be a substring of the document (AND); with a one-word query the rows whose key
 * equals it come first.
 *
 * [searchableFields] and [exactKeys] are SQL expressions over the table's own columns, never operator input: the
 * words always travel as bound parameters.
 */
class SmartSearchSql(
    private val query: SmartQuery?,
    searchableFields: List<String>,
    exactKeys: List<String>,
) {
    private val document = run {
        val text = "translate(lower(concat_ws(' ', ${searchableFields.joinToString(", ")})), 'ё', 'е')"
        "($text || ' ' || regexp_replace($text, '[^0-9[:space:]]+', '', 'g'))"
    }

    private val normalizedKeys = exactKeys.joinToString(", ") { "translate(lower($it), 'ё', 'е')" }

    /** `AND …` for the WHERE clause, empty without a query. */
    val condition: String =
        query?.words?.indices?.joinToString(separator = "") { index -> " AND $document LIKE :searchWord$index ESCAPE '\\'" }
            .orEmpty()

    /** `CASE … END, ` before the journal's own order, empty unless the query is one word. */
    val exactFirstOrder: String =
        if (query?.exactCandidate != null && exactKeys.isNotEmpty()) {
            "CASE WHEN CAST(:searchExact AS text) IN ($normalizedKeys) THEN 0 ELSE 1 END, "
        } else {
            ""
        }

    fun bind(statement: JdbcClient.StatementSpec): JdbcClient.StatementSpec {
        if (query == null) {
            return statement
        }
        var bound = statement
        query.words.forEachIndexed { index, word -> bound = bound.param("searchWord$index", "%${escapeLike(word)}%") }
        if (exactFirstOrder.isNotEmpty()) {
            bound = bound.param("searchExact", query.exactCandidate)
        }
        return bound
    }

    // A typed % or _ is a character to find, not a wildcard
    private fun escapeLike(word: String): String =
        word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
