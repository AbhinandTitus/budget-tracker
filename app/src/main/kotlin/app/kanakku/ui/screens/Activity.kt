package app.kanakku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.kanakku.R
import app.kanakku.core.Money
import app.kanakku.core.Months
import app.kanakku.core.model.TxnType
import app.kanakku.data.db.TxnEntity
import app.kanakku.ui.MonthView
import app.kanakku.ui.components.Chip
import app.kanakku.ui.components.EmptyState
import app.kanakku.ui.components.Glyph
import app.kanakku.ui.components.Gutter
import app.kanakku.ui.components.IconButton
import app.kanakku.ui.components.TxnRow
import app.kanakku.ui.components.friendlyDay
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.TNUM
import app.kanakku.ui.theme.Type

enum class ActivityFilter(val label: String) { ALL("All"), OUT("Spent"), IN("Received"), REVIEW("To review") }

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(Ink.Surface).padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(R.drawable.ic_search, Ink.Text3, 18.dp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Ink.Text4))
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(),
                textStyle = Type.Body, singleLine = true, cursorBrush = SolidColor(Ink.Gold),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
        }
        if (value.isNotEmpty()) IconButton(R.drawable.ic_x, "Clear", { onChange("") }, tint = Ink.Text3)
    }
}

@Composable
fun ActivityScreen(
    view: MonthView,
    query: String,
    results: List<TxnEntity>?,
    filter: ActivityFilter,
    contentPadding: PaddingValues,
    onQuery: (String) -> Unit,
    onFilter: (ActivityFilter) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpen: (TxnEntity) -> Unit,
) {
    val searching = query.isNotBlank()
    val source = if (searching) results.orEmpty() else view.txns
    val rows = source.filter {
        when (filter) {
            ActivityFilter.ALL -> true
            ActivityFilter.OUT -> it.type == TxnType.EXPENSE
            ActivityFilter.IN -> it.type == TxnType.INCOME || it.type == TxnType.REFUND
            ActivityFilter.REVIEW -> it.needsReview
        }
    }
    val byDay = rows.groupBy { Months.dateOf(it.occurredAt) }.toSortedMap(compareByDescending { it })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item("top") {
            MonthSwitcher(view, onPrevious, onNext)
            Spacer(Modifier.height(14.dp))
            SearchField(query, onQuery, "Search all months", Modifier.padding(horizontal = Gutter))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActivityFilter.entries.forEach { f -> Chip(f.label, filter == f, { onFilter(f) }) }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (rows.isEmpty()) {
            item("empty") {
                EmptyState(
                    R.drawable.ic_receipt_text,
                    when {
                        searching -> "No matches"
                        filter == ActivityFilter.REVIEW -> "Nothing to review"
                        else -> "No activity"
                    },
                    when {
                        searching -> "Try a merchant name, a note or an account."
                        filter == ActivityFilter.REVIEW -> "Every payment this month has a category."
                        else -> "Transactions for this month will be listed here."
                    },
                )
            }
        }
        byDay.forEach { (date, list) ->
            item("d$date") {
                val net = list.filter { !it.excluded }.sumOf {
                    when (it.type) {
                        TxnType.EXPENSE -> -it.amountMinor
                        TxnType.INCOME, TxnType.REFUND -> it.amountMinor
                        TxnType.TRANSFER -> 0L
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(friendlyDay(date, view.today), Modifier.weight(1f), style = Type.Label)
                    if (net != 0L) Text((if (net > 0) "+" else "−") + Money.whole(kotlin.math.abs(net)), style = Type.Caption.copy(fontFeatureSettings = TNUM))
                }
            }
            items(list, key = { it.id }) { t -> TxnRow(t, { onOpen(t) }, showDate = searching) }
        }
        item("end") { Spacer(Modifier.height(12.dp)) }
    }
}
