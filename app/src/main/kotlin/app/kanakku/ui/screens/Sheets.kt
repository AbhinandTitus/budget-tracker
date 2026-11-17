package app.kanakku.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kanakku.R
import app.kanakku.core.Money
import app.kanakku.core.Months
import app.kanakku.core.category.Categories
import app.kanakku.core.category.Categorizer
import app.kanakku.core.category.Category
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.Source
import app.kanakku.core.model.TxnType
import app.kanakku.data.db.TxnEntity
import app.kanakku.ui.categoryIcon
import app.kanakku.ui.channelLabel
import app.kanakku.ui.components.BigAmount
import app.kanakku.ui.components.CategoryBadge
import app.kanakku.ui.components.Chevron
import app.kanakku.ui.components.Chip
import app.kanakku.ui.components.Glyph
import app.kanakku.ui.components.Gutter
import app.kanakku.ui.components.Hairline
import app.kanakku.ui.components.IconButton
import app.kanakku.ui.components.ListRow
import app.kanakku.ui.components.PrimaryButton
import app.kanakku.ui.components.QuietButton
import app.kanakku.ui.components.Toggle
import app.kanakku.ui.components.clock
import app.kanakku.ui.components.friendlyDay
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.Type
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ink.Surface,
        contentColor = Ink.Text,
        scrimColor = Ink.Bg.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Ink.LineStrong))
        },
    ) { Box(Modifier.navigationBarsPadding()) { content() } }
}

@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(Ink.SurfaceHi).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).height(38.dp).clip(RoundedCornerShape(11.dp)).background(if (i == selected) Ink.SurfaceTop else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = Type.Label.copy(color = if (i == selected) Ink.Text else Ink.Text3)) }
        }
    }
}

@Composable
fun CategoryTile(category: Category, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(role = Role.RadioButton, onClick = onClick).padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(if (selected) Ink.Gold else Ink.SurfaceHi),
            contentAlignment = Alignment.Center,
        ) { Glyph(categoryIcon(category.id), if (selected) Ink.OnGold else Ink.Text2, 21.dp) }
        Spacer(Modifier.height(6.dp))
        Text(
            category.label, style = Type.Caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp, color = if (selected) Ink.Text else Ink.Text3),
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun Keypad(onKey: (Char) -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val keys = listOf("123", "456", "789", ".0<")
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)).background(Ink.SurfaceHi)
                            .clickable(role = Role.Button) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onKey(key)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == '<') Glyph(R.drawable.ic_delete, Ink.Text2, 22.dp, description = "Delete")
                        else Text(key.toString(), style = Type.Figure.copy(fontSize = 22.sp))
                    }
                }
            }
        }
    }
}

/** Applies one keypad press to the amount being typed. */
internal fun typeAmount(current: String, key: Char): String = when {
    key == '<' -> current.dropLast(1)
    key == '.' -> if ('.' in current) current else if (current.isEmpty()) "0." else "$current."
    current == "0" -> key.toString()
    '.' in current && current.substringAfter('.').length >= 2 -> current
    current.substringBefore('.').length >= 8 && '.' !in current -> current
    else -> current + key
}

private fun displayTyped(typed: String): String {
    if (typed.isEmpty()) return "0"
    val whole = typed.substringBefore('.')
    val grouped = Money.group(whole.toLongOrNull() ?: 0)
    return if ('.' in typed) grouped + "." + typed.substringAfter('.') else grouped
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntrySheet(
    onDismiss: () -> Unit,
    onSave: (amountMinor: Long, direction: Direction, categoryId: String, title: String, channel: Channel, at: Long, note: String) -> Unit,
) {
    var received by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Categories.FOOD) }
    var channel by rememberSaveable { mutableStateOf(Channel.CASH) }
    var dayOffset by rememberSaveable { mutableStateOf(0L) }
    var pickedDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf(false) }

    val today = LocalDate.now()
    val date = pickedDay?.let(LocalDate::ofEpochDay) ?: today.minusDays(dayOffset)
    val choices = if (received) Categories.income else Categories.expense
    if (choices.none { it.id == category }) category = choices.first().id
    val amount = Money.parse(typed) ?: 0

    Sheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 12.dp)) {
            Segmented(listOf("Spent", "Received"), if (received) 1 else 0, { received = it == 1 }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text("₹", style = Type.Hero.copy(fontSize = 28.sp, color = Ink.Text3), modifier = Modifier.padding(bottom = 6.dp, end = 4.dp))
                Text(displayTyped(typed), style = Type.Hero.copy(fontSize = 46.sp, color = if (typed.isEmpty()) Ink.Text4 else Ink.Text), maxLines = 1)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Ink.SurfaceHi).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                if (title.isEmpty()) Text(if (received) "From whom or what" else "What was it for", style = Type.Body.copy(color = Ink.Text4))
                BasicTextField(
                    title, { title = it.take(40) }, Modifier.fillMaxWidth(), textStyle = Type.Body, singleLine = true, cursorBrush = SolidColor(Ink.Gold),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                items(choices, key = { it.id }) { c -> CategoryTile(c, c.id == category, { category = c.id }) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip(friendlyDay(date, today), false, { picking = true }, icon = R.drawable.ic_calendar)
                listOf(Channel.CASH, Channel.UPI, Channel.CARD, Channel.BANK_TRANSFER).forEach { c ->
                    Chip(if (c == Channel.BANK_TRANSFER) "Bank" else channelLabel(c), channel == c, { channel = c })
                }
            }
            Spacer(Modifier.height(14.dp))
            Keypad({ typed = typeAmount(typed, it) })
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                "Save", {
                    val at = date.atTime(if (date == today) LocalTime.now() else LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onSave(amount, if (received) Direction.IN else Direction.OUT, category, title, channel, at, "")
                    onDismiss()
                },
                Modifier.fillMaxWidth(), enabled = amount > 0,
            )
        }
    }

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis() + 86_400_000L
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    state.selectedDateMillis?.let { pickedDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().coerceAtMost(today).toEpochDay() }
                    picking = false
                }) { Text("Done", style = Type.Label.copy(color = Ink.Gold)) }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Cancel", style = Type.Label) } },
        ) { DatePicker(state, showModeToggle = false) }
    }
}

@Composable
private fun InlineField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, single: Boolean = true) {
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.SurfaceHi).padding(horizontal = 16.dp, vertical = 14.dp), contentAlignment = Alignment.CenterStart) {
        if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Ink.Text4))
        BasicTextField(
            value, onChange, Modifier.fillMaxWidth(), textStyle = Type.Body, singleLine = single, maxLines = if (single) 1 else 4,
            cursorBrush = SolidColor(Ink.Gold), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
}

private enum class DetailPage { MAIN, CATEGORY, RENAME }

@Composable
fun TxnSheet(
    txn: TxnEntity,
    onDismiss: () -> Unit,
    onCategory: (categoryId: String, remember: Boolean) -> Unit,
    onRename: (String) -> Unit,
    onNote: (String) -> Unit,
    onExcluded: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    var page by remember { mutableStateOf(DetailPage.MAIN) }
    var rememberChoice by rememberSaveable { mutableStateOf(true) }
    var note by rememberSaveable(txn.id) { mutableStateOf(txn.note.orEmpty()) }
    var name by rememberSaveable(txn.id) { mutableStateOf(txn.merchantName) }
    var showMessage by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val date = Months.dateOf(txn.occurredAt)

    Sheet({
        if (note.trim() != txn.note.orEmpty()) onNote(note)
        onDismiss()
    }) {
        AnimatedContent(page, label = "detail") { current ->
            when (current) {
                DetailPage.MAIN -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(6.dp))
                        CategoryBadge(txn.categoryId, 52.dp)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.clip(RoundedCornerShape(10.dp)).clickable { page = DetailPage.RENAME }.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(txn.merchantName, style = Type.Heading, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(6.dp))
                            Glyph(R.drawable.ic_pencil, Ink.Text4, 14.dp, description = "Rename")
                        }
                        Spacer(Modifier.height(4.dp))
                        val prefix = if (txn.type == TxnType.INCOME || txn.type == TxnType.REFUND) "+" else ""
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (prefix.isNotEmpty()) Text(prefix, style = Type.Hero.copy(fontSize = 40.sp, color = Ink.Good))
                            BigAmount(
                                txn.amountMinor, Type.Hero.copy(fontSize = 40.sp),
                                color = if (prefix.isNotEmpty()) Ink.Good else Ink.Text,
                            )
                        }
                        val paise = txn.amountMinor % 100
                        Text(
                            buildString {
                                if (paise != 0L) append(Money.format(txn.amountMinor)).append(" · ")
                                append(friendlyDay(date)).append(" · ").append(clock(txn.occurredAt))
                            },
                            style = Type.Caption, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.padding(horizontal = Gutter).clip(RoundedCornerShape(18.dp)).background(Ink.SurfaceHi)) {
                        ListRow("Category", subtitle = if (txn.type == TxnType.REFUND) "Refund – lowers this category's total" else null, onClick = { page = DetailPage.CATEGORY }) {
                            Text(Categories.label(txn.categoryId), style = Type.Body.copy(color = Ink.Gold))
                            Spacer(Modifier.width(6.dp))
                            Chevron()
                        }
                        Hairline(Modifier.padding(start = 16.dp))
                        ListRow("Paid with") { Text("${txn.accountLabel} · ${channelLabel(txn.channel)}", style = Type.Body.copy(color = Ink.Text2), maxLines = 1) }
                        if (txn.effectiveAt != txn.occurredAt) {
                            Hairline(Modifier.padding(start = 16.dp))
                            ListRow("Counts towards") {
                                Text(Months.of(txn.effectiveAt).month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH), style = Type.Body.copy(color = Ink.Text2))
                            }
                        }
                        Hairline(Modifier.padding(start = 16.dp))
                        ListRow("Leave out of totals", subtitle = "For money that isn't really yours to count") { Toggle(txn.excluded, onExcluded) }
                    }
                    Spacer(Modifier.height(12.dp))
                    InlineField(note, { note = it.take(200) }, "Add a note", Modifier.padding(horizontal = Gutter), single = false)
                    if (txn.smsBody != null) {
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.padding(horizontal = Gutter).clip(RoundedCornerShape(18.dp)).background(Ink.SurfaceHi)) {
                            ListRow("Original message", icon = R.drawable.ic_message_square_text, onClick = { showMessage = !showMessage }) {
                                Glyph(if (showMessage) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down, Ink.Text4, 18.dp)
                            }
                            if (showMessage) {
                                Text(txn.smsBody, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), style = Type.Caption.copy(color = Ink.Text2, lineHeight = 19.sp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (confirmDelete) {
                        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuietButton("Keep", { confirmDelete = false }, Modifier.weight(1f))
                            QuietButton("Delete", { onDelete(); onDismiss() }, Modifier.weight(1f), color = Ink.Bad)
                        }
                    } else {
                        QuietButton("Delete", { confirmDelete = true }, Modifier.padding(horizontal = Gutter).fillMaxWidth(), color = Ink.Bad, icon = R.drawable.ic_trash)
                    }
                }

                DetailPage.CATEGORY -> Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.padding(start = 8.dp, end = Gutter), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(R.drawable.ic_arrow_left, "Back", { page = DetailPage.MAIN })
                        Text("Category", style = Type.Heading)
                    }
                    LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxWidth().height(380.dp).padding(horizontal = 12.dp)) {
                        items(Categorizer.choicesFor(txn.direction), key = { it.id }) { c ->
                            CategoryTile(c, c.id == txn.categoryId, {
                                onCategory(c.id, rememberChoice && txn.merchantKey != null)
                                page = DetailPage.MAIN
                            })
                        }
                    }
                    if (txn.merchantKey != null && txn.source == Source.SMS) {
                        Row(Modifier.padding(horizontal = Gutter).padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(Ink.SurfaceHi).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Always use for ${txn.merchantName}", style = Type.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("Applies to past and future payments", style = Type.Caption)
                            }
                            Toggle(rememberChoice, { rememberChoice = it })
                        }
                    }
                }

                DetailPage.RENAME -> Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(Modifier.padding(start = 8.dp, end = Gutter), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(R.drawable.ic_arrow_left, "Back", { page = DetailPage.MAIN })
                        Text("Name", style = Type.Heading)
                    }
                    Spacer(Modifier.height(8.dp))
                    InlineField(name, { name = it.take(40) }, "Name", Modifier.padding(horizontal = Gutter))
                    Text("Used everywhere this merchant appears.", Modifier.padding(horizontal = Gutter).padding(top = 8.dp, bottom = 16.dp), style = Type.Caption)
                    PrimaryButton("Save", { onRename(name); page = DetailPage.MAIN }, Modifier.padding(horizontal = Gutter).fillMaxWidth(), enabled = name.isNotBlank())
                }
            }
        }
    }
}
