package app.kanakku.ai

import kotlin.math.ln

/** One note in the local library the advisor draws on. */
data class Card(val id: String, val title: String, val tags: String, val body: String)

/**
 * The advisor's reference library: India- and Kerala-specific personal finance notes, kept on
 * the device. The most relevant notes for a question are retrieved and handed to the model so
 * that its answer rests on this material and the user's own numbers, not on guesswork.
 *
 * Figures that change (tax rates, lot sizes, interest rates) are written as "as of" values and
 * the model is told to say so.
 */
object KnowledgeBase {

    val cards: List<Card> = listOf(
        Card(
            "order", "The order money should go in",
            "plan priority start begin invest leftover surplus first step allocate where",
            "Sequence that maximises long-run wealth: (1) clear any credit-card balance in full – it costs 40%+ a year, no investment beats that; " +
                "(2) hold a starter buffer of one month's spending, then build to three; (3) automate a monthly SIP into a low-cost equity index fund – " +
                "this is the engine; (4) add a higher-growth satellite (mid/small-cap index, flexi-cap) once the buffer exists; (5) keep a small " +
                "learning budget for hands-on market experience. Raise the SIP every time income rises.",
        ),
        Card(
            "emergency", "Emergency buffer",
            "emergency fund buffer safety liquid savings account sweep fd job loss medical",
            "Target three months of spending, six if income is irregular or others depend on you. Keep it where it earns something and can be " +
                "withdrawn within a day: a sweep-in/auto-sweep FD, or a liquid mutual fund (historically about 6–7% a year, as of 2025). It is " +
                "insurance, not an investment – its job is to stop you selling equity at a loss or using a credit card when something breaks.",
        ),
        Card(
            "index", "Index funds: the core engine",
            "index fund nifty 50 sensex next 50 passive sip mutual fund core equity long term compounding direct plan expense ratio",
            "A Nifty 50 index fund buys India's 50 largest companies for roughly 0.1–0.3% a year in a direct plan. Over rolling 10-year periods the " +
                "Nifty 50 total-return index has delivered roughly 11–14% a year (past performance, not a promise) and most actively managed large-cap " +
                "funds have failed to beat it after costs. Use the direct plan, growth option, on a monthly SIP date just after salary. This is the " +
                "highest-probability way to build wealth from a salary.",
        ),
        Card(
            "growth", "Higher-potential equity: mid and small caps",
            "midcap smallcap small cap mid cap next 50 flexi cap high return potential aggressive growth satellite momentum",
            "The Nifty Midcap 150 and Smallcap 250 indices have outpaced the Nifty 50 over long stretches (mid-caps roughly 15–18% a year over 10–15 " +
                "years to 2025) but fall 40–60% in bad markets and can stay down for years. They suit money you will not need for 7+ years. A sensible " +
                "aggressive mix for someone in their twenties: 60–70% large-cap index, 20–30% mid/small-cap index or a flexi-cap fund, the rest for " +
                "learning. Add to them on a SIP rather than a lump sum so drawdowns work for you.",
        ),
        Card(
            "sip", "SIP mechanics and step-ups",
            "sip systematic investment plan monthly step up top up compounding how much start minimum rupee cost averaging",
            "A SIP auto-debits a fixed amount monthly; many funds start at ₹100–₹500. ₹5,000 a month at 12% a year grows to about ₹11.6 lakh in " +
                "10 years, ₹50 lakh in 20 and ₹1.76 crore in 30. Stepping the amount up 10% every year roughly doubles the 20-year result. Time in " +
                "the market matters more than the starting amount, so start now with whatever is left and increase it.",
        ),
        Card(
            "creditcard", "Using a credit card without paying for it",
            "credit card sbi card bill interest minimum due late fee utilisation cibil emi revolve statement",
            "Pay the total amount due, never the minimum: revolving costs about 3–3.75% a month (40%+ a year) and removes the interest-free period on " +
                "new spends. Late payment adds a fee and marks your credit history. Keep utilisation under about 30% of the limit for a strong CIBIL " +
                "score. Avoid cash withdrawal on the card (fee plus interest from day one) and 'no-cost' EMIs that carry processing fees and GST. " +
                "Used properly the card is a free 20–50 day loan plus rewards.",
        ),
        Card(
            "budget", "Splitting a ₹45–50k salary",
            "budget split salary 50 30 20 needs wants savings rule monthly plan percentage",
            "A workable frame: about 50% needs (rent, food, bills, transport), 30% wants, at least 20% saved and invested. On ₹48,000 that is " +
                "₹9,600+ invested monthly. People who build wealth quickly push saving toward 30–40% while living costs are low. Pay yourself first: " +
                "move the investment amount on salary day and spend what remains.",
        ),
        Card(
            "food", "Food delivery and eating out",
            "food swiggy zomato delivery eating out restaurant cut reduce save dining",
            "Delivery orders carry platform fees, packaging, delivery and menu mark-ups that often add 25–40% over eating at the same place. Easy cuts " +
                "that keep the habit: order for pickup, batch orders, cap delivery days per week, use a tiffin/mess for weekday lunches. Redirecting " +
                "even ₹2,000 a month to a SIP is about ₹4.6 lakh in ten years at 12%.",
        ),
        Card(
            "subscriptions", "Subscriptions",
            "subscription netflix spotify prime hotstar youtube recurring cancel audit annual plan",
            "List every recurring charge and ask whether you used it in the last 30 days. Share family plans, rotate streaming services month by month " +
                "instead of holding all of them, and prefer annual plans only for services you have kept for a year. Small recurring charges are the " +
                "easiest money to redirect into a SIP because you will not miss them.",
        ),
        Card(
            "learnmarkets", "Learning markets with small money",
            "learn trading stocks beginner first trade small amount 200 demat broker practice start how to trade experience",
            "The fastest way to learn is to have a little real money at stake and write down what happens. Open a demat account with a discount " +
                "broker (Zerodha, Groww, Upstox and others; account opening is free or a few hundred rupees). First trades should be delivery buys of an " +
                "index ETF or one share of a business you understand, ₹200–₹500 at a time. Learn market vs limit orders, bid–ask spread, settlement " +
                "(T+1), and how brokerage, STT, stamp duty and GST eat small trades. Keep this learning money separate from the SIP that builds wealth.",
        ),
        Card(
            "etf", "ETFs for a first purchase",
            "etf niftybees nifty bees gold etf first buy unit exchange traded fund juniorbees",
            "An ETF trades like a share but holds a whole index. A Nifty 50 ETF unit costs roughly ₹250–₹300 (as of 2025), so a single unit is a real " +
                "market position for the price of a meal. Check that the ETF has high trading volume and trades close to its indicative NAV. Gold ETFs " +
                "and Nifty Next 50 ETFs work the same way. Buying one unit, watching it for a month and reading why it moved teaches more than a course.",
        ),
        Card(
            "stocks", "Picking individual shares",
            "stocks shares direct equity company pick fundamental analysis pe ratio results quarterly portfolio multibagger",
            "Individual shares can far outperform an index and can also go to zero. Before buying, read the latest annual report and two quarterly " +
                "results: is revenue growing, is profit growing faster than debt, what is the P/E against its own history and peers, who are the " +
                "promoters. Limit any one share to 5% of your equity money and direct shares in total to about 10–20% until you have a multi-year " +
                "record that beats the index. Tips from Telegram, YouTube and WhatsApp groups are how people lose money, not make it.",
        ),
        Card(
            "intraday", "Intraday trading",
            "intraday day trading trade today scalping margin leverage quick profit daily",
            "Intraday means buying and selling the same day, usually with leverage. SEBI's 2024 study found about 7 in 10 individual intraday traders " +
                "in the cash segment lost money, and the share was higher among the young and among frequent traders. If you want the experience: use " +
                "₹200–₹500 you would otherwise spend on a meal, one liquid large-cap share or ETF, a stop-loss placed before entry, no leverage, and " +
                "a journal entry afterwards with entry, exit, costs and what you expected. Treat the cost as tuition.",
        ),
        Card(
            "options", "Options and F&O",
            "options trading futures f&o fno call put premium lot size nifty banknifty derivatives expiry theta option buying selling",
            "An option is a contract whose price (premium) moves with an index or share and decays to zero at expiry. Buying options risks only the " +
                "premium; selling them needs margin of about a lakh or more and has open-ended risk. Contracts trade in lots fixed by the exchange " +
                "(a Nifty lot has been 65–75 units recently), so one cheap out-of-the-money option can still cost ₹500–₹5,000. SEBI's studies for " +
                "FY22–FY25 found roughly 9 in 10 individual F&O traders lost money, with average losses over ₹1 lakh a year. Learn in this order: " +
                "paper-trade for a month, learn how premium, time decay and volatility interact, then if you still want real exposure buy a single " +
                "option with a premium you are content to lose entirely and a pre-decided exit. Never sell options or use borrowed money while learning.",
        ),
        Card(
            "papertrading", "Paper trading",
            "paper trade virtual simulator practice options demo sensibull tradingview",
            "Paper trading is placing trades with virtual money on real prices. Broker and third-party tools offer it (for example virtual trading " +
                "on Sensibull or TradingView). Run it for at least 20 trades with written reasons before risking money on anything leveraged. It will " +
                "not teach the emotion of loss, which is why a small real-money stage follows it.",
        ),
        Card(
            "tax", "Tax on investments and on a ₹5–6 lakh salary",
            "tax income tax new regime rebate 12 lakh stcg ltcg capital gains f&o business income 80c elss",
            "Under the new tax regime (FY 2025-26 rules) income up to ₹12 lakh carries no tax after the rebate, so on ₹5–6 lakh a year tax-saving " +
                "products such as ELSS or tax-saver FDs give no benefit – choose investments on return, not deductions. Equity shares and equity funds: " +
                "gains on units held under a year are taxed at 20%; over a year at 12.5% above ₹1.25 lakh of gains a year (Budget 2024 rates). Intraday " +
                "profit is speculative business income and F&O profit is non-speculative business income, both taxed at slab rates and needing ITR-3. " +
                "The capital-gains rebate does not cover special-rate gains, so keep records. Rules change each Budget; confirm current rates.",
        ),
        Card(
            "ppf", "PPF, EPF and NPS",
            "ppf epf vpf nps retirement provident fund pension long term safe tax free",
            "EPF is deducted from salary and earns a government-declared rate (8.25% for FY 2024-25); do not withdraw it when changing jobs. PPF " +
                "pays a government-set rate (7.1% as of 2025), tax-free, with a 15-year lock-in – a safe debt holding, not a growth engine. NPS is " +
                "low-cost with equity exposure but locks money to age 60. For someone in their twenties with no tax to save, equity index funds should " +
                "come first and PPF/NPS later as the safe portion.",
        ),
        Card(
            "fd", "FDs, RDs and short-term parking",
            "fd fixed deposit rd recurring deposit bank safe short term goal parking small finance bank dicgc",
            "For money needed within three years use FDs, RDs or debt funds rather than equity. Small finance banks pay more than large banks; " +
                "deposits are insured by DICGC up to ₹5 lakh per bank. FD interest is taxed at your slab rate. An RD is a good tool for a known " +
                "expense 6–18 months away (a phone, a trip home, an annual premium).",
        ),
        Card(
            "gold", "Gold",
            "gold jewellery sgb sovereign gold bond gold etf digital gold kerala ornaments making charges",
            "Gold has returned roughly 10–12% a year in rupees over the last two decades and tends to rise when shares fall, so 5–10% of a " +
                "portfolio in gold steadies it. Jewellery is the expensive way to hold it: making charges of 8–25% plus 3% GST are lost on resale. " +
                "Gold ETFs or gold mutual funds track the price at low cost. New Sovereign Gold Bond issues stopped in 2024; existing bonds trade on " +
                "exchanges. Jeweller monthly schemes only make sense if you were going to buy that jewellery anyway.",
        ),
        Card(
            "chitty", "KSFE chitty and Kerala savings options",
            "ksfe chitty chit fund kuri kerala treasury cooperative bank society deposit",
            "A KSFE chitty is a government-run chit: members pay a monthly instalment, one member takes the pot each month by auction or lot, and the " +
                "discount is shared as dividend after the 5% foreman commission. Bid early and it is a loan; wait till the end and it is a modest-return " +
                "forced saving, often around FD-like returns. It is useful for discipline or a planned lump sum, less so for wealth creation. Kerala " +
                "Treasury deposits pay high rates backed by the state. Deposits in primary co-operative societies are not covered by DICGC " +
                "insurance – check before placing large sums.",
        ),
        Card(
            "insurance", "Insurance",
            "insurance term life health cover policy lic endowment ulip premium medical",
            "Buy pure protection, keep investing separate. Health cover of ₹5–10 lakh in your own name is cheap in your twenties and stays with you " +
                "when you change employer. Term life insurance matters once someone depends on your income. Endowment, money-back and most ULIP plans " +
                "return around 4–6% a year – a term plan plus an index fund beats them comfortably.",
        ),
        Card(
            "debt", "Loans, BNPL and EMIs",
            "loan emi bnpl personal loan gold loan pay later debt interest prepay",
            "Borrowing for consumption works against compounding. Personal loans and pay-later products cost 14–36% a year. If you hold such debt, " +
                "clearing it is a guaranteed return at that rate – do it before investing beyond the buffer. Gold loans from Kerala's NBFCs are cheaper " +
                "but still 9–24%. Education and home loans are the exceptions worth carrying.",
        ),
        Card(
            "crypto", "Crypto",
            "crypto bitcoin ethereum coin wazirx coindcx token",
            "Crypto gains in India are taxed at a flat 30% with 1% TDS on each sale and no set-off of losses (as of 2025). Prices can halve in weeks. " +
                "If you want exposure, treat it as part of the learning budget: a small fixed amount in Bitcoin or Ether on a registered Indian " +
                "exchange, never leverage or unknown tokens.",
        ),
        Card(
            "goals", "Matching money to goals",
            "goal bike phone laptop travel wedding house down payment time horizon short medium long",
            "Decide where money goes by when you need it. Under 1 year: savings/sweep FD or liquid fund. 1–3 years: RD, FD or short-duration debt " +
                "fund. 3–7 years: a mix, for example a balanced or large-cap index fund. 7 years and beyond: equity index funds with a mid/small-cap " +
                "satellite. Naming the goal and its date makes it far more likely the money survives a tempting sale.",
        ),
        Card(
            "sinking", "Planning for festivals and annual costs",
            "onam vishu christmas eid festival annual yearly expense insurance premium trip home sinking fund",
            "Onam, Vishu, Christmas, Eid, weddings, trips home and annual premiums are predictable. Add them up, divide by twelve and move that " +
                "amount into an RD or separate account each month. It stops a festival month from wiping out the SIP or landing on the credit card.",
        ),
        Card(
            "family", "Money sent to and received from family",
            "family parents pocket money send home support transfer",
            "Treat regular support to family as a fixed line in the plan, sent on salary day, so it is never in competition with investing. Count " +
                "pocket money received as income but plan as if it may stop: base the SIP on salary alone and invest irregular receipts as lump sums.",
        ),
        Card(
            "cibil", "Credit score",
            "cibil credit score report loan eligibility history",
            "A CIBIL score of 750+ gets the best loan rates later. It is built by paying every card bill and EMI on time, keeping card utilisation " +
                "low, keeping your oldest card open and not applying for many loans at once. You can check your report free once a year.",
        ),
        Card(
            "scams", "Fraud and bad advice",
            "scam fraud otp tips telegram guaranteed returns ponzi whatsapp finfluencer",
            "Anything promising guaranteed or 'double in months' returns is a scam: fake trading apps, Telegram tip groups, ponzi schemes and " +
                "task-payment frauds are common. Never share an OTP or UPI PIN – a PIN is only ever needed to send money, never to receive it. Invest " +
                "only through SEBI-registered brokers and AMCs, and check that anyone selling advice is a SEBI-registered adviser.",
        ),
        Card(
            "rent", "Rent",
            "rent house flat hostel pg housing share room deposit",
            "Keeping rent near or under 25–30% of take-home pay leaves room to invest. Sharing a flat is usually the single largest saving available " +
                "on a starting salary. HRA exemption applies only under the old tax regime.",
        ),
        Card(
            "review", "A monthly money review",
            "review track habit monthly check routine progress net worth",
            "Once a month, ten minutes: look at what came in and went out, check the SIP ran, move any surplus, and note net worth (bank + " +
                "investments − card dues). Wealth comes from the habit more than from any single clever choice.",
        ),
    )

    private val stop = setOf(
        "the", "a", "an", "and", "or", "to", "of", "in", "on", "for", "with", "is", "are", "i", "my", "me", "do", "can", "should", "how",
        "what", "it", "this", "that", "be", "have", "has", "at", "as", "by", "from", "you", "your", "much", "any", "some", "about", "want",
    )

    internal fun tokens(text: String): List<String> =
        text.lowercase().split(Regex("""[^a-z0-9&]+""")).filter { it.length > 1 && it !in stop }.map(::stem)

    private fun stem(word: String): String = when {
        word.length > 5 && word.endsWith("ing") -> word.dropLast(3)
        word.length > 4 && word.endsWith("es") -> word.dropLast(2)
        word.length > 3 && word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    private val indexed = cards.map { card ->
        val weighted = tokens(card.title) + tokens(card.title) + tokens(card.tags) + tokens(card.tags) + tokens(card.body)
        card to weighted.groupingBy { it }.eachCount()
    }
    private val averageLength = indexed.map { it.second.values.sum() }.average()
    private val documentFrequency: Map<String, Int> =
        indexed.flatMap { it.second.keys }.groupingBy { it }.eachCount()

    /** BM25 over the library. Returns the [limit] best notes for [query], best first. */
    fun retrieve(query: String, limit: Int = 4): List<Card> {
        val terms = tokens(query).distinct()
        if (terms.isEmpty()) return emptyList()
        val n = indexed.size.toDouble()
        return indexed.map { (card, tf) ->
            val length = tf.values.sum()
            val score = terms.sumOf { term ->
                val f = tf[term] ?: return@sumOf 0.0
                val df = documentFrequency[term] ?: 0
                val idf = ln(1 + (n - df + 0.5) / (df + 0.5))
                idf * (f * 2.2) / (f + 1.2 * (0.25 + 0.75 * length / averageLength))
            }
            card to score
        }.filter { it.second > 0.6 }.sortedByDescending { it.second }.take(limit).map { it.first }
    }

    fun byId(id: String): Card? = cards.firstOrNull { it.id == id }
}
