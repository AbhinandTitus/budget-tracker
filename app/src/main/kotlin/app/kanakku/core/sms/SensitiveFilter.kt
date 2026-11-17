package app.kanakku.core.sms

/**
 * Decides whether a message carries, or is about, a secret: an OTP, PIN, CVV, password or
 * similar. Such messages are dropped before any other processing and are never stored.
 *
 * Two independent checks, either of which marks the message sensitive:
 *
 *  1. Code delivery – the message hands over a code ("OTP is 482913", "482913 is your OTP").
 *     Checked on the untouched text.
 *  2. Secret vocabulary – the message talks about an OTP / PIN / CVV / password at all.
 *     Bank alerts sometimes end with advice such as "Never share your OTP or PIN with anyone";
 *     that advice is removed first so a genuine transaction alert is not mistaken for a secret.
 */
object SensitiveFilter {

    private const val SECRET_WORD =
        """otp|one[\s-]*time[\s-]*pass\w*|verification\s*code|security\s*code|pass\s*code|password|pin"""

    private val codeDelivery = listOf(
        // "OTP is 482913", "OTP: 482913", "OTP for txn of Rs 500 at X is 482913"
        Regex("""(?i)(?<![a-z])(?:$SECRET_WORD)\s*(?:is|:|-|=|for\s+[^.]{0,70}?\bis)\s*:?\s*\d{4,8}\b"""),
        // "482913 is your OTP"
        Regex("""(?i)\b\d{4,8}\s+is\s+(?:your\s+|the\s+|ur\s+)?(?:otp|one[\s-]*time|verification|security\s*code|pass\s*code|login)"""),
        // "Use 482913 as OTP", "Use OTP 482913 to …"
        Regex("""(?i)\buse\s+(?:otp\s+|code\s+)?\d{4,8}\s+(?:as|for|to)\b"""),
    )

    /** "never share your OTP", "do not disclose PIN/CVV to anyone", "don't share it" … up to the sentence end. */
    private val shareWarning = Regex(
        """(?i)\b(?:never|do\s*not|don[’']?t|dont|not\s+to)\s+""" +
            """(?:ever\s+)?(?:share|disclose|reveal|give|tell|forward|enter)\b[^.!\n]{0,80}""",
    )

    /** "Bank never asks for OTP", "SBI will never call you for your PIN" … */
    private val bankNeverAsks = Regex(
        """(?i)\b(?:bank|we|sbi|hdfc|icici|axis|kotak|rbi|npci|officials?|staff|employees?)\s+""" +
            """(?:will\s+)?(?:never|does\s*n[o’']t|do\s*n[o’']t)\s+(?:asks?|calls?|requests?|seeks?|sends?)\b[^.!\n]{0,80}""",
    )

    /** "Beware of fraudsters asking for OTP" … */
    private val beware = Regex("""(?i)\b(?:beware|be\s+aware|be\s+careful|caution|stay\s+alert)\b[^.!\n]{0,100}""")

    private val secret = Regex(
        """(?i)(?<![a-z])(?:""" +
            """otps?|o\.t\.p|""" +
            """one[\s-]*time[\s-]*(?:pass(?:word|code)?|pin|code)|""" +
            """verification\s*(?:code|pin|number)|verify\s*code|""" +
            """security\s*(?:code|pin|number)|secure\s*code|secret\s*code|""" +
            """auth(?:entication|orisation|orization)?\s*code|""" +
            """login\s*(?:code|pin|password)|access\s*code|activation\s*(?:code|key)|""" +
            """pass\s*code|password|passwd|""" +
            """cvv2?|cvc2?|""" +
            """[imtu]pin|upi[\s-]*pin|atm[\s-]*pin|green[\s-]*pin|card[\s-]*pin|debit[\s-]*pin|pin|""" +
            """vbv|3d\s*secure|net\s*banking\s*(?:id|password)|temporary\s*(?:pin|password)""" +
            """)(?![a-z])""",
    )

    fun isSensitive(text: String): Boolean {
        if (text.isEmpty()) return false
        if (codeDelivery.any { it.containsMatchIn(text) }) return true
        val stripped = text
            .replace(shareWarning, " ")
            .replace(bankNeverAsks, " ")
            .replace(beware, " ")
        return secret.containsMatchIn(stripped)
    }
}
