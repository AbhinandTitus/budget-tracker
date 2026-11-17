package app.kanakku.core.category

/** A counterparty reduced to something stable enough to attach rules to and tidy enough to show. */
data class MerchantInfo(
    /** Stable identity used for learned rules: a cleaned upper-case name, or a lower-case UPI id. */
    val key: String?,
    /** Text the dictionaries are matched against. */
    val search: String,
    /** [search] with spaces removed, for UPI ids where words run together. */
    val compact: String,
    val pretty: String?,
    val isVpa: Boolean,
    val isPhoneVpa: Boolean,
)

object Merchants {

    private val gatewayPrefix = Regex(
        """^(?:UPI|POS|ECOM|RAZ|RAZORPAY|PAY|PAYU|PYU|CCA|CCAVENUE|BD|BILLDESK|IND|EBS|MSW|PTM|PAYTM|PP|PHONEPE|TPSL|""" +
            """CASHFREE|JUSPAY|SQ|EZETAP|PINELABS|BHARATPE|ATOM|OPC|GPAY)\s*[*/]+\s*|^(?:WWW|HTTPS?)[\s./:]+""",
    )
    private val companySuffix = Regex(
        """\s+(?:PVT\.?\s*LTD\.?|PRIVATE\s+LIMITED|PRIVATE\s+LIMITE?|PRIVATE\s+LIM?I?|PRIVA(?:TE)?|PVT|P\s+LTD|LIMITED|LTD|LLP|INC)$""",
    )
    private val domainSuffix = Regex("""\s+(?:COM|CO IN|IN|NET|ORG)$""")
    private val places = setOf(
        "KOCHI", "COCHIN", "ERNAKULAM", "EKM", "TRIVANDRUM", "THIRUVANANTHAPURAM", "TVM", "KOZHIKODE", "CALICUT", "THRISSUR",
        "TRICHUR", "KOLLAM", "KANNUR", "KOTTAYAM", "ALAPPUZHA", "ALLEPPEY", "PALAKKAD", "MALAPPURAM", "KASARAGOD",
        "PATHANAMTHITTA", "IDUKKI", "WAYANAD", "KAKKANAD", "EDAPPALLY", "ALUVA", "ANGAMALY", "KERALA", "KL",
        "BANGALORE", "BENGALURU", "MUMBAI", "CHENNAI", "HYDERABAD", "DELHI", "GURGAON", "GURUGRAM", "NOIDA", "PUNE",
        "KOLKATA", "COIMBATORE", "MANGALORE", "MYSORE", "IND", "INDIA", "KA", "TN", "MH", "DL", "HR",
    )
    private val acronyms = setOf(
        "KSEB", "KSRTC", "KWA", "BSNL", "IRCTC", "LIC", "HP", "BPCL", "IOCL", "HPCL", "KSFE", "KMRL", "PVR", "KFC", "CCD", "ATM",
        "UPI", "SBI", "HDFC", "ICICI", "DDRC", "KIMS", "MIMS", "IOC", "BPC", "RTO", "MVD", "EMI", "NPS", "PPF", "MF", "SIP", "LPG",
        "DTH", "TV", "IT", "EV", "QR", "PG", "A2B", "QRS", "KTDC", "DTPC", "CIAL", "KTU", "CUSAT", "IGNOU", "NPTEL", "OET",
        "IELTS", "NEFT", "IMPS", "RTGS", "ACH", "NACH", "BBPS", "USA", "UAE", "TCS", "UST", "IBS", "H&M", "IKEA", "ACT", "VI",
    )
    private val phoneVpa = Regex("""^(?:\+?91)?[6-9]\d{9}(?:[-.]\d{1,2})?$""")

    fun describe(raw: String?, vpa: String?): MerchantInfo {
        val id = (vpa ?: raw?.takeIf { it.contains('@') })?.lowercase()?.trim()
        if (id != null) {
            val user = id.substringBefore('@')
            val words = user.uppercase().replace(Regex("""[^A-Z0-9]+"""), " ").trim()
            val phone = phoneVpa.matches(user)
            return MerchantInfo(
                key = id,
                search = words,
                compact = words.replace(" ", ""),
                pretty = prettyVpa(user, id, phone),
                isVpa = true,
                isPhoneVpa = phone,
            )
        }
        val key = keyOf(raw)
        return MerchantInfo(key, key.orEmpty(), key.orEmpty().replace(" ", ""), key?.let(::titleCase), isVpa = false, isPhoneVpa = false)
    }

    fun keyOf(raw: String?): String? {
        var s = raw?.uppercase()?.trim() ?: return null
        s = gatewayPrefix.replace(s, "")
        s = s.replace("&", " AND ").replace(Regex("""[^A-Z0-9]+"""), " ").trim().replace(Regex("""\s+"""), " ")
        repeat(2) { s = companySuffix.replace(s, "") }
        s = domainSuffix.replace(s, "")
        val tokens = s.split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (tokens.size > 1 && tokens.last() in places) tokens.removeAt(tokens.size - 1)
        s = tokens.joinToString(" ")
        return s.takeIf { it.length >= 2 }
    }

    fun titleCase(key: String): String = key.split(' ').filter { it.isNotEmpty() }.joinToString(" ") { w ->
        when {
            w in acronyms -> w
            w.length == 1 -> w
            w == "AND" -> "&"
            w.any { it.isDigit() } -> w
            else -> w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun prettyVpa(user: String, id: String, phone: Boolean): String = when {
        phone -> id
        user.startsWith("paytmqr") || user.startsWith("paytm-") -> "Paytm merchant"
        Regex("""^q\d{5,}$""").matches(user) -> "PhonePe merchant"
        user.startsWith("bharatpe") -> "BharatPe merchant"
        user.startsWith("gpay-") -> "Google Pay merchant"
        else -> {
            val words = user.replace(Regex("""[._\-]+"""), " ").replace(Regex("""\d+"""), " ").trim().replace(Regex("""\s+"""), " ")
            if (words.count { it.isLetter() } >= 3) titleCase(words.uppercase()) else id
        }
    }

    // ---- people --------------------------------------------------------------------------

    private val givenNames: Set<String> = (
        "KUMAR NAIR MENON PILLAI VARGHESE THOMAS JOSEPH MATHEW GEORGE JOHN JOSE PHILIP ABRAHAM KURIAN CHACKO MOHAMMED MUHAMMED " +
            "MUHAMMAD MOHAMED ABDUL RASHEED SHAJI BIJU SAJI ANIL SUNIL SURESH RAJESH RAMESH MAHESH GIREESH SREEJITH VISHNU ARJUN " +
            "ARUN AKHIL NIKHIL RAHUL AMAL ADARSH ABHINAND ABHIJITH ABHISHEK ATHUL ANANDU ANAND GOKUL HARI KRISHNAN KRISHNA UNNI " +
            "NAMBIAR WARRIER PANICKER KURUP IYER SHARMA DEVI AMMA KUTTY BEEVI FATHIMA AYSHA AMINA ANJALI ANJU ATHIRA ASWATHY " +
            "DIVYA LAKSHMI MEERA NEETHU REMYA RESHMA SREELAKSHMI PARVATHY GAYATHRI SNEHA MARY ANNIE LEELA SUDHA GEETHA SHEEBA " +
            "BINDU SINDHU JAYAN BABU RAVI MOHAN GOPAL RAJAN RAJU PRASAD PRAKASH PRADEEP PRAVEEN SANTHOSH SATHEESH SHIBU SHYAM " +
            "SOORAJ SUJITH VIJAY VINOD VIPIN VIVEK YADHU ANTONY AUGUSTINE SEBASTIAN FRANCIS XAVIER PAUL PETER JAMES JACOB DAVID " +
            "DANIEL SAMUEL STEPHEN ALEX BASIL BINU CYRIL EDWIN FEBIN JERIN JIBIN JITHIN JOBIN JOEL LIBIN LIJO MELVIN NITHIN " +
            "RENJITH ROBIN ROHAN ROSHAN SACHIN SANDEEP SANJAY SARATH SHARON SIDHARTH TONY TIJO NOUSHAD NIZAR RIYAS SHAFI SHAMEER " +
            "SALIM ASHRAF BASHEER HAMZA IBRAHIM ISMAIL JABIR KABEER LATHEEF MAJEED NASAR RAFEEQ SIDDIQUE USMAN YOUSUF ZUBAIR " +
            "TITUS VARKEY KURUVILLA CHERIAN ITTY OOMMEN NINAN KOSHY EAPEN PUNNOOSE MANI DAS SHAJAHAN SHAHUL HAMEED NAVAS ANAS " +
            "AJMAL AFSAL FAISAL HARIS IRSHAD JUNAID NIHAL RINSHAD SHIHAB SINAN ASWIN ASHWIN ANOOP ANEESH AJAY AJITH AJEESH " +
            "DEEPAK DEEPU DILEEP GANESH GIRISH JAYESH JISHNU KIRAN MANOJ MIDHUN NAVEEN NIDHIN PRANAV RAKESH RATHEESH SREERAG " +
            "SREEHARI SUDHEESH VAISHNAV VINEETH AISWARYA AKSHARA AMRITHA ANAGHA ARCHANA ARYA DEVIKA GREESHMA HARITHA KAVYA " +
            "KEERTHANA MALAVIKA NANDANA POOJA REVATHY SANDRA SRUTHI SWATHI VARSHA VISMAYA ACHAN AMMACHI CHETTAN CHECHI SINGH " +
            "REDDY RAO GUPTA VERMA PATEL SHAH KHAN ALI AHMED HUSSAIN FERNANDEZ DSOUZA PEREIRA"
        ).split(' ').toSet()

    /**
     * true when [key] reads like an individual's name. Used only as a tie-breaker once the
     * merchant dictionaries have found nothing.
     */
    fun looksLikePerson(key: String?): Boolean {
        if (key.isNullOrBlank() || key.any { it.isDigit() }) return false
        val tokens = key.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 5) return false
        if (MerchantBook.isBusinessName(key)) return false
        val initials = tokens.count { it.length == 1 }
        if (initials == tokens.size) return false
        if (initials > 0 && tokens.any { it.length >= 3 }) return true
        return tokens.any { it in givenNames }
    }
}
