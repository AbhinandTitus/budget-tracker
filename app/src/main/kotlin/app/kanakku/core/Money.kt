package app.kanakku.core

import kotlin.math.abs

/** Rupee formatting with Indian digit grouping (12,34,567). Amounts are paise. */
object Money {
    fun group(rupees: Long): String {
        val s = abs(rupees).toString()
        if (s.length <= 3) return s
        val head = s.dropLast(3)
        val tail = s.takeLast(3)
        val grouped = head.reversed().chunked(2).joinToString(",").reversed()
        return "$grouped,$tail"
    }

    /** "₹1,23,456" or "₹1,286.50"; paise shown only when present. */
    fun format(minor: Long, symbol: Boolean = true, paise: Boolean = true): String {
        val a = abs(minor)
        val whole = group(a / 100)
        val frac = (a % 100).toInt()
        val body = if (paise && frac != 0) "$whole.${frac.toString().padStart(2, '0')}" else whole
        val sign = if (minor < 0) "−" else ""
        return if (symbol) "$sign₹$body" else "$sign$body"
    }

    /** Rounded to whole rupees. */
    fun whole(minor: Long, symbol: Boolean = true): String = format(Math.round(minor / 100.0) * 100, symbol, paise = false)

    /** "₹48.5K", "₹1.2L" – for chart axes and tight spaces. */
    fun compact(minor: Long): String {
        val r = abs(minor) / 100.0
        val sign = if (minor < 0) "−" else ""
        fun trim(v: Double) = if (v >= 100 || v % 1.0 < 0.05) v.toLong().toString() else String.format(java.util.Locale.ENGLISH, "%.1f", v)
        return when {
            r >= 1_00_00_000 -> "$sign₹${trim(r / 1_00_00_000)}Cr"
            r >= 1_00_000 -> "$sign₹${trim(r / 1_00_000)}L"
            r >= 1_000 -> "$sign₹${trim(r / 1_000)}K"
            else -> "$sign₹${r.toLong()}"
        }
    }

    /** Parses user-typed rupees ("1,250.5") into paise. */
    fun parse(text: String): Long? {
        val clean = text.replace(",", "").replace("₹", "").trim()
        if (clean.isEmpty()) return null
        val value = clean.toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}
