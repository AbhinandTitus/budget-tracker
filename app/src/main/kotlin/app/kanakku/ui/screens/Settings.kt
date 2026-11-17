package app.kanakku.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.kanakku.R
import app.kanakku.core.Money
import app.kanakku.core.Months
import app.kanakku.data.Prefs
import app.kanakku.ui.components.Chevron
import app.kanakku.ui.components.Chip
import app.kanakku.ui.components.Gutter
import app.kanakku.ui.components.Hairline
import app.kanakku.ui.components.IconButton
import app.kanakku.ui.components.ListRow
import app.kanakku.ui.components.Overline
import app.kanakku.ui.components.PrimaryButton
import app.kanakku.ui.components.QuietButton
import app.kanakku.ui.components.Toggle
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.Type

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter).padding(top = 26.dp)) {
        Overline(title, Modifier.padding(start = 4.dp, bottom = 10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ink.Surface)) { content() }
    }
}

@Composable
private fun Divider() = Hairline(Modifier.padding(start = 16.dp))

@Composable
private fun Value(text: String, accent: Boolean = false) {
    Text(text, style = Type.Body.copy(color = if (accent) Ink.Gold else Ink.Text2))
    Spacer(Modifier.width(6.dp))
    Chevron()
}

private enum class Dialog { NONE, SALARY, LIMIT, KEY, MODEL, RESCAN, WIPE }

@Composable
fun SettingsScreen(
    prefs: Prefs,
    scanning: Boolean,
    smsAllowed: Boolean,
    transactionCount: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onSalaryRange: (Long, Long) -> Unit,
    onShiftSalary: (Boolean) -> Unit,
    onMonthlyLimit: (Long) -> Unit,
    onSaveKey: (String, (Boolean, String, List<String>) -> Unit) -> Unit,
    onModel: (String) -> Unit,
    onLoadModels: ((List<String>) -> Unit) -> Unit,
    onAiCategorise: (Boolean) -> Unit,
    onSync: () -> Unit,
    onRescan: (Int) -> Unit,
    onGrantSms: () -> Unit,
    onExport: () -> Unit,
    onWipe: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    var dialog by remember { mutableStateOf(Dialog.NONE) }

    LazyColumn(Modifier.fillMaxSize().background(Ink.Bg), contentPadding = contentPadding) {
        item("top") {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = Gutter, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(R.drawable.ic_arrow_left, "Back", onBack)
                Spacer(Modifier.width(4.dp))
                Text("Settings", style = Type.Title)
            }
        }
        item("income") {
            Group("Income") {
                ListRow("Salary credit", subtitle = "A bank credit in this range is filed as salary", onClick = { dialog = Dialog.SALARY }) {
                    Value("${Money.compact(prefs.salaryMinMinor)} – ${Money.compact(prefs.salaryMaxMinor)}")
                }
                Divider()
                ListRow("Month-end salary", subtitle = "Salary on or after the ${Months.LATE_SALARY_DAY}th counts for the next month") {
                    Toggle(prefs.shiftLateSalary, onShiftSalary)
                }
                Divider()
                ListRow("Monthly spending limit", subtitle = "Measured against income when not set", onClick = { dialog = Dialog.LIMIT }) {
                    Value(if (prefs.monthlyLimitMinor > 0) Money.whole(prefs.monthlyLimitMinor) else "Not set")
                }
            }
        }
        item("gemini") {
            Group("Advisor") {
                ListRow(
                    "Gemini API key", icon = R.drawable.ic_key_round,
                    subtitle = if (prefs.hasGeminiKey) "Stored encrypted on this phone" else "Needed for advice and smarter categories",
                    onClick = { dialog = Dialog.KEY },
                ) { Value(if (prefs.hasGeminiKey) "Connected" else "Add", accent = !prefs.hasGeminiKey) }
                Divider()
                ListRow("Model", subtitle = "The default follows Google's newest Flash model", onClick = { dialog = Dialog.MODEL }) {
                    Value(if (prefs.geminiModel == Prefs.DEFAULT_MODEL) "Default" else prefs.geminiModel.removePrefix("gemini-").take(16))
                }
                Divider()
                ListRow("Categorise unknown merchants", subtitle = "Sends merchant names only") { Toggle(prefs.aiCategorise, onAiCategorise) }
            }
            Text(
                "With a key, the advisor sends Gemini your monthly totals, merchant names, amounts and dates. Message text, account and card numbers never leave the phone.",
                Modifier.padding(horizontal = Gutter + 4.dp).padding(top = 10.dp), style = Type.Caption,
            )
        }
        item("messages") {
            Group("Messages") {
                if (!smsAllowed) {
                    ListRow("Allow SMS access", icon = R.drawable.ic_message_square_text, subtitle = "Needed to read bank alerts", onClick = onGrantSms, titleColor = Ink.Gold) { Chevron() }
                    Divider()
                }
                ListRow(
                    if (scanning) "Reading messages…" else "Sync now", icon = R.drawable.ic_refresh_cw,
                    subtitle = if (prefs.lastScanAt > 0) "Last read ${app.kanakku.ui.components.friendlyDay(Months.dateOf(prefs.lastScanAt))}, ${app.kanakku.ui.components.clock(prefs.lastScanAt)}" else "Not read yet",
                    onClick = onSync,
                )
                Divider()
                ListRow("Read older messages", icon = R.drawable.ic_rotate_ccw_clock, subtitle = "Go further back in the inbox", onClick = { dialog = Dialog.RESCAN }) { Chevron() }
            }
            Text(
                "New alerts are picked up within seconds of arriving and again twice a day. One-time codes, PINs and passwords are skipped and never stored.",
                Modifier.padding(horizontal = Gutter + 4.dp).padding(top = 10.dp), style = Type.Caption,
            )
        }
        item("data") {
            Group("Data") {
                ListRow("Export as CSV", icon = R.drawable.ic_download, subtitle = "$transactionCount transactions", onClick = onExport) { Chevron() }
                Divider()
                ListRow("Delete everything", icon = R.drawable.ic_trash, onClick = { dialog = Dialog.WIPE }, titleColor = Ink.Bad)
            }
        }
        item("about") {
            Text(
                "Kanakku keeps everything on this phone. Nothing is uploaded or backed up, and there is no account.",
                Modifier.padding(horizontal = Gutter + 4.dp).padding(top = 24.dp, bottom = 24.dp), style = Type.Caption,
            )
        }
    }

    when (dialog) {
        Dialog.SALARY -> RangeSheet(prefs, { dialog = Dialog.NONE }, onSalaryRange)
        Dialog.LIMIT -> AmountSheet(
            "Monthly spending limit", "Leave empty to measure spending against income.", prefs.monthlyLimitMinor,
            { dialog = Dialog.NONE }, onMonthlyLimit,
        )
        Dialog.KEY -> KeySheet(prefs.hasGeminiKey, { dialog = Dialog.NONE }, onSaveKey, onOpenLink)
        Dialog.MODEL -> ModelSheet(prefs.geminiModel, prefs.hasGeminiKey, { dialog = Dialog.NONE }, onModel, onLoadModels)
        Dialog.RESCAN -> Sheet({ dialog = Dialog.NONE }) {
            Column(Modifier.padding(horizontal = Gutter).padding(bottom = 16.dp)) {
                Text("Read older messages", style = Type.Heading)
                Text("Existing transactions are kept; only missing ones are added.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = Type.Caption)
                listOf(3 to "Last 3 months", 6 to "Last 6 months", 12 to "Last year", 0 to "Everything in the inbox").forEach { (months, label) ->
                    QuietButton(label, { onRescan(months); dialog = Dialog.NONE }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
            }
        }
        Dialog.WIPE -> Sheet({ dialog = Dialog.NONE }) {
            Column(Modifier.padding(horizontal = Gutter).padding(bottom = 16.dp)) {
                Text("Delete everything?", style = Type.Heading)
                Text(
                    "Removes all transactions, the categories you have taught, and the advisor conversation. Your messages are not touched.",
                    Modifier.padding(top = 6.dp, bottom = 18.dp), style = Type.Body.copy(color = Ink.Text3),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuietButton("Cancel", { dialog = Dialog.NONE }, Modifier.weight(1f))
                    QuietButton("Delete", { onWipe(); dialog = Dialog.NONE }, Modifier.weight(1f), color = Ink.Bad)
                }
            }
        }
        Dialog.NONE -> Unit
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, number: Boolean = false, secret: Boolean = false, prefix: String? = null) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.SurfaceHi).padding(horizontal = 16.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        if (prefix != null) Text(prefix, style = Type.Body.copy(color = Ink.Text3), modifier = Modifier.padding(end = 6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = Type.Body.copy(color = Ink.Text4))
            BasicTextField(
                value, onChange, Modifier.fillMaxWidth(), textStyle = Type.Body, singleLine = true, cursorBrush = SolidColor(Ink.Gold),
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else if (secret) KeyboardType.Password else KeyboardType.Text),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            )
        }
    }
}

private fun digits(text: String) = text.filter { it.isDigit() }.take(8)

@Composable
private fun RangeSheet(prefs: Prefs, onDismiss: () -> Unit, onSave: (Long, Long) -> Unit) {
    var low by rememberSaveable { mutableStateOf((prefs.salaryMinMinor / 100).toString()) }
    var high by rememberSaveable { mutableStateOf((prefs.salaryMaxMinor / 100).toString()) }
    val min = low.toLongOrNull()
    val max = high.toLongOrNull()
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 16.dp)) {
            Text("Salary credit", style = Type.Heading)
            Text("A credit to your bank account between these amounts is filed as salary.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = Type.Caption)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(low, { low = digits(it) }, "From", Modifier.weight(1f), number = true, prefix = "₹")
                Field(high, { high = digits(it) }, "To", Modifier.weight(1f), number = true, prefix = "₹")
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Save", { onSave(min!! * 100, max!! * 100); onDismiss() }, Modifier.fillMaxWidth(), enabled = min != null && max != null && min > 0 && max >= min)
        }
    }
}

@Composable
private fun AmountSheet(title: String, hint: String, currentMinor: Long, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    var text by rememberSaveable { mutableStateOf(if (currentMinor > 0) (currentMinor / 100).toString() else "") }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 16.dp)) {
            Text(title, style = Type.Heading)
            Text(hint, Modifier.padding(top = 4.dp, bottom = 14.dp), style = Type.Caption)
            Field(text, { text = digits(it) }, "Amount", number = true, prefix = "₹")
            Spacer(Modifier.height(16.dp))
            PrimaryButton("Save", { onSave((text.toLongOrNull() ?: 0) * 100); onDismiss() }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun KeySheet(hasKey: Boolean, onDismiss: () -> Unit, onSave: (String, (Boolean, String, List<String>) -> Unit) -> Unit, onOpenLink: (String) -> Unit) {
    var key by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 16.dp)) {
            Text("Gemini API key", style = Type.Heading)
            Text(
                "Create a free key in Google AI Studio and paste it here. It is encrypted with this phone's keystore and used only to talk to Gemini.",
                Modifier.padding(top = 4.dp, bottom = 14.dp), style = Type.Caption,
            )
            Field(key, { key = it.trim(); status = null }, if (hasKey) "Paste a new key to replace the current one" else "Paste key", secret = true)
            status?.let { (ok, note) ->
                Text(note, Modifier.padding(top = 10.dp, start = 4.dp), style = Type.Caption.copy(color = if (ok) Ink.Good else Ink.Warn))
            }
            Spacer(Modifier.height(16.dp))
            PrimaryButton(
                if (busy) "Checking…" else "Save",
                {
                    busy = true
                    onSave(key) { ok, note, _ ->
                        busy = false
                        status = ok to note
                        if (ok) onDismiss()
                    }
                },
                Modifier.fillMaxWidth(), enabled = key.length >= 20 && !busy,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuietButton("Get a key", { onOpenLink("https://aistudio.google.com/apikey") }, Modifier.weight(1f), icon = R.drawable.ic_external_link)
                if (hasKey) QuietButton("Remove", { onSave("") { _, _, _ -> onDismiss() } }, Modifier.weight(1f), color = Ink.Bad)
            }
        }
    }
}

@Composable
private fun ModelSheet(current: String, hasKey: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit, onLoad: ((List<String>) -> Unit) -> Unit) {
    var models by remember { mutableStateOf<List<String>?>(null) }
    var custom by rememberSaveable { mutableStateOf(if (current == Prefs.DEFAULT_MODEL) "" else current) }
    androidx.compose.runtime.LaunchedEffect(hasKey) { if (hasKey) onLoad { models = it } else models = emptyList() }
    Sheet(onDismiss) {
        Column(Modifier.padding(horizontal = Gutter).padding(bottom = 16.dp)) {
            Text("Model", style = Type.Heading)
            Text("Flash models are fast and free-tier friendly. Pro models reason more deeply.", Modifier.padding(top = 4.dp, bottom = 14.dp), style = Type.Caption)
            val suggested = listOf(Prefs.DEFAULT_MODEL, "gemini-flash-lite-latest", "gemini-pro-latest")
            val listed = models.orEmpty().filter { it !in suggested && !it.contains("preview") && !it.contains("exp") }.takeLast(6)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (suggested + listed).forEach { m ->
                    Chip(if (m == Prefs.DEFAULT_MODEL) "Default (Flash)" else m.removePrefix("gemini-"), m == current, { onPick(m); onDismiss() })
                }
            }
            Spacer(Modifier.height(16.dp))
            Field(custom, { custom = it.trim() }, "Or type a model id")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("Use this model", { onPick(custom); onDismiss() }, Modifier.fillMaxWidth(), enabled = custom.startsWith("gemini"))
        }
    }
}

@Composable
fun OnboardingScreen(
    step: Int,
    scanning: Boolean,
    read: Int,
    found: Int,
    denied: Boolean,
    contentPadding: PaddingValues,
    onContinue: () -> Unit,
    onAllow: (monthsBack: Int) -> Unit,
    onOpenAppSettings: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit,
) {
    var months by rememberSaveable { mutableStateOf(6) }
    Column(Modifier.fillMaxSize().background(Ink.Bg).padding(contentPadding).padding(horizontal = 28.dp)) {
        Spacer(Modifier.weight(1f))
        when (step) {
            0 -> {
                Text("Your month,\nworked out for you.", style = Type.Title.copy(fontSize = Type.Hero.fontSize * 0.62f, lineHeight = Type.Hero.lineHeight * 0.72f))
                Spacer(Modifier.height(16.dp))
                Text(
                    "Kanakku reads the bank and card alerts already on this phone and keeps a running account of what came in, what went out and what is left.",
                    style = Type.Body.copy(color = Ink.Text2),
                )
                Spacer(Modifier.height(28.dp))
                Point(R.drawable.ic_shield_check, "Stays on this phone", "No account, no upload, no backup.")
                Point(R.drawable.ic_lock, "Skips anything secret", "One-time codes, PINs and passwords are never read into the app.")
                Point(R.drawable.ic_zap, "Runs on its own", "Wakes for a moment when an alert arrives. No background service.")
            }
            1 -> {
                Text("Allow access to messages", style = Type.Title)
                Spacer(Modifier.height(12.dp))
                Text("Only alerts from banks, cards and wallets are kept. Choose how far back to read.", style = Type.Body.copy(color = Ink.Text2))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3 to "3 months", 6 to "6 months", 12 to "1 year").forEach { (m, label) -> Chip(label, months == m, { months = m }) }
                }
                if (denied) {
                    Spacer(Modifier.height(24.dp))
                    Column(Modifier.clip(RoundedCornerShape(18.dp)).background(Ink.Surface).padding(16.dp)) {
                        Text("If Android blocked the request", style = Type.BodyStrong)
                        Text(
                            "Apps installed from a file start with SMS access restricted. Open app settings, tap the ⋮ menu at the top right, choose “Allow restricted settings”, then come back and allow again.",
                            Modifier.padding(top = 6.dp, bottom = 14.dp), style = Type.Caption.copy(color = Ink.Text2),
                        )
                        QuietButton("Open app settings", onOpenAppSettings, Modifier.fillMaxWidth(), icon = R.drawable.ic_external_link)
                    }
                }
            }
            else -> {
                Text(if (scanning) "Reading your messages" else "All set", style = Type.Title)
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        scanning -> "$read read · $found transactions"
                        found == 0 -> "No bank alerts found yet. New ones will be picked up as they arrive."
                        found == 1 -> "1 transaction found."
                        else -> "$found transactions found."
                    },
                    style = Type.Body.copy(color = Ink.Text2),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        when (step) {
            0 -> PrimaryButton("Continue", onContinue, Modifier.fillMaxWidth())
            1 -> {
                PrimaryButton("Allow", { onAllow(months) }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onSkip).padding(14.dp), contentAlignment = Alignment.Center) {
                    Text("Enter everything by hand instead", style = Type.Label.copy(color = Ink.Text3))
                }
            }
            else -> PrimaryButton("Open", onFinish, Modifier.fillMaxWidth(), enabled = !scanning)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Point(icon: Int, title: String, body: String) {
    Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        app.kanakku.ui.components.Glyph(icon, Ink.Gold, 20.dp, Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = Type.BodyStrong)
            Text(body, style = Type.Caption, modifier = Modifier.padding(top = 2.dp))
        }
    }
}
