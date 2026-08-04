package com.sysadmindoc.billminder.data

import android.content.Context
import java.util.Locale

data class CsvLearnedCorrection(
    val field: CsvField,
    val confirmations: Int
)

object CsvMappingLearner {
    fun recordCorrections(
        headers: List<String>,
        baseline: Map<CsvField, Int?>,
        selected: Map<CsvField, Int?>,
        learned: Map<String, CsvLearnedCorrection>
    ): Map<String, CsvLearnedCorrection> {
        val updated = learned.toMutableMap()
        CsvField.entries.forEach { field ->
            val originalColumn = baseline[field]
            val selectedColumn = selected[field]
            if (selectedColumn == null || selectedColumn == originalColumn) return@forEach

            val header = headers.getOrNull(selectedColumn)?.let(::normalizeHeader).orEmpty()
            if (header.isBlank()) return@forEach
            val previous = updated[header]
            val confirmations = if (previous?.field == field) {
                previous.confirmations + 1
            } else {
                1
            }
            updated[header] = CsvLearnedCorrection(field, confirmations)
        }
        return updated
    }

    fun applyLearned(
        headers: List<String>,
        baseline: Map<CsvField, Int?>,
        learned: Map<String, CsvLearnedCorrection>
    ): Map<CsvField, Int?> {
        val result = baseline.toMutableMap()
        learned
            .filterValues { it.confirmations >= REQUIRED_CONFIRMATIONS }
            .forEach { (header, correction) ->
                val index = headers.indexOfFirst { normalizeHeader(it) == header }
                if (index >= 0) result[correction.field] = index
            }
        return result
    }

    internal fun normalizeHeader(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    const val REQUIRED_CONFIRMATIONS = 3
}

object CsvMappingLearning {
    private const val PREFS_NAME = "billminder_csv_mapping_learning"
    private const val KEY_PREFIX = "header:"
    private const val VALUE_SEPARATOR = "|"

    fun suggestedMapping(
        context: Context,
        headers: List<String>,
        baseline: Map<CsvField, Int?>
    ): Map<CsvField, Int?> =
        CsvMappingLearner.applyLearned(headers, baseline, load(context))

    fun learnedCount(context: Context, headers: List<String>): Int {
        val normalizedHeaders = headers.map(CsvMappingLearner::normalizeHeader).toSet()
        return load(context).count { (header, correction) ->
            header in normalizedHeaders && correction.confirmations >= CsvMappingLearner.REQUIRED_CONFIRMATIONS
        }
    }

    fun recordCorrections(
        context: Context,
        headers: List<String>,
        baseline: Map<CsvField, Int?>,
        selected: Map<CsvField, Int?>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val updated = CsvMappingLearner.recordCorrections(headers, baseline, selected, load(context))
        prefs.edit().apply {
            updated.forEach { (header, correction) ->
                putString(
                    KEY_PREFIX + header,
                    correction.field.name + VALUE_SEPARATOR + correction.confirmations
                )
            }
        }.apply()
    }

    private fun load(context: Context): Map<String, CsvLearnedCorrection> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.mapNotNull { (key, raw) ->
            if (!key.startsWith(KEY_PREFIX) || raw !is String) return@mapNotNull null
            val parts = raw.split(VALUE_SEPARATOR)
            val field = parts.getOrNull(0)?.let { name ->
                CsvField.entries.firstOrNull { it.name == name }
            } ?: return@mapNotNull null
            val confirmations = parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
                ?: return@mapNotNull null
            key.removePrefix(KEY_PREFIX) to CsvLearnedCorrection(field, confirmations)
        }.toMap()
    }
}
