package app.kanakku.core.insight

import app.kanakku.core.Money
import app.kanakku.core.Months
import app.kanakku.core.category.Categories
import app.kanakku.core.model.TxnType
import app.kanakku.data.db.TxnEntity
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.abs

data class CategoryTotal(val categoryId: String, val amountMinor: Long, val count: Int)

data class MerchantTotal(val name: String, val categoryId: String, val amountMinor: Long, val count: Int)

data class Recurring(val name: String, val categoryId: String, val amountMinor: Long, val months: Int)

/** Everything the screens show about one calendar month. Amounts are paise. */
data class MonthSummary(
    val month: YearMonth,
    val incomeMinor: Long,
    /** Outflow net of refunds, including money put into investments. */
    val spentMinor: Long,
    val investedMinor: Long,
    val byCategory: List<CategoryTotal>,
    val incomeByCategory: List<CategoryTotal>,
    val topMerchants: List<MerchantTotal>,
    /** Running spend at the end of each day; index 0 is the 1st. */
    val cumulativeByDay: List<Long>,
    val count: Int,
    val reviewCount: Int,
) {
    val leftMinor: Long get() = incomeMinor - spentMinor

    /** Spending excluding money that was saved rather than consumed. */
    val consumedMinor: Long get() = spentMinor - investedMinor

    /** Share of income not consumed, 0..1; null when there is no income to measure against. */
    val savingsRate: Double? get() = if (incomeMinor > 0) (incomeMinor - consumedMinor).toDouble() / incomeMinor else null
}

object Summaries {

    fun of(month: YearMonth, all: List<TxnEntity>, zone: ZoneId = ZoneId.systemDefault()): MonthSummary {
        val start = Months.start(month, zone)
        val end = Months.end(month, zone)
        val rows = all.filter { !it.excluded && it.effectiveAt >= start && it.effectiveAt < end }

        val spendByCategory = HashMap<String, Long>()
        val spendCount = HashMap<String, Int>()
        val incomeByCategory = HashMap<String, Long>()
        val incomeCount = HashMap<String, Int>()
        val merchants = HashMap<String, MerchantTotal>()
        val perDay = LongArray(month.lengthOfMonth())

        for (t in rows) {
            when (t.type) {
                TxnType.EXPENSE, TxnType.REFUND -> {
                    val signed = if (t.type == TxnType.EXPENSE) t.amountMinor else -t.amountMinor
                    spendByCategory.merge(t.categoryId, signed, Long::plus)
                    if (t.type == TxnType.EXPENSE) spendCount.merge(t.categoryId, 1, Int::plus)
                    val day = Months.dateOf(t.effectiveAt, zone).dayOfMonth
                    perDay[day - 1] += signed
                    if (t.categoryId != Categories.PEOPLE || t.merchantKey != null) {
                        val key = t.merchantKey ?: t.merchantName
                        val prev = merchants[key]
                        merchants[key] = MerchantTotal(
                            t.merchantName, t.categoryId, (prev?.amountMinor ?: 0) + signed,
                            (prev?.count ?: 0) + if (t.type == TxnType.EXPENSE) 1 else 0,
                        )
                    }
                }
                TxnType.INCOME -> {
                    incomeByCategory.merge(t.categoryId, t.amountMinor, Long::plus)
                    incomeCount.merge(t.categoryId, 1, Int::plus)
                }
                TxnType.TRANSFER -> Unit
            }
        }

        var running = 0L
        val cumulative = perDay.map { running += it; running }
        val categories = spendByCategory.map { (id, amount) -> CategoryTotal(id, amount, spendCount[id] ?: 0) }
            .filter { it.amountMinor != 0L }
            .sortedByDescending { it.amountMinor }
        val spent = categories.sumOf { it.amountMinor }
        val invested = categories.filter { Categories.byId(it.categoryId).saving }.sumOf { it.amountMinor }

        return MonthSummary(
            month = month,
            incomeMinor = incomeByCategory.values.sum(),
            spentMinor = spent,
            investedMinor = invested,
            byCategory = categories,
            incomeByCategory = incomeByCategory.map { (id, amount) -> CategoryTotal(id, amount, incomeCount[id] ?: 0) }.sortedByDescending { it.amountMinor },
            topMerchants = merchants.values.filter { it.amountMinor > 0 }.sortedByDescending { it.amountMinor }.take(8),
            cumulativeByDay = cumulative,
            count = rows.count { it.type != TxnType.TRANSFER },
            reviewCount = rows.count { it.needsReview },
        )
    }

    /** Payments that repeat month after month for about the same amount: rent, subscriptions, SIPs. */
    fun recurring(all: List<TxnEntity>, zone: ZoneId = ZoneId.systemDefault()): List<Recurring> {
        val byMerchant = all.filter { it.type == TxnType.EXPENSE && !it.excluded && it.merchantKey != null }.groupBy { it.merchantKey!! }
        val out = ArrayList<Recurring>()
        for ((_, list) in byMerchant) {
            val perMonth = list.groupBy { Months.of(it.effectiveAt, zone) }
            if (perMonth.size < 2) continue
            val typical = perMonth.values.map { month -> month.maxOf { it.amountMinor } }.sorted()
            val median = typical[typical.size / 2]
            val steady = typical.count { abs(it - median) <= median / 8 }
            val singleEachMonth = perMonth.values.count { it.size <= 2 } >= perMonth.size - 1
            if (steady >= 2 && steady >= perMonth.size - 1 && singleEachMonth && median >= 50_00) {
                out += Recurring(list.first().merchantName, list.maxBy { it.occurredAt }.categoryId, median, perMonth.size)
            }
        }
        return out.sortedByDescending { it.amountMinor }
    }

    /** Short factual observations about the current month against the ones before it. */
    fun observations(current: MonthSummary, history: List<MonthSummary>, today: LocalDate): List<String> {
        val notes = ArrayList<String>()
        val isCurrentMonth = YearMonth.from(today) == current.month
        val day = if (isCurrentMonth) today.dayOfMonth else current.month.lengthOfMonth()
        val previous = history.lastOrNull { it.month == current.month.minusMonths(1) }

        if (previous != null && previous.cumulativeByDay.isNotEmpty() && current.spentMinor > 0) {
            val then = previous.cumulativeByDay[minOf(day, previous.cumulativeByDay.size) - 1]
            val now = current.cumulativeByDay[day - 1]
            val diff = now - then
            if (then > 0 && abs(diff) > then / 10) {
                notes += if (diff > 0) "${Money.whole(diff)} ahead of last month's spending by this date"
                else "${Money.whole(-diff)} under last month's spending by this date"
            }
        }

        val past = history.filter { it.month.isBefore(current.month) && it.spentMinor > 0 }.takeLast(3)
        if (past.isNotEmpty()) {
            current.byCategory.take(6).forEach { c ->
                val average = past.sumOf { p -> p.byCategory.firstOrNull { it.categoryId == c.categoryId }?.amountMinor ?: 0 } / past.size
                if (average >= 500_00 && c.amountMinor > average * 13 / 10 && notes.size < 4) {
                    notes += "${Categories.label(c.categoryId)} is ${Money.whole(c.amountMinor - average)} above your recent monthly average"
                }
            }
        }

        current.byCategory.firstOrNull()?.let { top ->
            if (current.spentMinor > 0 && top.amountMinor * 100 / current.spentMinor >= 35) {
                notes += "${Categories.label(top.categoryId)} makes up ${top.amountMinor * 100 / current.spentMinor}% of spending"
            }
        }
        current.savingsRate?.let { rate ->
            if (!isCurrentMonth) notes += "You kept ${(rate * 100).toInt().coerceAtLeast(0)}% of what came in"
        }
        return notes.take(4)
    }
}
