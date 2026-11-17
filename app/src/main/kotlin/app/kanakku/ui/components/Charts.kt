package app.kanakku.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import app.kanakku.core.Money
import app.kanakku.core.insight.MonthSummary
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.TNUM
import app.kanakku.ui.theme.Type
import java.time.YearMonth
import java.time.format.TextStyle as MonthStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

/** Rounds [value] up to a tidy axis maximum (1, 2, 2.5, 5 × 10ⁿ). */
internal fun niceCeiling(value: Long): Long {
    if (value <= 0) return 100_00
    val magnitude = 10.0.pow(kotlin.math.floor(log10(value.toDouble())))
    val scaled = value / magnitude
    val nice = when {
        scaled <= 1 -> 1.0
        scaled <= 2 -> 2.0
        scaled <= 2.5 -> 2.5
        scaled <= 5 -> 5.0
        scaled <= 7.5 -> 7.5
        else -> 10.0
    }
    return ceil(nice * magnitude).toLong()
}

@Composable
private fun LineKey(color: Color, label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 12.dp, height = 2.dp).background(color, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Type.Caption)
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Text(value, style = Type.Caption.copy(color = Ink.Text, fontFeatureSettings = TNUM))
        }
    }
}

@Composable
private fun BoxKey(color: Color, label: String, value: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = Type.Caption)
        if (value != null) {
            Spacer(Modifier.width(6.dp))
            Text(value, style = Type.Caption.copy(color = Ink.Text, fontFeatureSettings = TNUM))
        }
    }
}

/**
 * Running spend through the month against the month before. The current month is the subject;
 * the previous one is context, so it is drawn in grey. Touch and drag to read any day.
 */
@Composable
fun PaceChart(
    current: List<Long>,
    previous: List<Long>,
    throughDay: Int,
    modifier: Modifier = Modifier,
    currentLabel: String = "This month",
    previousLabel: String = "Last month",
) {
    val days = maxOf(current.size, previous.size, 28)
    val shown = current.take(throughDay.coerceIn(1, maxOf(current.size, 1)))
    var scrub by remember(current, previous) { mutableStateOf<Int?>(null) }
    val day = (scrub ?: shown.size).coerceIn(1, days)
    val measurer = rememberTextMeasurer()
    val top = niceCeiling(maxOf(shown.maxOrNull() ?: 0, previous.maxOrNull() ?: 0, 1))
    val hasPrevious = previous.any { it > 0 }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            LineKey(Ink.MarkSpent, currentLabel, shown.getOrNull(day - 1)?.let { Money.whole(it) })
            if (hasPrevious) LineKey(Ink.MarkMuted, previousLabel, previous.getOrNull(day - 1)?.let { Money.whole(it) })
            Spacer(Modifier.weight(1f))
            if (scrub != null) Text("Day $day", style = Type.Caption)
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(132.dp)
                .semantics { contentDescription = "Running spend by day. $currentLabel ${Money.whole(shown.lastOrNull() ?: 0)}." }
                .pointerInput(days) {
                    detectTapGestures(onPress = { offset ->
                        scrub = ((offset.x / size.width) * (days - 1)).roundToInt().plus(1).coerceIn(1, days)
                        tryAwaitRelease()
                        scrub = null
                    })
                }
                .pointerInput(days) {
                    detectDragGestures(
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                    ) { change, _ ->
                        scrub = ((change.position.x / size.width) * (days - 1)).roundToInt().plus(1).coerceIn(1, days)
                    }
                },
        ) {
            val axis = 18.dp.toPx()
            val plot = Rect(0f, 4.dp.toPx(), size.width, size.height - axis)
            fun x(d: Int) = plot.left + plot.width * (d - 1) / (days - 1).coerceAtLeast(1)
            fun y(v: Long) = plot.bottom - plot.height * (v.coerceAtLeast(0).toFloat() / top)

            // Recessive grid: baseline, midline and top, with the two values that anchor the scale.
            listOf(0L, top / 2, top).forEach { v ->
                drawLine(if (v == 0L) Ink.LineStrong else Ink.Line, Offset(plot.left, y(v)), Offset(plot.right, y(v)), 1.dp.toPx())
            }
            val tick = Type.Caption.copy(fontSize = Type.Overline.fontSize, color = Ink.Text4, fontFeatureSettings = TNUM)
            listOf(top / 2, top).forEach { v ->
                val label = measurer.measure(Money.compact(v), tick)
                // Cumulative lines start low on the left, so that corner is always clear.
                drawText(label, topLeft = Offset(plot.left, y(v) + 3.dp.toPx()))
            }
            listOf(1, (days + 1) / 2, days).forEach { d ->
                val label = measurer.measure(d.toString(), tick)
                val lx = (x(d) - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(lx, plot.bottom + 5.dp.toPx()))
            }

            fun path(values: List<Long>) = Path().apply {
                values.forEachIndexed { i, v -> if (i == 0) moveTo(x(1), y(v)) else lineTo(x(i + 1), y(v)) }
            }
            val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            if (hasPrevious) drawPath(path(previous), Ink.MarkMuted, style = stroke)
            if (shown.isNotEmpty()) {
                val line = path(shown)
                val area = Path().apply {
                    addPath(line)
                    lineTo(x(shown.size), plot.bottom)
                    lineTo(x(1), plot.bottom)
                    close()
                }
                drawPath(area, Ink.MarkSpent.copy(alpha = 0.10f))
                drawPath(line, Ink.MarkSpent, style = stroke)
                val end = Offset(x(shown.size), y(shown.last()))
                drawCircle(Ink.Surface, 6.dp.toPx(), end)
                drawCircle(Ink.MarkSpent, 4.dp.toPx(), end)
            }
            scrub?.let { d ->
                drawLine(Ink.Text3, Offset(x(d), plot.top), Offset(x(d), plot.bottom), 1.dp.toPx())
                shown.getOrNull(d - 1)?.let { v ->
                    drawCircle(Ink.Surface, 6.dp.toPx(), Offset(x(d), y(v)))
                    drawCircle(Ink.MarkSpent, 4.dp.toPx(), Offset(x(d), y(v)))
                }
                if (hasPrevious) previous.getOrNull(d - 1)?.let { v ->
                    drawCircle(Ink.Surface, 5.dp.toPx(), Offset(x(d), y(v)))
                    drawCircle(Ink.MarkMuted, 3.dp.toPx(), Offset(x(d), y(v)))
                }
            }
        }
    }
}

/** Money in and money out for recent months, side by side on one scale. Tap a month to select it. */
@Composable
fun MonthColumns(months: List<MonthSummary>, selected: YearMonth, onSelect: (YearMonth) -> Unit, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val top = niceCeiling(months.maxOfOrNull { maxOf(it.incomeMinor, it.spentMinor) } ?: 1)
    val focus = months.firstOrNull { it.month == selected } ?: months.lastOrNull()

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BoxKey(Ink.MarkIncome, "In", focus?.let { Money.whole(it.incomeMinor) })
            BoxKey(Ink.MarkSpent, "Out", focus?.let { Money.whole(it.spentMinor) })
        }
        Spacer(Modifier.height(14.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .semantics {
                    contentDescription = months.joinToString(". ") {
                        "${it.month.month.getDisplayName(MonthStyle.FULL, Locale.ENGLISH)}: in ${Money.whole(it.incomeMinor)}, out ${Money.whole(it.spentMinor)}"
                    }
                }
                .pointerInput(months) {
                    detectTapGestures { offset ->
                        if (months.isNotEmpty()) {
                            val slot = size.width / months.size.toFloat()
                            onSelect(months[(offset.x / slot).toInt().coerceIn(0, months.size - 1)].month)
                        }
                    }
                },
        ) {
            if (months.isEmpty()) return@Canvas
            val axis = 22.dp.toPx()
            val plot = Rect(0f, 4.dp.toPx(), size.width, size.height - axis)
            val slot = plot.width / months.size
            val bar = minOf(14.dp.toPx(), slot * 0.26f)
            val gap = 2.dp.toPx()
            val radius = CornerRadius(4.dp.toPx())
            fun h(v: Long) = plot.height * (v.coerceAtLeast(0).toFloat() / top)

            drawLine(Ink.Line, Offset(plot.left, plot.top), Offset(plot.right, plot.top), 1.dp.toPx())
            drawLine(Ink.Line, Offset(plot.left, plot.center.y), Offset(plot.right, plot.center.y), 1.dp.toPx())
            val tick = Type.Caption.copy(fontSize = Type.Overline.fontSize, color = Ink.Text4, fontFeatureSettings = TNUM)
            val topLabel = measurer.measure(Money.compact(top), tick)
            drawText(topLabel, topLeft = Offset(plot.right - topLabel.size.width, plot.top + 3.dp.toPx()))

            months.forEachIndexed { i, m ->
                val centre = plot.left + slot * (i + 0.5f)
                val isFocus = m.month == focus?.month
                val alpha = if (isFocus) 1f else 0.5f
                fun column(left: Float, value: Long, color: Color) {
                    val height = h(value)
                    if (height <= 0f) return
                    val path = Path().apply {
                        addRoundRect(
                            androidx.compose.ui.geometry.RoundRect(
                                Rect(left, plot.bottom - height, left + bar, plot.bottom),
                                topLeft = radius, topRight = radius, bottomLeft = CornerRadius.Zero, bottomRight = CornerRadius.Zero,
                            ),
                        )
                    }
                    drawPath(path, color.copy(alpha = alpha))
                }
                column(centre - gap / 2 - bar, m.incomeMinor, Ink.MarkIncome)
                column(centre + gap / 2, m.spentMinor, Ink.MarkSpent)
                val name = m.month.month.getDisplayName(MonthStyle.SHORT, Locale.ENGLISH)
                val label = measurer.measure(name, Type.Caption.copy(color = if (isFocus) Ink.Text else Ink.Text3))
                drawText(label, topLeft = Offset(centre - label.size.width / 2f, plot.bottom + 7.dp.toPx()))
            }
            drawLine(Ink.LineStrong, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), 1.dp.toPx())
        }
    }
}
