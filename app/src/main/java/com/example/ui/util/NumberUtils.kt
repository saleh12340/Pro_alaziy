package com.example.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object NumberUtils {
    val englishSymbols: DecimalFormatSymbols = DecimalFormatSymbols(Locale.US)
    val standardFormatter: DecimalFormat = DecimalFormat("#,##0.##", englishSymbols)
    val currencyFormatter: DecimalFormat = DecimalFormat("#,##0.00", englishSymbols)
    val integerFormatter: DecimalFormat = DecimalFormat("#,##0", englishSymbols)
}

/**
 * Converts Eastern Arabic numerals (٠-٩) and Persian numerals (۰-۹) to standard Western English digits (0-9).
 * Also normalizes Arabic decimal separator (٫) to a standard dot (.).
 */
fun String.toEnglishDigits(): String {
    if (this.isEmpty()) return this
    val builder = StringBuilder(this.length)
    for (ch in this) {
        when (ch) {
            in '٠'..'٩' -> builder.append((ch - '٠' + '0'.code).toChar())
            in '۰'..'۹' -> builder.append((ch - '۰' + '0'.code).toChar())
            '٫' -> builder.append('.') // Standard Arabic decimal point U+066B
            else -> builder.append(ch)
        }
    }
    return builder.toString()
}

/**
 * Safely parses any formatted numeric string to Double.
 * Strips thousands separators (English comma, Arabic comma/thousands separator, and spaces).
 * Never fails or returns 0.0 due to formatting commas like "1,000" or "4,500".
 */
fun String.toCleanDouble(): Double {
    if (this.isBlank()) return 0.0
    val normalized = this.toEnglishDigits()
        .replace(",", "")
        .replace("٬", "")
        .replace("،", "")
        .replace(" ", "")
        .trim()
    return normalized.toDoubleOrNull() ?: 0.0
}

/**
 * Formats a Double to a clean numeric string for editing and storage (NO thousands commas).
 * e.g., 1000.0 -> "1000", 1250.5 -> "1250.5", 0.0 -> ""
 */
fun formatCleanNumber(d: Double): String {
    if (d == 0.0) return ""
    return if (d == d.toLong().toDouble()) {
        d.toLong().toString()
    } else {
        val symbols = DecimalFormatSymbols(Locale.US)
        DecimalFormat("0.##", symbols).format(d)
    }
}

fun Double.formatEnglish(pattern: String = "#,##0.##"): String {
    val symbols = DecimalFormatSymbols(Locale.US)
    return DecimalFormat(pattern, symbols).format(this)
}

fun Number.toEnglishString(): String {
    return this.toString().toEnglishDigits()
}

fun formatQuantity(s: String): String {
    if (s.isBlank()) return ""
    val clean = s.toCleanDouble()
    if (clean == 0.0) {
        val raw = s.toEnglishDigits().trim()
        return if (raw == "0") "0" else s
    }
    return if (clean == clean.toLong().toDouble()) {
        clean.toLong().toString()
    } else {
        clean.toString()
    }
}
