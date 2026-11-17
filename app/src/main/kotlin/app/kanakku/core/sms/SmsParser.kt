package app.kanakku.core.sms

import app.kanakku.core.model.AccountKind
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.Nature
import java.time.LocalDate

/** A money movement read from one message. Amounts are in paise. */
data class ParsedTxn(
    val amountMinor: Long,
    val direction: Direction,
    val nature: Nature,
    val channel: Channel,
    val accountKind: AccountKind,
    val accountLast4: String?,
    val institution: Institution?,
    /** Merchant, payee or payer as written in the message (lightly cleaned), or a VPA. */
    val counterparty: String?,
    val vpa: String?,
    val refNo: String?,
    val balanceMinor: Long?,
    /** true = an individual, false = a merchant, null = the message does not say. */
    val personHint: Boolean?,
)

/** A card statement or payment reminder. */
data class ParsedDue(
    val institution: Institution?,
    val accountLast4: String?,
    val totalMinor: Long,
    val minimumMinor: Long?,
    val dueDate: LocalDate,
)

sealed interface SmsVerdict {
    /** Carries a secret (OTP, PIN, CVV …). Never stored. */
    data object Sensitive : SmsVerdict

    data class Ignored(val reason: String) : SmsVerdict
    data class Transaction(val txn: ParsedTxn) : SmsVerdict
    data class Due(val due: ParsedDue) : SmsVerdict
}

/**
 * Reads Indian bank, card and wallet alerts.
 *
 * The parser is structural rather than template-per-bank: it looks for a completed movement
 * verb, a currency amount that is not a balance, and an account reference, then extracts the
 * counterparty with an ordered set of patterns. Anything that is a secret, a promotion, a
 * request, a reminder or a failed attempt is rejected before it gets that far.
 */
object SmsParser {

    private const val CUR = """(?:(?<![a-z])rs\.?|(?<![a-z])inr|₹)"""
    private const val NUM = """\d[\d,]*(?:\.\d{1,2})?"""

    private val quickMoney = Regex("""(?i)(?:(?<![a-z])rs\b|(?<![a-z])rs\.|inr|₹|debited|credited)""")

    /** Cheap test used before any real work: could this body be a money alert at all? */
    fun mightBeFinancial(body: String): Boolean =
        body.length >= 20 && body.any { it.isDigit() } && quickMoney.containsMatchIn(body)

    // ---- movement verbs ------------------------------------------------------------------

    private val outVerb = Regex(
        """(?i)(?<![a-z])(?:""" +
            """(?:debited|spent|paid|sent|withdrawn|deducted|charged|purchase[ds]?|transferred)(?![a-z])|""" +
            """dr\.?(?![a-z])|has\s+a?\s*debit(?![a-z])|debit\s+(?:of|for)(?![a-z])|trf\s+to(?![a-z])|""" +
            """money\s+transfer(?![a-z])|purchase\s+worth(?![a-z])|used\s+(?:for|at)(?![a-z])|""" +
            """(?:txn|trxn|transaction|trans)\.?\s+(?:(?:number|no\.?|id|ref)\s+\w+\s+)?(?:of\s+|for\s+|worth\s+)?(?=(?:rs\.?|inr|₹)\s*\d)""" +
            """)""",
    )

    private val inVerb = Regex(
        """(?i)(?<![a-z])(?:credited|received|deposited|refunded|reversed|cr\.?(?![a-z])|""" +
            """has\s+a?\s*credit|credit\s+(?:of|for)|cash\s+deposit|added\s+to)(?![a-z])""",
    )

    /** "will be debited", "to be credited" … – a promise, not a movement. */
    private val futureVerb = Regex(
        """(?i)\b(?:will|would|shall|to|may|can|could)\s+(?:not\s+)?(?:be\s+|get\s+)(?:auto[\s-]?)?""" +
            """(?:debited|deducted|credited|charged|processed|presented|refunded|reversed|paid|collected|recovered|levied|sent|received)\b""",
    )

    private val pending = Regex(
        """(?i)\b(?:is\s+due|are\s+due|due\s+(?:on|by|date|for|dt)|payment\s+due|overdue|reminder|""" +
            """has\s+requested|requested\s+(?:money|payment|rs|inr|₹)|request(?:ed)?\s+(?:of|for)\s+(?:rs|inr|₹)|""" +
            """collect\s+request|if\s+(?:already\s+)?paid|ignore\s+if|min(?:imum)?\.?\s+(?:amt|amount)\.?\s+due|""" +
            """total\s+(?:amt|amount)\.?\s+due|bill\s+(?:generated|is\s+ready|of\s+(?:rs|inr|₹)))\b""",
    )

    private val failed = Regex(
        """(?i)\b(?:declined|failed|failure|unsuccessful|not\s+successful|rejected|cancelled|canceled|""" +
            """(?:could\s*n[o’']t|could\s+not|was\s+not|not\s+been|unable\s+to)\s+(?:be\s+)?(?:process(?:ed)?|complete[d]?|successful)|""" +
            """insufficient\s+(?:funds?|balance|bal|limit)|timed?\s*out|is\s+pending|pending\s+(?:for|with)|on\s+hold|""" +
            """being\s+processed|under\s+process)\b""",
    )
    private val cancelWord = Regex("""(?i)\bcancel""")

    private val strongPromo = Regex(
        """(?i)\b(?:pre-?approved|pre-?qualified|eligible|apply\s+now|congrat\w*|instant\s+loan|""" +
            """loan\s+(?:of|up\s*to|upto|offer|amount\s+of)|limited\s+period|offer\s+valid|t\s*&\s*c|tnc)\b|""" +
            """(?i)\b(?:get|avail|earn|win|grab|claim|enjoy)\b[^.]{0,40}?(?:rs\.?|inr|₹)\s*\d|""" +
            """(?i)\b(?:up\s*to|upto|flat)\s+(?:rs\.?|inr|₹)\s*\d""",
    )

    private val refundWord = Regex("""(?i)\b(?:refund(?:ed)?|reversal|reversed|credited\s+back|charge\s*back|returned)\b""")
    private val cashbackWord = Regex("""(?i)\b(?:cash\s*back|rewards?\s+(?:redemption|redeemed|credit)|incentive)\b""")
    private val interestWord = Regex("""(?i)\b(?:interest|int\.?\s*(?:cr|pd|paid|credit)|sb\s*int|sbint)\b""")
    private val salaryWord = Regex("""(?i)\b(?:salary|sal|payroll|stipend|wages)\b""")
    private val feeWord = Regex(
        """(?i)\b(?:(?:annual|joining|renewal|late|processing|convenience|service|sms|maintenance|membership|amc|over\s*limit)\s*""" +
            """(?:fee|fees|charge|charges)|charges?\s+(?:of|for|towards)|finance\s+charges?|penalty|penal\s+charges?|""" +
            """gst\s+on|min(?:imum)?\.?\s*bal(?:ance)?\.?\s*(?:charge|fee)s?|amb\s+charges?)\b""",
    )
    private val emiWord = Regex("""(?i)\b(?:emi|loan\s+(?:a/c|account|repayment|instal+ment|emi)|instal+ments?)\b""")

    private val cardPaymentIn = Regex(
        """(?i)\b(?:payment|pymt)\b.{0,80}?\b(?:received|credited)\b|received\s+(?:a\s+|your\s+)?payment|""" +
            """thank\s+you\s+for\s+(?:the\s+|your\s+)?payment|bill\s+payment""",
    )
    private val cardPayee = Regex(
        """(?i)(?:sbi\s*cards?|sbicard|credit\s*card|creditcard|\bcc\s*(?:bill|payment|pymt)|card\s*(?:bill|payment|pymt)|""" +
            """\bcred\b|cred\.?club|cc\.payment|cardpayment|\bamex\b|american\s*express|\bonecard\b|""" +
            """(?:hdfc|icici|axis|kotak|rbl|idfc|indusind|yes|bob|au)\s*(?:bank\s*)?cards?\b|billdesk.{0,12}card)""",
    )

    // ---- amounts -------------------------------------------------------------------------

    private val amountRx = Regex("""(?i)$CUR\s*:?\s*($NUM)""")
    private val bareAmountRx = Regex("""(?i)\b(?:debited|credited)\s+(?:by|with|for)\s+($NUM)(?![\d,])""")
    private val balanceLead = Regex(
        """(?i)(?:bal(?:ance)?|lmt|limit|outstanding|o/s|avl|avbl|avlbl|avail(?:able)?)\.?[^a-z0-9]{0,3}""" +
            """(?:(?:is|of|now|amt|amount|cr|credit)\b\.?[^a-z0-9]{0,3}){0,2}(?:paytm\s+wallet|wallet)?[^a-z0-9]{0,3}$""",
    )

    private data class Amount(val minor: Long, val index: Int, val isBalance: Boolean)

    internal fun toMinor(number: String): Long? {
        val clean = number.replace(",", "").trimEnd('.')
        if (clean.isEmpty()) return null
        val parts = clean.split('.')
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = when {
            parts.size < 2 || parts[1].isEmpty() -> 0L
            parts[1].length == 1 -> parts[1].toLong() * 10
            else -> parts[1].take(2).toLong()
        }
        return rupees * 100 + paise
    }

    private fun findAmounts(text: String): List<Amount> = amountRx.findAll(text).mapNotNull { m ->
        val minor = toMinor(m.groupValues[1]) ?: return@mapNotNull null
        val lead = text.substring(maxOf(0, m.range.first - 34), m.range.first)
        Amount(minor, m.range.first, balanceLead.containsMatchIn(lead))
    }.toList()

    // ---- accounts ------------------------------------------------------------------------

    private const val MASKED = """((?:[xX*.#]+[\s-]*)*\d{3,6})(?!\d)"""
    private val cardRx = Regex(
        """(?i)\bcard\s*(?:no\.?|number|num|a/c|account)?\s*(?:end(?:ing|s)\s*(?:with|in)?)?\s*[:\-]?\s*$MASKED""",
    )
    private val acctRx = Regex(
        """(?i)(?<![a-z])(?:a/c|acct?|account|ac)(?![a-wy-z])\.?\s*(?:no\.?|number|num)?\s*[:\-]?\s*$MASKED""",
    )
    private val longAcctRx = Regex(
        """(?i)(?<![a-z])(?:a/c|acct?|account)(?![a-z])\.?\s*(?:no\.?|number|num)?\s*[:\-]?\s*\d{5,14}(\d{4})(?!\d)""",
    )
    private val bareMaskRx = Regex("""(?<![A-Za-z0-9])[xX*]{1,}\d{3,6}(?!\d)""")
    private val walletRx = Regex("""(?i)\b(?:paytm\s+wallet|wallet|amazon\s+pay\s+balance|apay\s+balance|upi\s*lite)\b""")
    private val accountWord = Regex("""(?i)(?<![a-z])(?:a/c|acct?|account|card|upi|wallet)(?![a-z])""")

    private fun digitsOf(masked: String): String = masked.filter { it.isDigit() }.takeLast(4)

    // ---- references, channels ------------------------------------------------------------

    private val refRx = Regex(
        """(?i)(?:upi\s*ref(?:erence)?\s*(?:no|number|id)?|ref(?:erence)?\.?\s*(?:no|number|id)?|refno|rrn|utr|""" +
            """txn\s*(?:id|no|number|#)|transaction\s+(?:id|number|reference\s+number)|upi)\.?\s*(?:is)?\s*[:#\-]?\s*([A-Z]{0,6}\d{6,20})\b""",
    )
    private val atmRx = Regex("""(?i)\batm\b|cash\s+withdraw|withdrawn\s+(?:at|from)|cardless""")
    private val upiRx = Regex("""(?i)\bupi\b|\bvpa\b|[a-z0-9._-]{2,}@[a-z]{2,}""")
    private val bankXferRx = Regex("""(?i)\b(?:neft|imps|rtgs|fund\s+transfer|net\s*banking|inb|mob(?:ile)?\s*b(?:an)?k(?:ing)?)\b""")
    private val autopayRx = Regex("""(?i)\b(?:e-?mandate|mandate|autopay|auto[\s-]?debit|nach|ecs|ach|standing\s+instruction)\b""")
    private val chequeRx = Regex("""(?i)\b(?:cheque|chq|clg|clearing)\b""")

    // ---- counterparty --------------------------------------------------------------------

    /** Everything after one of these is help-line boilerplate, not part of the transaction. */
    private val tailMarker = Regex(
        """(?i)(?:\bnot\s+(?:you|u|done|yours)\b|\bif\s+not\b|\bif\s+this\b|\bin\s+case\b|\bcall\s+(?:us|\d)|\bsms\s+[A-Z]|\bdial\s+\d|""" +
            """\bforward\b|\bhelpline\b|\breport\s+(?:at|to|on|it)\b|\bto\s+block\b|\bblock\s+(?:upi|card|cc)\b|""" +
            """\bt(?:r?x)n\.?\s+not\b|\bnot\s+done\b|\bdispute\b|\bsmall\s+txns\b|\bdownload\b|\bknow\s+more\b|\bfor\s+dispute\b)""",
    )
    private val vpaRx = Regex("""(?i)([a-z0-9][a-z0-9._-]{1,48}@[a-z][a-z0-9]{1,24})""")
    private val upiPath = Regex("""(?i)\bUPI/(P2[AMP])/\d+/([^/]+?)(?:/|\s*$|\s+-|\s+bal|\s+avl)""")
    private val infoRx = Regex("""(?i)\binfo\s*[:\-]?\s*(.+?)(?=\.\s|\.$|\s+avl\b|\s+bal\b|\s+available\b|$)""")
    private val neftRx = Regex("""(?i)\b(?:NEFT|IMPS|RTGS)(?:\s*(?:Cr|Dr))?[-/\s:]+(?:[A-Z]{4}0[A-Z0-9]{6}[-/\s]+|\d{6,}[-/\s]+)+(.+?)(?:[-/]|\.\s|\.$|\s+avl\b|\s+bal\b|$)""")

    private const val END = """(?=\s+on\b|\s+at\b|\s+from\b|\s+via\b|\s+thru\b|\s+through\b|\s+using\b|\s+ref\b|\s+refno\b|\s+upi\b|\s+txn\b|\s+dt\b|\s+dated\b|\s+is\b|\s+has\b|\s+was\b|\s+avl\b|\s+bal\b|\s+available\b|\s+total\b|\s*\(|\s*,|\s*;|\.\s|\.$|\s+-|$)"""

    private val outPatterns = listOf(
        Regex("""(?i)\btrf\s+to\s+(.+?)\s+ref"""),
        Regex("""(?i)\bto\s+vpa\s+(\S+?@\S+?)(?=[\s,;(]|\.\s|\.$|\.ref|$)"""),
        Regex("""(?i);\s*(.+?)\s+credited\b"""),
        Regex("""(?i)\bcredited\s+to\s+(?!(?:your|a/c|ac\b|acct|account|the|beneficiary))(.+?)$END"""),
        Regex("""(?i)\bon\s+\d{1,2}[-/ ][a-z]{3}[-/ ]\d{2,4}\s+(?:on|at)\s+(?!\d{1,2}:\d{2})(.+?)(?=\.\s|\.$|\s+avl\b|\s+avbl\b|$)"""),
        Regex("""(?i)\d{1,2}[-/]\d{1,2}[-/]\d{2,4},?\s+\d{1,2}:\d{2}(?::\d{2})?(?:\s*IST)?\s+(?!UPI/)([A-Za-z].+?)\s+(?:avl|avbl|available)\b"""),
        Regex("""(?i)\bat\s+(?!\d{1,2}:\d{2})(.+?)(?=\s+on\b|\s+via\b|\s+thru\b|\s+through\b|\s+using\b|\s+by\b|\s+txn\b|\s+ref\b|\s+dated\b|\s+dt\b|\s+avl\b|\s+avbl\b|\s+bal\b|\s*\(|\.\s|\.$|$)"""),
        Regex("""(?i)\btowards\s+(?!(?:your|the)\s+(?:a/c|account|card|credit|loan))(.+?)$END"""),
        Regex("""(?i)\bto\s+(?!(?:your|the|a/c|ac\b|acct|account|beneficiary|vpa|block|report|dispute|avoid|know|view|check)\b)(.+?)$END"""),
        Regex("""(?i)\bfor\s+(?:upi[\s-]?mandate|mandate|autopay|payment\s+to)\s+(.+?)$END"""),
    )

    private val inPatterns = listOf(
        Regex("""(?i)\bfrom\s+vpa\s+(\S+?@\S+?)(?=[\s,;(]|\.\s|\.$|$)"""),
        Regex("""(?i)\btransfer\s+from\s+(.+?)\s+ref"""),
        Regex("""(?i)\bdeposit\s+by\s+transfer\s+from\s+(.+?)(?=\.\s|\.$|\s+avl\b|\s+bal\b|$)"""),
        Regex("""(?i)\brefund\s+(?:of\s+\S+\s+)?from\s+(.+?)$END"""),
        Regex("""(?i)\bfrom\s+(?!(?:your|the|a/c|ac\b|acct|account|vpa)\b)(.+?)$END"""),
        Regex("""(?i)\bby\s+(?!(?:(?:rs|inr)\.?\s*\d|₹|neft\b|imps\b|rtgs\b|upi\b|transfer\b|cash\b|cheque\b|chq\b|clearing\b|salary\b|mob\b|\d|\())(.+?)$END"""),
        Regex("""(?i)\bat\s+(?!\d{1,2}:\d{2})(.+?)(?=\s+on\b|\s+via\b|\s+has\b|\s+is\b|\s*\(|\.\s|\.$|$)"""),
    )

    private val honorific = Regex("""(?i)^(?:mr|mrs|ms|miss|dr|shri|smt|sri|kum|kumari|m/s|messrs)\.?\s+""")
    private val personalTitle = Regex("""(?i)^(?:mr|mrs|ms|miss|shri|smt|sri|kum|kumari)\.?\s+""")
    private val infoNoise = setOf(
        "UPI", "IMPS", "NEFT", "RTGS", "ACH", "NACH", "ECS", "POS", "ATM", "ECOM", "INB", "MB", "TPT", "FT", "BIL", "ONL",
        "D", "C", "DR", "CR", "P2A", "P2M", "P2P", "TO", "BY", "BILLPAY", "PAYMENT", "TRANSFER", "MMT", "IB", "SI", "MANDATE",
    )
    private val ifsc = Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""")

    private fun plausibleName(raw: String?): String? {
        var s = raw?.trim() ?: return null
        s = s.trim('.', ',', ';', ':', '-', '*', '/', '(', ')', ' ', '"', '\'')
        s = s.replace(Regex("""\s+"""), " ")
        if (s.length < 2 || s.length > 60) return null
        if (s.none { it.isLetter() }) return null
        if (Regex("""(?i)^(?:a/c|ac|acct|account|your|the|you|vpa|upi|imps|neft|rtgs|bank|beneficiary|self)\b""").containsMatchIn(s) && s.count { it == ' ' } < 1) return null
        if (Regex("""(?i)^(?:a/c|acct|account)\b""").containsMatchIn(s)) return null
        if (Regex("""(?i)^[xX*]+\d{2,}$""").matches(s)) return null
        return s
    }

    private fun nameFromInfo(payload: String): String? {
        val tokens = payload.split('-', '/', '*', '|', ':').map { it.trim() }.filter { it.isNotEmpty() }
        return tokens.firstOrNull { t ->
            val up = t.uppercase()
            up !in infoNoise && !ifsc.matches(up) && t.count { it.isLetter() } >= 3 &&
                t.count { it.isDigit() } <= t.length / 2 && !t.contains('@')
        }?.let(::plausibleName)
    }

    private data class Party(val name: String?, val vpa: String?, val person: Boolean?)

    private fun findParty(text: String, direction: Direction): Party {
        val head = tailMarker.find(text)?.let { text.substring(0, it.range.first) } ?: text

        upiPath.find(head)?.let { m ->
            val person = when (m.groupValues[1].uppercase()) {
                "P2A", "P2P" -> true
                "P2M" -> false
                else -> null
            }
            plausibleName(m.groupValues[2])?.let { return Party(it, null, person) }
        }

        val patterns = if (direction == Direction.OUT) outPatterns else inPatterns
        for (rx in patterns) {
            val m = rx.find(head) ?: continue
            val captured = m.groupValues[1].trim()
            val vpa = vpaRx.find(captured)?.value
            if (vpa != null) return Party(vpa, vpa.lowercase(), null)
            val person = if (personalTitle.containsMatchIn(captured)) true else null
            val name = plausibleName(honorific.replace(captured, "")) ?: continue
            return Party(name, null, person)
        }

        neftRx.find(head)?.let { m -> plausibleName(m.groupValues[1])?.let { return Party(it, null, null) } }
        infoRx.find(head)?.let { m ->
            val payload = m.groupValues[1]
            val vpa = vpaRx.find(payload)?.value?.lowercase()
            nameFromInfo(payload)?.let { return Party(it, vpa, null) }
            if (vpa != null) return Party(vpa, vpa, null)
        }
        vpaRx.find(head)?.let { return Party(it.value, it.value.lowercase(), null) }
        return Party(null, null, null)
    }

    private val signature = Regex("""[-–]\s*([A-Za-z][A-Za-z&.' ]{2,40}?(?:Bank|BANK|bank|Society|SCB|Co-?op\w*))\.?\s*$""")

    // ---- entry points --------------------------------------------------------------------

    fun normalise(body: String): String =
        body.replace(' ', ' ').replace(Regex("""[\r\n\t]+"""), " ").replace(Regex(""" {2,}"""), " ").trim()

    fun parse(address: String, body: String, receivedAt: LocalDate = LocalDate.now()): SmsVerdict {
        val text = normalise(body)
        if (SensitiveFilter.isSensitive(text)) return SmsVerdict.Sensitive

        val sender = Senders.parse(address)
        if (sender.isPersonalNumber) return SmsVerdict.Ignored("personal number")
        if (sender.isNumericHeader) return SmsVerdict.Ignored("numeric header")
        if (sender.isPromotional) return SmsVerdict.Ignored("promotional header")
        if (!mightBeFinancial(text)) return SmsVerdict.Ignored("no money")

        DueParser.parse(text, sender, receivedAt)?.let { return SmsVerdict.Due(it) }
        return parseTransaction(text, sender)
    }

    private fun parseTransaction(text: String, sender: SenderInfo): SmsVerdict {
        val present = futureVerb.replace(text) { " ".repeat(it.value.length) }
        val outHit = outVerb.find(present)
        val inHit = inVerb.find(present)
        if (outHit == null && inHit == null) return SmsVerdict.Ignored("no completed movement")
        if (pending.containsMatchIn(text)) return SmsVerdict.Ignored("request or reminder")

        val refundish = refundWord.containsMatchIn(text)
        var direction = when {
            outHit == null -> Direction.IN
            inHit == null -> Direction.OUT
            refundish -> Direction.IN
            outHit.range.first <= inHit.range.first -> Direction.OUT
            else -> Direction.IN
        }
        val creditForCancellation = direction == Direction.IN && (refundish || cancelWord.containsMatchIn(text))
        if (failed.containsMatchIn(text) && !creditForCancellation) {
            return SmsVerdict.Ignored("failed or declined")
        }

        val amounts = findAmounts(text)
        val amount = amounts.firstOrNull { !it.isBalance }
            ?: bareAmountRx.find(text)?.let { m -> toMinor(m.groupValues[1])?.let { Amount(it, m.range.first, false) } }
            ?: return SmsVerdict.Ignored("no amount")
        if (amount.minor <= 0) return SmsVerdict.Ignored("zero amount")
        val balance = amounts.firstOrNull { it.isBalance }?.minor

        // Account or card.
        val cardHit = cardRx.find(text)
        val acctHit = acctRx.find(text)
        val isCardIssuer = sender.institution?.kind == Institution.Kind.CARD
        val saysCredit = Regex("""(?i)credit\s*card""").containsMatchIn(text)
        val saysDebitCard = Regex("""(?i)(?:debit|atm|prepaid|forex)\s*(?:cum\s*atm\s*)?card""").containsMatchIn(text)
        val head = tailMarker.find(text)?.let { text.substring(0, it.range.first) } ?: text
        val wallet = walletRx.containsMatchIn(head) && acctHit == null && cardHit == null

        val accountKind: AccountKind
        val last4: String?
        when {
            cardHit != null && (saysCredit || isCardIssuer || (!saysDebitCard && acctHit == null)) -> {
                accountKind = AccountKind.CREDIT_CARD; last4 = digitsOf(cardHit.groupValues[1])
            }
            acctHit != null -> { accountKind = AccountKind.BANK; last4 = digitsOf(acctHit.groupValues[1]) }
            cardHit != null -> { accountKind = AccountKind.DEBIT_CARD; last4 = digitsOf(cardHit.groupValues[1]) }
            longAcctRx.containsMatchIn(text) -> {
                accountKind = AccountKind.BANK; last4 = longAcctRx.find(text)!!.groupValues[1]
            }
            !wallet && bareMaskRx.containsMatchIn(text) -> {
                accountKind = if (isCardIssuer || saysCredit) AccountKind.CREDIT_CARD else AccountKind.BANK
                last4 = digitsOf(bareMaskRx.find(text)!!.value)
            }
            wallet -> { accountKind = AccountKind.WALLET; last4 = null }
            isCardIssuer || saysCredit -> { accountKind = AccountKind.CREDIT_CARD; last4 = null }
            sender.institution?.kind == Institution.Kind.BANK -> { accountKind = AccountKind.BANK; last4 = null }
            else -> { accountKind = AccountKind.UNKNOWN; last4 = null }
        }

        val ref = refRx.find(text)?.groupValues?.get(1)
        val trusted = sender.institution?.kind == Institution.Kind.BANK || isCardIssuer
        val hasAccount = last4 != null
        val structured = hasAccount ||
            (trusted && (ref != null || accountWord.containsMatchIn(text))) ||
            (accountKind == AccountKind.WALLET)
        if (!structured) return SmsVerdict.Ignored("not an account alert")
        if (strongPromo.containsMatchIn(text) && ref == null) return SmsVerdict.Ignored("promotion")

        val party = findParty(text, direction)

        val channel = when {
            direction == Direction.OUT && atmRx.containsMatchIn(text) && !Regex("""(?i)\bpos\b|purchase""").containsMatchIn(text) -> Channel.ATM
            accountKind == AccountKind.CREDIT_CARD -> Channel.CARD
            upiRx.containsMatchIn(text) -> Channel.UPI
            accountKind == AccountKind.DEBIT_CARD || (cardHit != null && saysDebitCard) -> Channel.CARD
            autopayRx.containsMatchIn(text) -> Channel.AUTOPAY
            bankXferRx.containsMatchIn(text) -> Channel.BANK_TRANSFER
            chequeRx.containsMatchIn(text) -> Channel.CHEQUE
            accountKind == AccountKind.WALLET -> Channel.WALLET
            else -> Channel.OTHER
        }

        val partyText = party.name.orEmpty()
        val nature = when {
            direction == Direction.IN && accountKind == AccountKind.CREDIT_CARD && cardPaymentIn.containsMatchIn(text) && !refundish ->
                Nature.CARD_BILL_PAYMENT
            direction == Direction.OUT && accountKind != AccountKind.CREDIT_CARD &&
                (cardPayee.containsMatchIn(partyText) || Regex("""(?i)towards\s+(?:your\s+)?[^.]{0,30}credit\s*card""").containsMatchIn(text)) ->
                Nature.CARD_BILL_PAYMENT
            channel == Channel.ATM -> Nature.ATM_WITHDRAWAL
            direction == Direction.IN && refundish -> Nature.REFUND
            direction == Direction.IN && cashbackWord.containsMatchIn(text) -> Nature.CASHBACK
            direction == Direction.IN && accountKind == AccountKind.CREDIT_CARD -> Nature.REFUND
            direction == Direction.IN && interestWord.containsMatchIn(text) -> Nature.INTEREST
            direction == Direction.IN && salaryWord.containsMatchIn(text) -> Nature.SALARY
            direction == Direction.OUT && feeWord.containsMatchIn(text) -> Nature.FEE
            direction == Direction.OUT && emiWord.containsMatchIn(text) -> Nature.EMI
            else -> Nature.GENERIC
        }

        val institution = sender.institution ?: signature.find(text)?.groupValues?.get(1)?.trim()?.let { name ->
            Institution(name.lowercase().filter { it.isLetterOrDigit() }, name, Institution.Kind.BANK)
        }

        return SmsVerdict.Transaction(
            ParsedTxn(
                amountMinor = amount.minor,
                direction = direction,
                nature = nature,
                channel = channel,
                accountKind = accountKind,
                accountLast4 = last4,
                institution = institution,
                counterparty = party.name,
                vpa = party.vpa,
                refNo = ref,
                balanceMinor = balance,
                personHint = party.person,
            ),
        )
    }
}
