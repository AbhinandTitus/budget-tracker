package app.kanakku.core.sms

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

/** Reads credit-card statement and payment-reminder messages into an amount and a due date. */
object DueParser {

    private const val CUR = """(?:(?<![a-z])rs\.?|(?<![a-z])inr|₹)"""
    private const val NUM = """(\d[\d,]*(?:\.\d{1,2})?)"""

    private val trigger = Regex("""(?i)\b(?:due|outstanding|statement|stmt|e-?stmt|payable)\b""")
    private val cardWord = Regex("""(?i)\bcard\b|cardmember|card\s*member""")
    private val settled = Regex("""(?i)(?<!not\s)(?<!if\s)\b(?:received|credited|debited|spent|refunded|reversed)\b|thank\s+you\s+for""")

    private val total = Regex(
        """(?i)(?:total\s*(?:amt|amount)?\.?\s*(?:due|outstanding|o/s|payable)|tot\.?\s*(?:amt\.?)?\s*due|total\s+due|""" +
            """(?<!min\s)(?<!min\.\s)(?<!minimum\s)(?:amount|amt)\.?\s+due|outstanding(?:\s+(?:amt|amount|balance|of))?|""" +
            """(?:stmt|statement)\s*(?:amt|amount|balance)|bill\s*(?:amt|amount)|payment\s+of|dues?\s+of)""" +
            """\s*(?:is|of|:|-)?\s*$CUR\s*:?\s*$NUM""",
    )
    private val minimum = Regex(
        """(?i)min(?:imum)?\.?\s*(?:amt|amount)?\.?\s*(?:due|payable)?\s*(?:is|of|:|-)?\s*$CUR\s*:?\s*$NUM""",
    )
    private val dueDate = Regex(
        """(?i)(?:payable\s+by|pay\s+by|due\s+(?:date|on|by|dt)|is\s+due|payment\s+due\s+date|before|by)\s*(?:is|on|:|-)?\s*""" +
            """(\d{1,2}(?:st|nd|rd|th)?[-/ .]?(?:[A-Za-z]{3,9}|\d{1,2})[-/ .,']*\d{2,4})""",
    )
    private val last4 = Regex("""(?i)\bcard\s*(?:no\.?|number|a/c|account)?\s*(?:end(?:ing|s)\s*(?:with|in)?)?\s*[:\-]?\s*(?:[xX*.#]+[\s-]*)*(\d{4,5})(?!\d)""")

    private val formats: List<DateTimeFormatter> = listOf(
        "d/M/uuuu", "d-M-uuuu", "d.M.uuuu", "d/M/uu", "d-M-uu", "d.M.uu",
        "d MMM uuuu", "d-MMM-uuuu", "d/MMM/uuuu", "d MMM uu", "d-MMM-uu", "d/MMM/uu", "dMMMuuuu", "dMMMuu",
        "d MMMM uuuu", "d-MMMM-uuuu", "d MMMM uu",
    ).map { DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(it).toFormatter(Locale.ENGLISH) }

    internal fun parseDate(raw: String): LocalDate? {
        val s = raw.trim()
            .replace(Regex("""(?i)(\d)(?:st|nd|rd|th)\b"""), "$1")
            .replace(Regex("""[,']"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        for (f in formats) {
            try {
                return LocalDate.parse(s, f)
            } catch (_: Exception) {
            }
        }
        return null
    }

    fun parse(text: String, sender: SenderInfo, receivedAt: LocalDate): ParsedDue? {
        if (!trigger.containsMatchIn(text)) return null
        val isCard = sender.institution?.kind == Institution.Kind.CARD || cardWord.containsMatchIn(text)
        if (!isCard) return null
        if (settled.containsMatchIn(text)) return null

        val totalMinor = total.find(text)?.groupValues?.get(1)?.let(SmsParser::toMinor) ?: return null
        if (totalMinor <= 0) return null
        val date = dueDate.findAll(text).firstNotNullOfOrNull { parseDate(it.groupValues[1]) } ?: return null
        // A statement that is already long past due is noise from an old inbox.
        if (date.isBefore(receivedAt.minusDays(45)) || date.isAfter(receivedAt.plusDays(75))) return null

        return ParsedDue(
            institution = sender.institution,
            accountLast4 = last4.find(text)?.groupValues?.get(1)?.takeLast(4),
            totalMinor = totalMinor,
            minimumMinor = minimum.find(text)?.groupValues?.get(1)?.let(SmsParser::toMinor),
            dueDate = date,
        )
    }
}
