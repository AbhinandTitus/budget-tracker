package app.kanakku.core.sms

/** The bank, card issuer or wallet behind an SMS header. */
data class Institution(val id: String, val name: String, val kind: Kind) {
    enum class Kind { BANK, CARD, WALLET, OTHER }
}

/**
 * Indian commercial SMS arrive from headers such as `VM-SBICRD-S` or `JD-HDFCBK`:
 * an operator/circle prefix, a six-character sender id and, since 2025, a suffix giving the
 * message class – S(ervice), T(ransactional), P(romotional) or G(overnment).
 */
data class SenderInfo(
    val header: String,
    val suffix: Char?,
    val isPersonalNumber: Boolean,
    val isNumericHeader: Boolean,
    val institution: Institution?,
) {
    val isPromotional: Boolean get() = suffix == 'P'
}

object Senders {

    private val personal = Regex("""^\+?\d[\d\s-]{6,}$""")

    private fun bank(id: String, name: String) = Institution(id, name, Institution.Kind.BANK)
    private fun card(id: String, name: String) = Institution(id, name, Institution.Kind.CARD)
    private fun wallet(id: String, name: String) = Institution(id, name, Institution.Kind.WALLET)
    private fun other(id: String, name: String) = Institution(id, name, Institution.Kind.OTHER)

    /** Ordered: the first pattern found in the header wins, so specific ids precede general ones. */
    private val table: List<Pair<Regex, Institution>> = listOf(
        "SBICRD|SBICAR|SBCARD|SBIECD" to card("sbicard", "SBI Card"),
        "SBI(?!LIF|MF|GEN|SEC|CAP|FND|PEN|DFH)|SBYONO|CBSSBI" to bank("sbi", "SBI"),
        "HDFCB|HDFCBANK" to bank("hdfc", "HDFC Bank"),
        "ICICIB|ICICIT|ICICIO|ICIBNK|ICICBK" to bank("icici", "ICICI Bank"),
        "AXISB|AXSBNK|AXISCR|AXISCC" to bank("axis", "Axis Bank"),
        "KOTAKB|KOTAKM|KOTKBK|KMBL" to bank("kotak", "Kotak"),
        "FEDBNK|FEDBKK|FEDBAN|FEDERA|FEDFIB" to bank("federal", "Federal Bank"),
        "SIBSMS|SIBANK|SIBBNK|SIBLTD|SOUTHI" to bank("sib", "South Indian Bank"),
        "CSBBNK|CSBANK|CSBSMS" to bank("csb", "CSB Bank"),
        "DHANBK|DLXBNK|DHANLX|DHNBNK" to bank("dhanlaxmi", "Dhanlaxmi Bank"),
        "ESAFBK|ESAFBN|ESAFSF" to bank("esaf", "ESAF Bank"),
        "KGBANK|KGBKER|KERGRB|KLGBNK|KGBSMS" to bank("kgb", "Kerala Gramin Bank"),
        "KERBNK|KSCBNK|KBANKL" to bank("keralabank", "Kerala Bank"),
        "CANBNK|CNRBNK|CANARB|CANBAN" to bank("canara", "Canara Bank"),
        "UNIONB|UBIBNK|UBOI|UNIONBK" to bank("union", "Union Bank"),
        "PNBSMS|PNBBNK|PUNBNK|PNBANK" to bank("pnb", "PNB"),
        "BOBCRD|BOBCAR" to card("bobcard", "BOB Card"),
        "BOBSMS|BOBTXN|BARODA|BOBBNK|BOBONE" to bank("bob", "Bank of Baroda"),
        "IDFCFB|IDFCBK|IDFCFI|IDFCBN" to bank("idfc", "IDFC FIRST"),
        "INDUSB|INDUSL|INDUSI" to bank("indusind", "IndusInd"),
        "YESBNK|YESBK|YESBAN" to bank("yes", "Yes Bank"),
        "IOBCHN|IOBBNK|IOBSMS|IOBANK" to bank("iob", "IOB"),
        "INDBNK|INDBAN|INDNBK|IBANKL" to bank("indianbank", "Indian Bank"),
        "BOIIND|BOIBNK|BKOFIN" to bank("boi", "Bank of India"),
        "CENTBK|CBOI|CENBNK" to bank("central", "Central Bank"),
        "KBLBNK|KTKBNK|KARBNK" to bank("ktk", "Karnataka Bank"),
        "KVBANK|KVBL|KVBSMS" to bank("kvb", "KVB"),
        "TMBANK|TMBL|TMBSMS" to bank("tmb", "TMB"),
        "CUBANK|CUBL|CUBSMS" to bank("cub", "City Union Bank"),
        "AUBANK|AUSFB|AUBSMS" to bank("au", "AU Bank"),
        "RBLCRD|RBLCAR" to card("rblcard", "RBL Card"),
        "RBLBNK|RBLBAN" to bank("rbl", "RBL Bank"),
        "ONECRD|ONECAR|ONCARD" to card("onecard", "OneCard"),
        "AMEX" to card("amex", "Amex"),
        "SCBANK|SCBL|STANCH|SCBIND" to bank("sc", "Standard Chartered"),
        "HSBCIN|HSBCBK|HSBCBN" to bank("hsbc", "HSBC"),
        "CITIBK|CITIBN|CITIBA" to bank("citi", "Citi"),
        "DBSBNK|DBSBAN|DIGIBK" to bank("dbs", "DBS"),
        "BANDHN|BDNBNK" to bank("bandhan", "Bandhan Bank"),
        "IDBIBK|IDBIBN|IDBIBA" to bank("idbi", "IDBI Bank"),
        "UCOBNK|UCOBAN" to bank("uco", "UCO Bank"),
        "MAHABK|MAHBNK|BOMBNK" to bank("bom", "Bank of Maharashtra"),
        "JKBANK|JKBNK" to bank("jk", "J&K Bank"),
        "EQUITS|EQUTAS|EQUITA" to bank("equitas", "Equitas"),
        "UJJIVN|UJJBNK" to bank("ujjivan", "Ujjivan"),
        "IPPBMB|IPPBBK|IPPBSM|IPPB" to bank("ippb", "India Post Payments Bank"),
        "AIRBNK|AIRPAY|APBANK" to bank("airtelbank", "Airtel Payments Bank"),
        "JIOPBK|JIOBNK|JPBANK" to bank("jiobank", "Jio Payments Bank"),
        "NSDLPB|NSDLBK" to bank("nsdlpb", "NSDL Payments Bank"),
        "FINOBK|FINOPB" to bank("fino", "Fino Payments Bank"),
        "JUPITR|JTEDGE|JUPBNK" to bank("jupiter", "Jupiter"),
        "FIMONY|FIBANK|EPIFI" to bank("fi", "Fi"),
        "NIYOBK|NIYOSB" to bank("niyo", "Niyo"),
        "SLICE|SLCEIT|SLICEB" to card("slice", "slice"),
        "SCAPIA" to card("scapia", "Scapia"),
        "KIWICC|GOKIWI" to card("kiwi", "Kiwi"),
        "PAYTMB|PYTMBK" to bank("paytmbank", "Paytm Payments Bank"),
        "PAYTM|PYTM" to wallet("paytm", "Paytm"),
        "AMZNPY|APAYIN|AMZPAY" to wallet("amazonpay", "Amazon Pay"),
        "PHONPE|PHONEP" to wallet("phonepe", "PhonePe"),
        "MOBIKW" to wallet("mobikwik", "MobiKwik"),
        "FRECHG|FREECH" to wallet("freecharge", "Freecharge"),
        "LZYPAY|LAZYPY" to other("lazypay", "LazyPay"),
        "SIMPL" to other("simpl", "Simpl"),
        "CRED" to other("cred", "CRED"),
    ).map { (pattern, inst) -> Regex(pattern) to inst }

    fun parse(address: String): SenderInfo {
        val raw = address.trim()
        if (raw.isEmpty() || personal.matches(raw)) {
            return SenderInfo(raw, null, isPersonalNumber = true, isNumericHeader = false, institution = null)
        }
        val parts = raw.uppercase().split('-', ' ', '_').filter { it.isNotBlank() }
        var suffix: Char? = null
        var core = parts
        if (core.size >= 2 && core.last().length == 1 && core.last()[0] in "STPG") {
            suffix = core.last()[0]
            core = core.dropLast(1)
        }
        // Drop the two-letter operator/circle prefix when a longer id follows.
        if (core.size >= 2 && core.first().length <= 2) core = core.drop(1)
        val header = core.joinToString("")
        val numeric = header.isNotEmpty() && header.all { it.isDigit() }
        val institution = if (numeric) null else table.firstOrNull { it.first.containsMatchIn(header) }?.second
        return SenderInfo(header, suffix, isPersonalNumber = false, isNumericHeader = numeric, institution = institution)
    }
}
