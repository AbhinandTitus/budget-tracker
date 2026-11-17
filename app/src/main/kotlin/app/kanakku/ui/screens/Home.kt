package app.kanakku.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kanakku.R
import app.kanakku.core.Money
import app.kanakku.core.category.Categories
import app.kanakku.core.insight.CategoryTotal
import app.kanakku.data.Prefs
import app.kanakku.data.db.DueEntity
import app.kanakku.data.db.TxnEntity
import app.kanakku.ui.MonthView
import app.kanakku.ui.components.BigAmount
import app.kanakku.ui.components.CategoryBadge
import app.kanakku.ui.components.Chevron
import app.kanakku.ui.components.EmptyState
import app.kanakku.ui.components.Glyph
import app.kanakku.ui.components.Gutter
import app.kanakku.ui.components.IconButton
import app.kanakku.ui.components.Meter
import app.kanakku.ui.components.Overline
import app.kanakku.ui.components.Panel
import app.kanakku.ui.components.QuietButton
import app.kanakku.ui.components.RankBar
import app.kanakku.ui.components.SectionHeader
import app.kanakku.ui.components.TxnRow
import app.kanakku.ui.components.shortDay
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.TNUM
import app.kanakku.ui.theme.Type
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun MonthSwitcher(view: MonthView, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = Gutter, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(view.month.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH), style = Type.Title)
            if (view.month.year != view.today.year) Text(view.month.year.toString(), style = Type.Caption)
        }
        IconButton(R.drawable.ic_chevron_left, "Previous month", onPrevious)
        IconButton(R.drawable.ic_chevron_right, "Next month", onNext, tint = if (view.isCurrent) Ink.Text4 else Ink.Text2)
        trailing()
    }
}

@Composable
private fun SyncButton(scanning: Boolean, onClick: () -> Unit) {
    val spin = rememberInfiniteTransition(label = "sync")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    IconButton(R.drawable.ic_refresh_cw, "Read new messages", onClick, Modifier.rotate(if (scanning) angle else 0f), tint = if (scanning) Ink.Gold else Ink.Text2)
}

@Composable
fun HomeScreen(
    view: MonthView,
    prefs: Prefs,
    dues: List<DueEntity>,
    scanning: Boolean,
    contentPadding: PaddingValues,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSync: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (TxnEntity) -> Unit,
    onSeeAll: () -> Unit,
    onReview: () -> Unit,
    onInsights: () -> Unit,
    onAdd: () -> Unit,
) {
    val summary = view.summary
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item("top") {
            MonthSwitcher(view, onPrevious, onNext) {
                SyncButton(scanning, onSync)
                IconButton(R.drawable.ic_settings, "Settings", onSettings)
            }
        }
        item("hero") { Hero(view, prefs) }

        if (view.loaded && summary.count == 0) {
            item("empty") {
                EmptyState(
                    R.drawable.ic_inbox,
                    if (view.isCurrent) "Nothing recorded yet" else "No activity this month",
                    "Bank and card alerts appear here as they arrive. You can also add an entry yourself.",
                ) { QuietButton("Add entry", onAdd, icon = R.drawable.ic_plus) }
            }
            return@LazyColumn
        }

        val upcoming = dues.filter { it.dueDay >= view.today.toEpochDay() }.groupBy { it.label }.map { it.value.maxBy { d -> d.seenAt } }
        if (view.isCurrent && upcoming.isNotEmpty()) {
            items(upcoming, key = { "due${it.id}" }) { DueCard(it, view.today) }
        }
        if (summary.reviewCount > 0) {
            item("review") { ReviewCard(summary.reviewCount, onReview) }
        }

        if (summary.byCategory.isNotEmpty()) {
            item("catHeader") {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Where it went", action = "Insights", onAction = onInsights)
                Spacer(Modifier.height(6.dp))
            }
            val top = summary.byCategory.filter { it.amountMinor > 0 }.take(5)
            val largest = top.maxOfOrNull { it.amountMinor } ?: 1
            items(top, key = { "cat${it.categoryId}" }) { CategoryRow(it, largest, summary.spentMinor) }
        }

        if (view.txns.isNotEmpty()) {
            item("recentHeader") {
                Spacer(Modifier.height(28.dp))
                SectionHeader("Recent", action = "See all", onAction = onSeeAll)
                Spacer(Modifier.height(2.dp))
            }
            items(view.txns.take(6), key = { "t${it.id}" }) { t -> TxnRow(t, { onOpen(t) }, showDate = true) }
        }
        item("end") { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Hero(view: MonthView, prefs: Prefs) {
    val s = view.summary
    val limit = if (prefs.monthlyLimitMinor > 0) prefs.monthlyLimitMinor else s.incomeMinor
    val daysInMonth = view.month.lengthOfMonth()
    val daysLeft = if (view.isCurrent) daysInMonth - view.today.dayOfMonth + 1 else 0

    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 26.dp)) {
        when {
            limit <= 0 -> {
                Overline(if (view.isCurrent) "Spent so far" else "Spent")
                Spacer(Modifier.height(6.dp))
                BigAmount(s.spentMinor)
                Spacer(Modifier.height(10.dp))
                Text(if (view.isCurrent) "No income recorded for this month yet" else "No income recorded", style = Type.Caption)
            }
            else -> {
                val left = limit - s.spentMinor
                val overline = when {
                    left < 0 -> "Over by"
                    view.isCurrent -> "Left to spend"
                    else -> "Left over"
                }
                Overline(overline)
                Spacer(Modifier.height(6.dp))
                BigAmount(kotlin.math.abs(left), color = if (left < 0) Ink.Bad else Ink.Text)
                Spacer(Modifier.height(4.dp))
                val basis = if (prefs.monthlyLimitMinor > 0) "of a ${Money.whole(limit)} limit" else "of ${Money.whole(limit)} income"
                Text(basis, style = Type.Body.copy(color = Ink.Text3))
                Spacer(Modifier.height(18.dp))
                val fraction = s.spentMinor.toFloat() / limit
                Meter(fraction)
                Spacer(Modifier.height(10.dp))
                val percent = (fraction * 100).toInt().coerceAtLeast(0)
                val note = buildString {
                    append("$percent% spent")
                    if (view.isCurrent) {
                        append(" · ").append(if (daysLeft == 1) "last day" else "$daysLeft days left")
                        if (left > 0 && daysLeft > 0) append(" · ").append(Money.whole(left / daysLeft)).append(" a day")
                    }
                }
                Text(note, style = Type.Caption)
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("In", s.incomeMinor, R.drawable.ic_arrow_down_left, Modifier.weight(1f))
            StatTile(
                "Out", s.spentMinor, R.drawable.ic_arrow_up_right, Modifier.weight(1f),
                note = if (s.investedMinor > 0) "${Money.whole(s.investedMinor)} invested" else null,
            )
        }
    }
}

@Composable
private fun StatTile(label: String, minor: Long, icon: Int, modifier: Modifier = Modifier, note: String? = null) {
    Panel(modifier, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Glyph(icon, Ink.Text3, 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(label, style = Type.Caption)
            }
            Spacer(Modifier.height(6.dp))
            BigAmount(minor, Type.Figure)
            Text(note ?: " ", style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DueCard(due: DueEntity, today: LocalDate) {
    val date = LocalDate.ofEpochDay(due.dueDay)
    val days = (due.dueDay - today.toEpochDay()).toInt()
    val urgent = days <= 3
    Panel(Modifier.padding(horizontal = Gutter).padding(top = 12.dp).fillMaxWidth(), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.SurfaceHi), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_credit_card, if (urgent) Ink.Warn else Ink.Text2)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${due.label} bill", style = Type.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val whenText = when (days) {
                    0 -> "Due today"
                    1 -> "Due tomorrow"
                    else -> "Due ${shortDay(date)} · in $days days"
                }
                Text(whenText, style = Type.Caption.copy(color = if (urgent) Ink.Warn else Ink.Text3))
            }
            Text(Money.format(due.totalMinor), style = Type.Amount)
        }
    }
}

@Composable
private fun ReviewCard(count: Int, onClick: () -> Unit) {
    Panel(Modifier.padding(horizontal = Gutter).padding(top = 12.dp).fillMaxWidth(), onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(Ink.Gold))
            Spacer(Modifier.width(12.dp))
            Text(if (count == 1) "1 payment needs a category" else "$count payments need a category", Modifier.weight(1f), style = Type.Body)
            Chevron()
        }
    }
}

@Composable
fun CategoryRow(total: CategoryTotal, largest: Long, spent: Long, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(total.categoryId)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Categories.label(total.categoryId), Modifier.weight(1f), style = Type.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.whole(total.amountMinor), style = Type.Amount)
            }
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RankBar(total.amountMinor.toFloat() / largest.coerceAtLeast(1), Modifier.weight(1f).clip(RoundedCornerShape(2.dp)).background(Ink.SurfaceHi))
                Spacer(Modifier.width(10.dp))
                val share = if (spent > 0) (total.amountMinor * 100 / spent).toInt() else 0
                Text("$share%", Modifier.width(34.dp), style = Type.Caption.copy(fontFeatureSettings = TNUM), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}
