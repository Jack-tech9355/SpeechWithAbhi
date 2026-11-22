package com.speechwithabhi.app.feature.cleanup

/**
 * Intelligent Table Conversationalizer.
 * Detects tabular structures (tab-delimited, pipe-delimited, or CSV-formatted blocks)
 * and transforms rows into clear, conversational sentences preserving header-to-cell relationships.
 */
class TableConversationalizer {

    /**
     * Checks if the given text represents a table.
     * Supports both Markdown pipe-delimited (| col1 | col2 |) and tab-delimited structures.
     */
    fun isTable(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 2) return false

        // Check for Markdown table pipes (| col1 | col2 | or col1 | col2)
        val pipeLineCount = lines.count { line ->
            line.count { it == '|' } >= 2
        }
        if (pipeLineCount >= 2) return true

        // Check for tab-separated columns
        val tabLineCount = lines.count { line ->
            line.contains('\t')
        }
        if (tabLineCount >= 2) return true

        return false
    }

    /**
     * Converts a raw tabular string into natural spoken sentences.
     */
    fun conversationalize(text: String): String {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.matches(Regex("""^\|?[\s\-:]+(\|[\s\-:]+)+\|?$""")) } // Exclude markdown separator row

        if (lines.size < 2) return text

        val parsedRows: List<List<String>> = lines.map { parseRow(it) }
        if (parsedRows.isEmpty()) return text

        val headers = parsedRows.first()
        val dataRows = parsedRows.drop(1)

        if (headers.size < 2) return text

        val spokenBuilder = StringBuilder()
        spokenBuilder.append("Table details: ")

        for ((rowIndex, row) in dataRows.withIndex()) {
            if (row.isEmpty()) continue

            val rowSubject = row.firstOrNull() ?: "Item ${rowIndex + 1}"
            spokenBuilder.append("For $rowSubject, ")

            val cellStatements = mutableListOf<String>()
            for (colIndex in 1 until minOf(headers.size, row.size)) {
                val headerName = headers[colIndex]
                val cellValue = row[colIndex]
                if (cellValue.isNotBlank()) {
                    cellStatements.add("$headerName is $cellValue")
                }
            }

            if (cellStatements.isNotEmpty()) {
                spokenBuilder.append(cellStatements.joinToString(", ")).append(". ")
            } else {
                spokenBuilder.append(row.joinToString(" - ")).append(". ")
            }
        }

        return spokenBuilder.toString().trim()
    }

    private fun parseRow(line: String): List<String> {
        return if (line.contains("\t")) {
            line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
        } else if (line.contains("|")) {
            line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            line.split(Regex("""\s{2,}""")).map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
