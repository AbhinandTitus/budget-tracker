package app.kanakku.ai

import kotlin.math.pow

data class PlanLine(val label: String, val amountMinor: Long, val why: String, val cardId: String)

/** What to do with one month's surplus. Produced on the device; the model explains it, it does not invent it. */
data class Plan(
    val surplusMinor: Long,
    val essentialsMinor: Long,
    val bufferMinor: Long?,
    val bufferTargetMinor: Long,
    val stage: Stage,
    val lines: List<PlanLine>,
    /** Value after ten years if the equity lines are invested monthly and earn 12% a year. */
    val tenYearMinor: Long,
) {
    enum class Stage { BUILD_BUFFER, STRENGTHEN_BUFFER, GROW }
}

/**
 * The allocation rule. It follows the order in the "order" note of the knowledge base:
 * buffer first, then a low-cost index core, then a higher-growth satellite, with a small
 * hands-on learning budget throughout. The split tilts toward growth as the buffer fills.
 */
object Planner {

    private const val STEP = 100_00L

    fun build(surplusMinor: Long, essentialsMinor: Long, knownBufferMinor: Long?): Plan? {
        val surplus = surplusMinor / STEP * STEP
        if (surplus < 500_00 || essentialsMinor <= 0) return null
        val target = essentialsMinor * 3 / STEP * STEP
        val buffer = knownBufferMinor
        val stage = when {
            buffer == null || buffer < essentialsMinor -> Plan.Stage.BUILD_BUFFER
            buffer < target -> Plan.Stage.STRENGTHEN_BUFFER
            else -> Plan.Stage.GROW
        }
        val (bufferShare, growthShare, learnShare, learnCap) = when (stage) {
            Plan.Stage.BUILD_BUFFER -> Shares(50, 10, 5, 1_000_00L)
            Plan.Stage.STRENGTHEN_BUFFER -> Shares(30, 18, 7, 1_000_00L)
            Plan.Stage.GROW -> Shares(10, 27, 8, 1_500_00L)
        }
        fun part(percent: Int) = surplus * percent / 100 / STEP * STEP
        val learn = if (surplus >= 2_000_00) part(learnShare).coerceIn(200_00, learnCap) else 0L
        val safe = part(bufferShare)
        val growth = part(growthShare)
        val core = surplus - learn - safe - growth

        val lines = buildList {
            if (safe > 0) {
                add(
                    PlanLine(
                        if (stage == Plan.Stage.GROW) "Near-term goals" else "Emergency buffer",
                        safe,
                        if (stage == Plan.Stage.GROW) "Sweep FD or liquid fund for things you will buy within three years"
                        else "Sweep FD or liquid fund until it covers three months of spending",
                        "emergency",
                    ),
                )
            }
            add(PlanLine("Index fund SIP", core, "Nifty 50 index fund, direct plan – the long-term engine", "index"))
            if (growth > 0) add(PlanLine("Growth satellite", growth, "Midcap 150 or flexi-cap fund for higher long-run potential", "growth"))
            if (learn > 0) add(PlanLine("Learning budget", learn, "Real trades in small size to learn how markets work", "learnmarkets"))
        }
        return Plan(surplus, essentialsMinor, buffer, target, stage, lines, futureValue(core + growth, 0.12, 10))
    }

    private data class Shares(val buffer: Int, val growth: Int, val learn: Int, val learnCap: Long)

    /** Future value of a monthly investment made at the start of each month. */
    fun futureValue(monthlyMinor: Long, annualRate: Double, years: Int): Long {
        val r = annualRate / 12
        val n = years * 12
        return (monthlyMinor * (((1 + r).pow(n) - 1) / r) * (1 + r)).toLong()
    }
}

data class LearnStep(val title: String, val stake: String, val action: String, val lesson: String, val cardId: String)

/** A hands-on course in markets, one small real action at a time. */
object LearningTrack {
    val steps: List<LearnStep> = listOf(
        LearnStep(
            "Open a demat account", "Free – ₹300",
            "Open an account with a SEBI-registered discount broker and link UPI. Find the order screen and the charges page.",
            "What a demat account holds, and what every trade costs before it makes anything.",
            "learnmarkets",
        ),
        LearnStep(
            "Buy one index ETF unit", "About ₹300",
            "Buy a single unit of a Nifty 50 ETF as a delivery order. Note the price you paid against the last traded price.",
            "Market and limit orders, the bid–ask spread, T+1 settlement.",
            "etf",
        ),
        LearnStep(
            "Start a ₹500 index SIP", "₹500 a month",
            "Set up a monthly SIP in a Nifty 50 index fund, direct plan, dated just after salary day.",
            "NAV, expense ratio, direct versus regular plans, how automation beats willpower.",
            "sip",
        ),
        LearnStep(
            "Own one share of a company you use", "Up to ₹500",
            "Pick a listed business whose product you use. Read its latest quarterly result, then buy one share.",
            "Revenue, profit, P/E and market cap – what a share price is actually a price of.",
            "stocks",
        ),
        LearnStep(
            "Place a limit order and a stop-loss", "₹200 – ₹500",
            "Buy with a limit order below the current price, then set a GTT stop-loss 5% under your entry.",
            "Deciding your exit before you enter – the core of risk control.",
            "stocks",
        ),
        LearnStep(
            "One intraday trade, journalled", "₹200 – ₹500",
            "Trade one liquid large-cap or ETF within a day without leverage. Write down entry, exit, every charge and what you expected.",
            "How costs and noise work against short-term trades. About 7 in 10 intraday traders lose money.",
            "intraday",
        ),
        LearnStep(
            "Paper-trade options for a month", "₹0",
            "Use a virtual trading tool to buy calls and puts on the Nifty. Log 20 trades with your reasoning.",
            "Premium, strike, expiry, time decay and why direction alone is not enough.",
            "papertrading",
        ),
        LearnStep(
            "Buy one option with money you can lose", "Up to ₹1,000",
            "Only after the paper month: buy a single Nifty option, with the premium as your total risk and an exit decided beforehand.",
            "What leverage feels like. About 9 in 10 individual F&O traders lose money, so size it as tuition.",
            "options",
        ),
        LearnStep(
            "Work out the tax on your trades", "₹0",
            "List this year's trades and classify each: delivery under a year, over a year, intraday, F&O.",
            "Capital gains rates, business income from intraday and F&O, and why records matter.",
            "tax",
        ),
        LearnStep(
            "Set a monthly review", "10 minutes",
            "Pick a date. Check the SIP ran, compare your direct picks with the index, and move the next surplus.",
            "Whether your own picks beat the index decides how much you should run yourself.",
            "review",
        ),
    )

    fun current(done: Int): LearnStep? = steps.getOrNull(done)
}
