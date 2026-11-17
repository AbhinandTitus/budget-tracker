package app.kanakku.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kanakku.R
import app.kanakku.ai.Plan
import app.kanakku.ai.Position
import app.kanakku.core.Money
import app.kanakku.core.category.Categories
import app.kanakku.core.insight.Recurring
import app.kanakku.core.insight.Summaries
import app.kanakku.ui.MonthView
import app.kanakku.ui.components.CategoryBadge
import app.kanakku.ui.components.EmptyState
import app.kanakku.ui.components.Gutter
import app.kanakku.ui.components.Hairline
import app.kanakku.ui.components.MonthColumns
import app.kanakku.ui.components.PaceChart
import app.kanakku.ui.components.Panel
import app.kanakku.ui.components.QuietButton
import app.kanakku.ui.components.SectionHeader
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.Type
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(
    view: MonthView,
    position: Position?,
    contentPadding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onMonth: (YearMonth) -> Unit,
    onAskPlan: () -> Unit,
) {
    val s = view.summary
    val hasAnything = view.history.any { it.count > 0 } || s.count > 0
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item("top") { MonthSwitcher(view, onPrevious, onNext) }
        if (view.loaded && !hasAnything) {
            item("empty") { EmptyState(R.drawable.ic_chart_no_axes_column, "Nothing to chart yet", "Trends appear once a few transactions have been recorded.") }
            return@LazyColumn
        }

        item("columns") {
            Spacer(Modifier.height(20.dp))
            SectionHeader("Six months")
            Spacer(Modifier.height(12.dp))
            Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth()) { MonthColumns(view.history, view.month, onMonth) }
        }

        if (s.spentMinor > 0) {
            item("pace") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Spending through the month")
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth()) {
                    PaceChart(
                        current = s.cumulativeByDay,
                        previous = view.previous?.cumulativeByDay.orEmpty(),
                        throughDay = if (view.isCurrent) view.today.dayOfMonth else s.cumulativeByDay.size,
                        currentLabel = view.month.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        previousLabel = view.month.minusMonths(1).month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    )
                }
            }
        }

        val notes = Summaries.observations(s, view.history, view.today)
        if (notes.isNotEmpty()) {
            item("notes") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Worth knowing")
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                    Column {
                        notes.forEachIndexed { i, note ->
                            if (i > 0) Hairline()
                            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(Ink.Gold))
                                Spacer(Modifier.width(12.dp))
                                Text(note, style = Type.Body)
                            }
                        }
                    }
                }
            }
        }

        position?.plan?.takeIf { view.isCurrent }?.let { plan ->
            item("plan") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Plan for what's left")
                Spacer(Modifier.height(12.dp))
                PlanCard(plan, onAskPlan)
            }
        }

        val spending = s.byCategory.filter { it.amountMinor > 0 }
        if (spending.isNotEmpty()) {
            item("catHeader") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("By category")
                Spacer(Modifier.height(6.dp))
            }
            val largest = spending.maxOf { it.amountMinor }
            items(spending, key = { "c${it.categoryId}" }) { CategoryRow(it, largest, s.spentMinor) }
        }

        if (s.incomeByCategory.isNotEmpty()) {
            item("income") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Money in")
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    Column {
                        s.incomeByCategory.forEachIndexed { i, c ->
                            if (i > 0) Hairline()
                            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryBadge(c.categoryId, 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(Categories.label(c.categoryId), Modifier.weight(1f), style = Type.Body)
                                Text(Money.whole(c.amountMinor), style = Type.Amount.copy(color = Ink.Good))
                            }
                        }
                    }
                }
            }
        }

        if (s.topMerchants.isNotEmpty()) {
            item("merchants") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Top merchants")
                Spacer(Modifier.height(12.dp))
                Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    Column {
                        s.topMerchants.take(6).forEachIndexed { i, m ->
                            if (i > 0) Hairline()
                            Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.name, style = Type.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(if (m.count == 1) "1 payment" else "${m.count} payments", style = Type.Caption)
                                }
                                Text(Money.whole(m.amountMinor), style = Type.Amount)
                            }
                        }
                    }
                }
            }
        }

        position?.recurring?.takeIf { it.isNotEmpty() }?.let { list ->
            item("recurring") {
                Spacer(Modifier.height(24.dp))
                SectionHeader("Repeats every month")
                Spacer(Modifier.height(12.dp))
                RecurringPanel(list.take(8))
            }
        }
        item("end") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun RecurringPanel(list: List<Recurring>) {
    Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth(), padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            list.forEachIndexed { i, r ->
                if (i > 0) Hairline()
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(r.categoryId, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(r.name, Modifier.weight(1f), style = Type.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.whole(r.amountMinor), style = Type.Amount)
                }
            }
            Hairline()
            Row(Modifier.padding(vertical = 12.dp)) {
                Text("Together each month", Modifier.weight(1f), style = Type.Caption)
                Text(Money.whole(list.sumOf { it.amountMinor }), style = Type.Amount)
            }
        }
    }
}

@Composable
fun PlanCard(plan: Plan, onAsk: () -> Unit, modifier: Modifier = Modifier) {
    Panel(modifier.padding(horizontal = Gutter).fillMaxWidth()) {
        Column {
            Text("${Money.whole(plan.surplusMinor)} a month", style = Type.Figure)
            Text(
                when (plan.stage) {
                    Plan.Stage.BUILD_BUFFER -> "Your typical surplus. Build a safety buffer first, and start investing alongside it."
                    Plan.Stage.STRENGTHEN_BUFFER -> "Your typical surplus. The buffer has started; more can go to growth."
                    Plan.Stage.GROW -> "Your typical surplus. The buffer is in place, so most of it can work for growth."
                },
                Modifier.padding(top = 4.dp), style = Type.Caption,
            )
            Spacer(Modifier.height(14.dp))
            plan.lines.forEach { line ->
                Hairline()
                Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(line.label, style = Type.BodyStrong)
                        Text(line.why, style = Type.Caption, modifier = Modifier.padding(top = 2.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(Money.whole(line.amountMinor), style = Type.Amount)
                }
            }
            Hairline()
            Text(
                "Invested monthly at 12% a year, the equity part would be about ${Money.compact(plan.tenYearMinor)} in ten years. Markets vary; this is an illustration.",
                Modifier.padding(top = 12.dp, bottom = 14.dp), style = Type.Caption,
            )
            QuietButton("Ask about this plan", onAsk, Modifier.fillMaxWidth(), color = Ink.Gold, icon = R.drawable.ic_message_circle)
        }
    }
}
