package app.kanakku

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.kanakku.data.Prefs
import app.kanakku.data.db.TxnEntity
import app.kanakku.data.sms.SmsPermission
import app.kanakku.ui.AppViewModel
import app.kanakku.ui.components.Glyph
import app.kanakku.ui.screens.ActivityFilter
import app.kanakku.ui.screens.ActivityScreen
import app.kanakku.ui.screens.AddEntrySheet
import app.kanakku.ui.screens.AdvisorScreen
import app.kanakku.ui.screens.HomeScreen
import app.kanakku.ui.screens.InsightsScreen
import app.kanakku.ui.screens.KeySheet
import app.kanakku.ui.screens.LearnScreen
import app.kanakku.ui.screens.OnboardingScreen
import app.kanakku.ui.screens.SettingsScreen
import app.kanakku.ui.screens.TxnSheet
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.KanakkuTheme
import app.kanakku.ui.theme.Type
import kotlinx.coroutines.delay
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { KanakkuTheme { Root() } }
    }
}

enum class Tab(val label: String, val icon: Int) {
    HOME("Home", R.drawable.ic_house),
    ACTIVITY("Activity", R.drawable.ic_receipt_text),
    INSIGHTS("Insights", R.drawable.ic_chart_no_axes_column),
    ADVISOR("Advisor", R.drawable.ic_message_circle),
}

private enum class Page { NONE, SETTINGS, LEARN }

@Composable
fun Root(model: AppViewModel = viewModel()) {
    val prefs by model.prefs.collectAsStateWithLifecycle()
    val current = prefs
    Box(Modifier.fillMaxSize().background(Ink.Bg)) {
        when {
            current == null -> Unit
            !current.onboarded -> Onboarding(model)
            else -> Main(model, current)
        }
    }
}

@Composable
private fun Onboarding(model: AppViewModel) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(0) }
    var denied by rememberSaveable { mutableStateOf(false) }
    var monthsBack by rememberSaveable { mutableStateOf(6) }
    val scanning by model.scanning.collectAsStateWithLifecycle()
    val progress by model.scanProgress.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (SmsPermission.granted(context)) {
            denied = false
            step = 2
            model.sync(monthsBack)
        } else {
            denied = true
        }
    }
    // Returning from system settings with access now allowed.
    LifecycleResumeEffect(step) {
        if (step == 1 && denied && SmsPermission.granted(context)) {
            denied = false
            step = 2
            model.sync(monthsBack)
        }
        onPauseOrDispose { }
    }
    BackHandler(step == 1) { step = 0 }

    OnboardingScreen(
        step = step,
        scanning = scanning,
        read = progress.read,
        found = progress.added,
        denied = denied,
        contentPadding = WindowInsets.statusBars.asPaddingValues().plus(WindowInsets.navigationBars.asPaddingValues()),
        onContinue = { step = 1 },
        onAllow = { months ->
            monthsBack = months
            launcher.launch(SmsPermission.required)
        },
        onOpenAppSettings = { context.openAppSettings() },
        onSkip = { model.finishOnboarding() },
        onFinish = { model.finishOnboarding() },
    )
}

private fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    top = calculateTopPadding() + other.calculateTopPadding(),
    bottom = calculateBottomPadding() + other.calculateBottomPadding(),
)

private fun android.content.Context.openAppSettings() {
    runCatching {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun android.content.Context.openLink(url: String) {
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun android.content.Context.shareCsv(csv: String) {
    runCatching {
        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "kanakku-transactions.csv").apply { writeText(csv) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Export").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun Main(model: AppViewModel, prefs: Prefs) {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var page by rememberSaveable { mutableStateOf(Page.NONE) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var addingKey by rememberSaveable { mutableStateOf(false) }
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var filter by rememberSaveable { mutableStateOf(ActivityFilter.ALL) }
    var draft by rememberSaveable { mutableStateOf("") }

    val view by model.view.collectAsStateWithLifecycle()
    val dues by model.dues.collectAsStateWithLifecycle()
    val scanning by model.scanning.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val results by model.results.collectAsStateWithLifecycle()
    val chat by model.chat.collectAsStateWithLifecycle()
    val thinking by model.thinking.collectAsStateWithLifecycle()
    val position by model.position.collectAsStateWithLifecycle()
    val total by model.total.collectAsStateWithLifecycle()
    val notice by model.message.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (SmsPermission.granted(context)) model.sync(6, announce = true) else context.openAppSettings()
    }
    // Catch up with anything that arrived while the app was closed.
    LifecycleResumeEffect(Unit) {
        model.sync()
        onPauseOrDispose { }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2800)
            model.messageShown()
        }
    }
    BackHandler(page != Page.NONE) { page = Page.NONE }
    BackHandler(page == Page.NONE && tab != Tab.HOME) { tab = Tab.HOME }

    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val barHeight = 64.dp
    val content = PaddingValues(top = top, bottom = bottom + barHeight + 12.dp)
    val holder = rememberSaveableStateHolder()

    Box(Modifier.fillMaxSize()) {
        Crossfade(tab, label = "tab") { shown ->
            holder.SaveableStateProvider(shown.name) {
                when (shown) {
                    Tab.HOME -> HomeScreen(
                        view, prefs, dues, scanning, content,
                        onPrevious = model::previousMonth, onNext = model::nextMonth,
                        onSync = { if (SmsPermission.granted(context)) model.sync(announce = true) else permission.launch(SmsPermission.required) },
                        onSettings = { page = Page.SETTINGS },
                        onOpen = { openId = it.id },
                        onSeeAll = { filter = ActivityFilter.ALL; tab = Tab.ACTIVITY },
                        onReview = { filter = ActivityFilter.REVIEW; tab = Tab.ACTIVITY },
                        onInsights = { tab = Tab.INSIGHTS },
                        onAdd = { adding = true },
                    )
                    Tab.ACTIVITY -> ActivityScreen(
                        view, query, results, filter, content,
                        onQuery = model::setQuery, onFilter = { filter = it },
                        onPrevious = model::previousMonth, onNext = model::nextMonth, onOpen = { openId = it.id },
                    )
                    Tab.INSIGHTS -> InsightsScreen(
                        view, position, content,
                        onPrevious = model::previousMonth, onNext = model::nextMonth, onMonth = model::showMonth,
                        onAskPlan = {
                            tab = Tab.ADVISOR
                            if (prefs.hasGeminiKey) model.ask("Walk me through the plan for my monthly surplus and exactly how to set each part up.")
                        },
                    )
                    Tab.ADVISOR -> AdvisorScreen(
                        chat, thinking, prefs.hasGeminiKey, prefs.learnStep,
                        PaddingValues(top = top, bottom = bottom + barHeight + 4.dp),
                        draft = draft, onDraft = { draft = it },
                        onSend = model::ask, onClear = model::clearChat,
                        onAddKey = { addingKey = true }, onLearn = { page = Page.LEARN },
                    )
                }
            }
        }

        BottomBar(tab, { tab = it }, { adding = true }, Modifier.align(Alignment.BottomCenter), bottom, barHeight)

        AnimatedVisibility(
            notice != null, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + barHeight + 16.dp),
            enter = fadeIn() + slideInVertically { it / 2 }, exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            Box(Modifier.padding(horizontal = 24.dp).clip(RoundedCornerShape(14.dp)).background(Ink.Text).padding(horizontal = 16.dp, vertical = 11.dp)) {
                Text(notice.orEmpty(), style = Type.Label.copy(color = Ink.Bg))
            }
        }

        AnimatedVisibility(page != Page.NONE, enter = slideInHorizontally { it } + fadeIn(), exit = slideOutHorizontally { it } + fadeOut()) {
            val pagePadding = PaddingValues(top = top, bottom = bottom + 12.dp)
            // Swallow touches so the screen underneath cannot be tapped through.
            Box(Modifier.fillMaxSize().background(Ink.Bg).clickable(remember { MutableInteractionSource() }, null) {}) {
                when (page) {
                    Page.LEARN -> LearnScreen(
                        prefs.learnStep, pagePadding, onBack = { page = Page.NONE }, onSetDone = model::setLearnStep,
                        onAsk = { question ->
                            page = Page.NONE
                            tab = Tab.ADVISOR
                            if (prefs.hasGeminiKey) model.ask(question) else addingKey = true
                        },
                    )
                    else -> SettingsScreen(
                        prefs, scanning, SmsPermission.granted(context), total, pagePadding,
                        onBack = { page = Page.NONE },
                        onSalaryRange = model::setSalaryRange,
                        onShiftSalary = model::setShiftLateSalary,
                        onMonthlyLimit = model::setMonthlyLimit,
                        onSaveKey = { key, done -> model.saveGeminiKey(key, done) },
                        onModel = model::setModel,
                        onLoadModels = { done -> model.loadModels(done) },
                        onAiCategorise = model::setAiCategorise,
                        onSync = { if (SmsPermission.granted(context)) model.sync(announce = true) else permission.launch(SmsPermission.required) },
                        onRescan = { months -> if (SmsPermission.granted(context)) model.sync(months, announce = true) else permission.launch(SmsPermission.required) },
                        onGrantSms = { permission.launch(SmsPermission.required) },
                        onExport = { model.exportCsv { csv -> context.shareCsv(csv) } },
                        onWipe = model::wipe,
                        onOpenLink = { context.openLink(it) },
                    )
                }
            }
        }
    }

    if (adding) AddEntrySheet({ adding = false }) { amount, direction, category, title, channel, at, note ->
        model.addManual(amount, direction, category, title, channel, at, note)
    }
    if (addingKey) KeySheet(prefs.hasGeminiKey, { addingKey = false }, { key, done -> model.saveGeminiKey(key, done) }, { context.openLink(it) })
    openId?.let { id -> OpenTxn(model, id) { openId = null } }
}

@Composable
private fun OpenTxn(model: AppViewModel, id: Long, onClose: () -> Unit) {
    val flow = remember(id) { model.observe(id) }
    val txn by flow.collectAsState(initial = null)
    var seen by remember(id) { mutableStateOf<TxnEntity?>(null) }
    txn?.let { seen = it }
    val shown = seen ?: return
    TxnSheet(
        shown, onClose,
        onCategory = { category, rememberChoice -> model.setCategory(shown, category, rememberChoice) },
        onRename = { model.rename(shown, it) },
        onNote = { model.setNote(shown, it) },
        onExcluded = { model.setExcluded(shown, it) },
        onDelete = { model.delete(shown) },
    )
}

@Composable
fun BottomBar(selected: Tab, onSelect: (Tab) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Column(modifier.fillMaxWidth().background(Ink.Bg)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Ink.Line))
        Row(Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEachIndexed { index, tab ->
                if (index == 2) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Ink.Gold).clickable(role = Role.Button, onClick = onAdd),
                            contentAlignment = Alignment.Center,
                        ) { Glyph(R.drawable.ic_plus, Ink.OnGold, 22.dp, description = "Add entry") }
                    }
                }
                val active = tab == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { onSelect(tab) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Glyph(tab.icon, if (active) Ink.Text else Ink.Text4, 22.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(tab.label, style = Type.Overline.copy(letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified, color = if (active) Ink.Text else Ink.Text4))
                }
            }
        }
        Spacer(Modifier.height(inset))
    }
}
