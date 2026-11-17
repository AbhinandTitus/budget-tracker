package app.kanakku.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.kanakku.R
import app.kanakku.core.Money
import app.kanakku.core.Months
import app.kanakku.core.category.Categories
import app.kanakku.core.model.TxnType
import app.kanakku.data.db.TxnEntity
import app.kanakku.ui.categoryIcon
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.Type
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val Gutter = 20.dp

@Composable
fun Glyph(@DrawableRes icon: Int, tint: Color = Ink.Text2, size: Dp = 20.dp, modifier: Modifier = Modifier, description: String? = null) {
    Icon(painterResource(icon), description, modifier.size(size), tint)
}

/** A round, quietly tinted button for a single icon. */
@Composable
fun IconButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Ink.Text2, filled: Boolean = false) {
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) Ink.SurfaceHi else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(icon, tint, description = description) }
}

@Composable
fun CategoryBadge(categoryId: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Ink.SurfaceHi), contentAlignment = Alignment.Center) {
        Glyph(categoryIcon(categoryId), Ink.Text2, size * 0.46f)
    }
}

@Composable
fun Overline(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = Type.Overline)
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
        Overline(title, Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 4.dp),
                style = Type.Label.copy(color = Ink.Gold),
            )
        }
    }
}

/** A flat panel. Depth comes from tone, not shadow. */
@Composable
fun Panel(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: PaddingValues = PaddingValues(16.dp), content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(Ink.Surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) { content() }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Ink.Gold else Ink.SurfaceTop)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Type.BodyStrong.copy(color = if (enabled) Ink.OnGold else Ink.Text4)) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Ink.Text, @DrawableRes icon: Int? = null) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.SurfaceHi)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Glyph(icon, color, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = Type.BodyStrong.copy(color = color))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, @DrawableRes icon: Int? = null) {
    Row(
        modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) Ink.Text else Color.Transparent)
            .border(1.dp, if (selected) Color.Transparent else Ink.LineStrong, CircleShape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Glyph(icon, if (selected) Ink.Bg else Ink.Text2, 16.dp)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = Type.Label.copy(color = if (selected) Ink.Bg else Ink.Text2))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
}

/**
 * A ratio against a limit. The fill carries the state (within, close, over) and the track is a
 * dim step of the same colour.
 */
@Composable
fun Meter(fraction: Float, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val clamped = fraction.coerceIn(0f, 1f)
    val animated by animateFloatAsState(clamped, tween(600), label = "meter")
    val (fill, track) = when {
        fraction > 1f -> Ink.BadFill to Ink.TrackBad
        fraction >= 0.9f -> Ink.Warn to Ink.TrackWarn
        else -> Ink.MarkSpent to Ink.TrackSpent
    }
    Box(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        if (animated > 0f) Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(CircleShape).background(fill))
    }
}

/** A single-hue bar whose length compares this row with the largest. */
@Composable
fun RankBar(fraction: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "rank")
    Box(modifier.fillMaxWidth().height(4.dp)) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(animated.coerceAtLeast(0.012f))
                .clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(Ink.MarkSpent),
        )
    }
}

/** "₹17,240" with the rupee sign set smaller so the figure leads. */
@Composable
fun BigAmount(minor: Long, style: TextStyle = Type.Hero, modifier: Modifier = Modifier, color: Color = Ink.Text) {
    val text = buildAnnotatedString {
        if (minor < 0) append("−")
        withStyle(SpanStyle(fontSize = style.fontSize * 0.62f, color = Ink.Text3, letterSpacing = 0.em)) { append("₹") }
        append(Money.whole(kotlin.math.abs(minor), symbol = false))
    }
    Text(text, modifier, style = style.copy(color = color), maxLines = 1)
}

fun signedAmount(t: TxnEntity): String = when (t.type) {
    TxnType.INCOME, TxnType.REFUND -> "+" + Money.format(t.amountMinor)
    else -> Money.format(t.amountMinor)
}

fun amountColor(t: TxnEntity): Color = when {
    t.excluded || t.type == TxnType.TRANSFER -> Ink.Text3
    t.type == TxnType.INCOME || t.type == TxnType.REFUND -> Ink.Good
    else -> Ink.Text
}

private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

fun friendlyDay(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> if (date.year == today.year) date.format(weekdayDayMonth) else date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
}

fun shortDay(date: LocalDate): String = date.format(dayMonth)

fun clock(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { t: LocalTime -> t.format(time) }

@Composable
fun TxnRow(txn: TxnEntity, onClick: () -> Unit, modifier: Modifier = Modifier, showDate: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Gutter, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(txn.categoryId)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.merchantName, style = Type.BodyStrong.copy(color = if (txn.excluded) Ink.Text3 else Ink.Text), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val meta = buildString {
                append(
                    when {
                        txn.excluded -> "Not counted"
                        txn.type == TxnType.REFUND -> "Refund · ${Categories.label(txn.categoryId)}"
                        else -> Categories.label(txn.categoryId)
                    },
                )
                append(" · ")
                append(if (showDate) shortDay(Months.dateOf(txn.occurredAt)) else txn.accountLabel)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (txn.needsReview) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Ink.Gold))
                    Spacer(Modifier.width(6.dp))
                }
                Text(meta, style = Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(signedAmount(txn), style = Type.Amount.copy(color = amountColor(txn)), maxLines = 1)
    }
}

/** One line of a settings-style list. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Ink.Text,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 60.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Glyph(icon, Ink.Text3)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Type.Body.copy(color = titleColor))
            if (subtitle != null) Text(subtitle, style = Type.Caption, modifier = Modifier.padding(top = 2.dp))
        }
        trailing()
    }
}

/** An on/off switch drawn to match the rest of the interface. */
@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val position by animateFloatAsState(if (checked) 1f else 0f, tween(160), label = "toggle")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) Ink.Gold else Ink.SurfaceTop)
            .clickable(interaction, ripple(), role = Role.Switch) { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(Modifier.padding(start = 18.dp * position).size(22.dp).clip(CircleShape).background(if (checked) Ink.OnGold else Ink.Text3))
    }
}

@Composable
fun Chevron() = Glyph(R.drawable.ic_chevron_right, Ink.Text4, 18.dp)

@Composable
fun EmptyState(@DrawableRes icon: Int, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(Ink.Surface), contentAlignment = Alignment.Center) { Glyph(icon, Ink.Text3, 24.dp) }
        Spacer(Modifier.height(18.dp))
        Text(title, style = Type.Heading)
        Spacer(Modifier.height(6.dp))
        Text(body, style = Type.Caption.copy(fontSize = Type.Body.fontSize * 0.9f, lineHeight = Type.Body.lineHeight), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
