package app.kanakku.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.kanakku.Graph
import app.kanakku.ai.GeminiException
import app.kanakku.ai.Position
import app.kanakku.core.Months
import app.kanakku.core.category.Categories
import app.kanakku.core.insight.MonthSummary
import app.kanakku.core.insight.Summaries
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.data.Prefs
import app.kanakku.data.ScanResult
import app.kanakku.data.db.ChatEntity
import app.kanakku.data.db.DueEntity
import app.kanakku.data.db.TxnEntity
import app.kanakku.data.sms.CategoriseWorker
import app.kanakku.data.sms.SmsPermission
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** What the month-based screens render. */
data class MonthView(
    val month: YearMonth,
    val today: LocalDate,
    val txns: List<TxnEntity> = emptyList(),
    val summary: MonthSummary,
    /** The six months ending at the current month, oldest first. */
    val history: List<MonthSummary> = emptyList(),
    val previous: MonthSummary? = null,
    val loaded: Boolean = false,
) {
    val isCurrent: Boolean get() = YearMonth.from(today) == month
    val isFuture: Boolean get() = month.isAfter(YearMonth.from(today))
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = Graph.get(app)
    private val repository = graph.repository
    private val settings = graph.settings
    private val advisor = graph.advisor
    private val zone = ZoneId.systemDefault()

    private fun today() = LocalDate.now(zone)

    val prefs: StateFlow<Prefs?> = settings.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _month = MutableStateFlow(YearMonth.from(today()))
    val month: StateFlow<YearMonth> = _month

    val view: StateFlow<MonthView> = _month.flatMapLatest { selected ->
        val now = YearMonth.from(today())
        val from = minOf(selected.minusMonths(1), now.minusMonths(5))
        val to = maxOf(selected, now)
        repository.observeRange(from, to).map { rows ->
            val start = Months.start(selected, zone)
            val end = Months.end(selected, zone)
            MonthView(
                month = selected,
                today = today(),
                txns = rows.filter { it.effectiveAt in start until end },
                summary = Summaries.of(selected, rows, zone),
                history = (5 downTo 0).map { Summaries.of(now.minusMonths(it.toLong()), rows, zone) },
                previous = Summaries.of(selected.minusMonths(1), rows, zone),
                loaded = true,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthView(_month.value, today(), summary = Summaries.of(_month.value, emptyList(), zone)))

    val dues: StateFlow<List<DueEntity>> = repository.observeDues().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val total: StateFlow<Int> = repository.observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val scanning: StateFlow<Boolean> = repository.scanning

    private val _scanProgress = MutableStateFlow(ScanResult())
    val scanProgress: StateFlow<ScanResult> = _scanProgress

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query
    val results: StateFlow<List<TxnEntity>?> = _query.debounce(180).flatMapLatest { q ->
        if (q.isBlank()) flowOf(null) else repository.search(q).map<List<TxnEntity>, List<TxnEntity>?> { it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val chat: StateFlow<List<ChatEntity>> = advisor.conversation().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val thinking: StateFlow<Boolean> = advisor.busy

    private val _position = MutableStateFlow<Position?>(null)
    val position: StateFlow<Position?> = _position

    private val _message = MutableStateFlow<String?>(null)
    /** A one-line notice for the user; cleared once shown. */
    val message: StateFlow<String?> = _message
    fun messageShown() { _message.value = null }

    init {
        viewModelScope.launch {
            combine(repository.observeCount(), settings.prefs) { count, _ -> count }.debounce(400).collect { refreshPosition() }
        }
    }

    private suspend fun refreshPosition() {
        _position.value = runCatching { advisor.position() }.getOrNull()
    }

    // ---- month navigation ----------------------------------------------------------------

    fun showMonth(month: YearMonth) { if (!month.isAfter(YearMonth.from(today()))) _month.value = month }
    fun previousMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() = showMonth(_month.value.plusMonths(1))
    fun setQuery(q: String) { _query.value = q }

    // ---- sync ----------------------------------------------------------------------------

    val hasSmsPermission: Boolean get() = SmsPermission.granted(getApplication())

    /** Reads anything new from the inbox. [monthsBack] re-reads that much history instead. */
    fun sync(monthsBack: Int? = null, announce: Boolean = false) {
        if (!hasSmsPermission) {
            if (announce) _message.value = "Allow SMS access to read bank alerts"
            return
        }
        viewModelScope.launch {
            val since = monthsBack?.let {
                if (it <= 0) 0L else today().minusMonths(it.toLong()).withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
            }
            _scanProgress.value = ScanResult()
            val result = runCatching { repository.scan(since) { _scanProgress.value = it } }.getOrNull()
            if (result != null && result.added > 0) CategoriseWorker.enqueue(getApplication())
            if (announce) {
                _message.value = when {
                    result == null -> "Could not read messages"
                    result.added == 0 -> "Up to date"
                    result.added == 1 -> "1 new transaction"
                    else -> "${result.added} new transactions"
                }
            }
        }
    }

    fun finishOnboarding() = viewModelScope.launch { settings.setOnboarded() }

    // ---- transactions --------------------------------------------------------------------

    fun addManual(amountMinor: Long, direction: Direction, categoryId: String, title: String, channel: Channel, at: Long, note: String) =
        viewModelScope.launch { repository.addManual(amountMinor, direction, categoryId, title, channel, at, note) }

    fun setCategory(txn: TxnEntity, categoryId: String, remember: Boolean) = viewModelScope.launch {
        repository.setCategory(txn, categoryId, remember)
        if (remember && txn.merchantKey != null) _message.value = "${txn.merchantName} will be filed under ${Categories.label(categoryId)}"
    }

    fun rename(txn: TxnEntity, name: String) = viewModelScope.launch { repository.renameMerchant(txn, name) }
    fun setNote(txn: TxnEntity, note: String) = viewModelScope.launch { repository.setNote(txn, note) }
    fun setExcluded(txn: TxnEntity, excluded: Boolean) = viewModelScope.launch { repository.setExcluded(txn, excluded) }
    fun updateManual(txn: TxnEntity) = viewModelScope.launch { repository.update(txn) }
    fun delete(txn: TxnEntity) = viewModelScope.launch { repository.delete(txn.id) }
    fun observe(id: Long) = repository.observe(id)

    // ---- settings ------------------------------------------------------------------------

    fun setSalaryRange(minMinor: Long, maxMinor: Long) = viewModelScope.launch { settings.setSalaryRange(minMinor, maxMinor) }
    fun setShiftLateSalary(on: Boolean) = viewModelScope.launch { settings.setShiftLateSalary(on) }
    fun setMonthlyLimit(minor: Long) = viewModelScope.launch { settings.setMonthlyLimit(minor) }
    fun setAiCategorise(on: Boolean) = viewModelScope.launch { settings.setAiCategorise(on) }
    fun setModel(model: String) = viewModelScope.launch { settings.setGeminiModel(model) }
    fun setLearnStep(step: Int) = viewModelScope.launch { settings.setLearnStep(step) }

    /** Verifies [key] with Gemini, then stores it. Reports the outcome through [done]. */
    fun saveGeminiKey(key: String, done: (ok: Boolean, note: String, models: List<String>) -> Unit) = viewModelScope.launch {
        if (key.isBlank()) {
            settings.setGeminiKey("")
            done(true, "Key removed", emptyList())
            return@launch
        }
        try {
            val models = advisor.verify(key)
            if (settings.setGeminiKey(key)) {
                done(true, "Connected", models)
                CategoriseWorker.enqueue(getApplication())
            } else {
                done(false, "This device could not store the key securely", models)
            }
        } catch (e: GeminiException) {
            done(false, e.userMessage, emptyList())
        } catch (e: Exception) {
            done(false, "Could not verify the key", emptyList())
        }
    }

    fun loadModels(done: (List<String>) -> Unit) = viewModelScope.launch { done(runCatching { advisor.models() }.getOrDefault(emptyList())) }

    fun exportCsv(done: (String) -> Unit) = viewModelScope.launch { done(repository.exportCsv()) }

    fun wipe() = viewModelScope.launch {
        repository.wipe()
        _message.value = "All data deleted"
    }

    // ---- advisor -------------------------------------------------------------------------

    fun ask(question: String) = viewModelScope.launch { advisor.ask(question) }
    fun clearChat() = viewModelScope.launch { advisor.clear() }
}
