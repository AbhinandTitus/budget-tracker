package app.kanakku.ai

import app.kanakku.core.Money
import app.kanakku.core.Months
import app.kanakku.core.category.Categories
import app.kanakku.core.insight.MonthSummary
import app.kanakku.core.insight.Recurring
import app.kanakku.core.insight.Summaries
import app.kanakku.core.model.AccountKind
import app.kanakku.core.model.TxnType
import app.kanakku.data.Repository
import app.kanakku.data.Settings
import app.kanakku.data.db.ChatDao
import app.kanakku.data.db.ChatEntity
import app.kanakku.data.db.TxnEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The user's position as the advisor sees it; also drives the plan card in the app. */
data class Position(
    val today: LocalDate,
    val months: List<MonthSummary>,
    val current: MonthSummary,
    val recurring: List<Recurring>,
    val averageLeftMinor: Long,
    val essentialsMinor: Long,
    val bufferMinor: Long?,
    val cardDueMinor: Long,
    val plan: Plan?,
)

/**
 * Financial guidance grounded in the user's own ledger.
 *
 * Retrieval-augmented: for each question the app assembles (a) a snapshot of the ledger,
 * (b) the allocation worked out by [Planner], (c) transactions relevant to the question and
 * (d) the best-matching notes from [KnowledgeBase]. Gemini writes the answer from that material.
 * Only merchant names, amounts, dates and rounded totals are sent – never message text or
 * account numbers.
 */
class Advisor(
    private val repository: Repository,
    private val settings: Settings,
    private val chat: ChatDao,
    private val client: GeminiClient,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    fun conversation(): Flow<List<ChatEntity>> = chat.observe()

    suspend fun clear() = chat.clear()

    suspend fun position(): Position {
        val today = LocalDate.now(zone())
        val thisMonth = YearMonth.from(today)
        val rows = repository.range(thisMonth.minusMonths(5), thisMonth)
        val summaries = (5 downTo 0).map { Summaries.of(thisMonth.minusMonths(it.toLong()), rows, zone()) }
        val complete = summaries.dropLast(1).filter { it.incomeMinor > 0 && it.spentMinor > 0 }.takeLast(3)
        val current = summaries.last()
        val averageLeft = when {
            complete.isNotEmpty() -> complete.sumOf { it.incomeMinor - it.consumedMinor } / complete.size
            else -> current.incomeMinor - current.consumedMinor
        }
        val essentials = when {
            complete.isNotEmpty() -> complete.sumOf { it.consumedMinor } / complete.size
            else -> current.consumedMinor
        }
        val buffer = repository.balances().filter { it.kind != AccountKind.WALLET }.takeIf { it.isNotEmpty() }?.sumOf { it.balanceMinor }
        val dues = repository.observeDues().first().filter { it.dueDay >= today.toEpochDay() }.sumOf { it.totalMinor }
        return Position(
            today = today,
            months = summaries,
            current = current,
            recurring = Summaries.recurring(rows, zone()),
            averageLeftMinor = averageLeft,
            essentialsMinor = essentials,
            bufferMinor = buffer,
            cardDueMinor = dues,
            plan = Planner.build(averageLeft, essentials, buffer),
        )
    }

    /** Sends [question] and stores both sides of the exchange. Failures are stored as a failed reply. */
    suspend fun ask(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _busy.value) return
        _busy.value = true
        try {
            chat.insert(ChatEntity(role = USER, text = text, createdAt = clock()))
            val reply = try {
                val key = settings.geminiKey() ?: throw GeminiException(GeminiException.Kind.NO_KEY, "no key")
                val prefs = settings.current()
                val position = position()
                val history = chat.latest(HISTORY).reversed().filter { !it.failed }.map { Turn(it.role == USER, it.text) }
                val turns = history.dropWhile { !it.fromUser }.ifEmpty { listOf(Turn(true, text)) }
                val rows = repository.range(YearMonth.from(position.today).minusMonths(5), YearMonth.from(position.today))
                val system = Prompts.system(position, prefs.learnStep, text, rows, zone())
                ChatEntity(role = MODEL, text = client.generate(key, prefs.geminiModel, system, turns), createdAt = clock())
            } catch (e: GeminiException) {
                ChatEntity(role = MODEL, text = e.userMessage, createdAt = clock(), failed = true)
            }
            chat.insert(reply)
        } finally {
            _busy.value = false
        }
    }

    /** Checks a key by listing models. Returns the models it can use. */
    suspend fun verify(apiKey: String): List<String> = client.listModels(apiKey.trim())

    suspend fun models(): List<String> = settings.geminiKey()?.let { client.listModels(it) }.orEmpty()

    /**
     * Asks the model to place merchants the dictionaries could not. Only merchant names are sent.
     * Returns how many transactions were re-filed.
     */
    suspend fun categoriseUnknownMerchants(): Int {
        val prefs = settings.current()
        if (!prefs.aiCategorise) return 0
        val key = settings.geminiKey() ?: return 0
        val unknown = repository.unknownMerchants().filter { '@' !in it.merchantKey }
        if (unknown.isEmpty()) return 0
        val choices = Categories.expense.map { it.id }
        val listing = unknown.joinToString("\n") { it.merchantKey }
        val system = "You classify merchant names from Indian bank alerts (many from Kerala) into spending categories. " +
            "Categories: " + Categories.expense.joinToString("; ") { "${it.id} = ${it.label}" } + ". " +
            "Names that are plainly an individual person go to 'people'. Use 'other' when the name gives no real clue. " +
            "Return one entry per input line, keeping each key exactly as given."
        val raw = client.generate(key, prefs.geminiModel, system, listOf(Turn(true, listing)), categorySchema(choices), temperature = 0.0)
        val known = unknown.map { it.merchantKey }.toSet()
        val suggestions = runCatching {
            Json.parseToJsonElement(raw).jsonArray.mapNotNull { item ->
                val o = item.jsonObject
                val k = o["key"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val c = o["category"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                if (k in known && c in choices) k to c else null
            }.toMap()
        }.getOrDefault(emptyMap())
        return repository.applySuggestedCategories(suggestions)
    }

    companion object {
        const val USER = "user"
        const val MODEL = "model"
        private const val HISTORY = 12
    }
}

/** Builds the grounding the model answers from. Pure, so it can be tested without a network. */
object Prompts {

    private val monthLabel = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
    private val dayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun system(position: Position, learnStep: Int, question: String, rows: List<TxnEntity>, zone: ZoneId): String = buildString {
        append(RULES).append("\n\n")
        append("# Their numbers\n").append(snapshot(position)).append("\n\n")
        position.plan?.let { append("# Allocation worked out by the app (present this, do not replace it)\n").append(plan(it)).append("\n\n") }
        append("# Learning track\n").append(learning(learnStep)).append("\n\n")
        val relevant = relevantTransactions(question, rows, position.today, zone)
        if (relevant.isNotEmpty()) append("# Transactions relevant to the question\n").append(relevant).append("\n\n")
        val notes = KnowledgeBase.retrieve(question + " " + autoTags(position), limit = 5)
        val always = listOfNotNull(KnowledgeBase.byId("order"))
        append("# Reference notes\n")
        (always + notes).distinctBy { it.id }.forEach { append("## ").append(it.title).append('\n').append(it.body).append("\n\n") }
    }

    private const val RULES = """You are the money guide inside a personal finance app used by one salaried person in their twenties living in Kerala, India. You can see their real income and spending below.

How to answer
- Be direct and specific. Use their actual rupee figures and name the exact next action (which kind of fund, how much, on what date).
- Aim for the best long-run outcome available to them: favour low-cost, high-expected-return choices and say plainly which option you would pick and why.
- When money is the topic, show the upside in numbers (for example what a monthly amount becomes in 10 and 20 years at a stated rate). State each risk once, as a fact with a number, then move on. No generic disclaimers and no "consult an adviser" lines.
- They want to learn markets by doing. When it fits, end with one "Try this" step from the learning track sized in rupees. You may discuss stocks, intraday, options and crypto openly; give the real odds from the reference notes and size the stake as tuition.
- Never promise or imply guaranteed returns. Never recommend a specific company's shares as a sure thing; you may explain how to evaluate one.
- Ground every figure in the data and notes provided. If something is not in them, say so briefly rather than guess. Rates and tax rules in the notes are "as of" values – say so when you quote them.
- Plain text, short paragraphs, at most six short bullet points, no tables, no headings. Under 220 words unless asked for depth. Amounts as ₹12,300."""

    fun snapshot(p: Position): String = buildString {
        val month = p.current.month
        val daysLeft = month.lengthOfMonth() - p.today.dayOfMonth + 1
        append("Today: ").append(p.today).append(" (").append(daysLeft).append(" days left in ").append(month.format(monthLabel)).append(")\n")
        append("Month by month (income / spent incl. investments / invested / left):\n")
        p.months.forEach { m ->
            if (m.incomeMinor == 0L && m.spentMinor == 0L) return@forEach
            append("- ").append(m.month.format(monthLabel)).append(": ")
                .append(Money.whole(m.incomeMinor)).append(" / ").append(Money.whole(m.spentMinor)).append(" / ")
                .append(Money.whole(m.investedMinor)).append(" / ").append(Money.whole(m.leftMinor)).append('\n')
        }
        append("This month by category: ")
        append(p.current.byCategory.joinToString(", ") { "${Categories.label(it.categoryId)} ${Money.whole(it.amountMinor)}" }.ifEmpty { "nothing yet" }).append('\n')
        if (p.current.incomeByCategory.isNotEmpty()) {
            append("This month's income: ").append(p.current.incomeByCategory.joinToString(", ") { "${Categories.label(it.categoryId)} ${Money.whole(it.amountMinor)}" }).append('\n')
        }
        if (p.current.topMerchants.isNotEmpty()) {
            append("Top merchants this month: ").append(p.current.topMerchants.take(6).joinToString(", ") { "${it.name} ${Money.whole(it.amountMinor)} (${it.count}x)" }).append('\n')
        }
        val previous = p.months.dropLast(1).lastOrNull { it.spentMinor > 0 }
        if (previous != null) {
            append("Last month by category: ").append(previous.byCategory.take(8).joinToString(", ") { "${Categories.label(it.categoryId)} ${Money.whole(it.amountMinor)}" }).append('\n')
        }
        if (p.recurring.isNotEmpty()) {
            append("Recurring payments: ").append(p.recurring.take(10).joinToString(", ") { "${it.name} ${Money.whole(it.amountMinor)}/month" }).append('\n')
        }
        append("Typical monthly living cost: ").append(Money.whole(p.essentialsMinor)).append('\n')
        append("Typical monthly surplus: ").append(Money.whole(p.averageLeftMinor)).append('\n')
        append("Bank balance last reported by SMS: ").append(p.bufferMinor?.let { Money.whole(it / 1000_00 * 1000_00) + " (rounded)" } ?: "unknown").append('\n')
        if (p.cardDueMinor > 0) append("Credit card bill due: ").append(Money.whole(p.cardDueMinor)).append('\n')
    }

    fun plan(plan: Plan): String = buildString {
        append("Stage: ").append(
            when (plan.stage) {
                Plan.Stage.BUILD_BUFFER -> "building a first safety buffer"
                Plan.Stage.STRENGTHEN_BUFFER -> "buffer started, not yet three months"
                Plan.Stage.GROW -> "buffer in place, tilt to growth"
            },
        ).append(". Buffer target ").append(Money.whole(plan.bufferTargetMinor)).append(".\n")
        append("Split of a ").append(Money.whole(plan.surplusMinor)).append(" monthly surplus:\n")
        plan.lines.forEach { append("- ").append(it.label).append(": ").append(Money.whole(it.amountMinor)).append(" – ").append(it.why).append('\n') }
        append("At 12% a year the equity lines would be worth about ").append(Money.compact(plan.tenYearMinor)).append(" after ten years (illustration, not a promise).\n")
    }

    private fun learning(done: Int): String {
        val next = LearningTrack.current(done) ?: return "All ${LearningTrack.steps.size} steps completed."
        return "Completed ${done} of ${LearningTrack.steps.size} steps. Next: \"${next.title}\" (${next.stake}). ${next.action} Teaches: ${next.lesson}"
    }

    /** Words describing the user's situation, added to the question so retrieval surfaces fitting notes. */
    internal fun autoTags(p: Position): String = buildString {
        if (p.cardDueMinor > 0 || p.current.byCategory.isNotEmpty()) append(" credit card")
        if (p.current.byCategory.firstOrNull()?.categoryId == Categories.FOOD) append(" food delivery")
        if (p.plan?.stage != Plan.Stage.GROW) append(" emergency buffer")
        if (p.current.investedMinor == 0L) append(" start sip index")
    }

    /** Ledger rows that mention what the question mentions: a category, a merchant, or simply the current month. */
    internal fun relevantTransactions(question: String, rows: List<TxnEntity>, today: LocalDate, zone: ZoneId): String {
        val q = question.lowercase()
        val visible = rows.filter { !it.excluded && it.type != TxnType.TRANSFER }
        val categoryHits = Categories.all.filter { c ->
            c.label.lowercase().split(' ', '&').filter { it.length > 3 }.any { it in q } || c.id in q
        }.map { it.id }.toSet()
        val merchantHits = visible.map { it.merchantName }.distinct().filter { name ->
            name.length >= 4 && name.lowercase().split(' ').first { it.isNotEmpty() }.let { it.length >= 4 && it in q }
        }.toSet()
        val start = Months.start(YearMonth.from(today), zone)
        val picked = when {
            merchantHits.isNotEmpty() -> visible.filter { it.merchantName in merchantHits }
            categoryHits.isNotEmpty() -> visible.filter { it.categoryId in categoryHits }
            else -> visible.filter { it.effectiveAt >= start }
        }.sortedByDescending { it.occurredAt }.take(60)
        return picked.joinToString("\n") { t ->
            val sign = if (t.type == TxnType.INCOME || t.type == TxnType.REFUND) "+" else "-"
            "${Months.dateOf(t.occurredAt, zone).format(dayLabel)} $sign${Money.whole(t.amountMinor)} ${t.merchantName} [${Categories.label(t.categoryId)}]"
        }
    }
}
