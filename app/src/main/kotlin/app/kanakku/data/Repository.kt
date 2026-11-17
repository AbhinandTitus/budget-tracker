package app.kanakku.data

import androidx.room.withTransaction
import app.kanakku.core.Months
import app.kanakku.core.category.Categories
import app.kanakku.core.category.Categorizer
import app.kanakku.core.category.CategorizerConfig
import app.kanakku.core.category.LearnedRule
import app.kanakku.core.category.Merchants
import app.kanakku.core.model.AccountKind
import app.kanakku.core.model.CategorySource
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.Source
import app.kanakku.core.model.TxnType
import app.kanakku.core.sms.SmsParser
import app.kanakku.core.sms.SmsVerdict
import app.kanakku.data.db.AppDatabase
import app.kanakku.data.db.DueEntity
import app.kanakku.data.db.RuleEntity
import app.kanakku.data.db.TxnEntity
import app.kanakku.data.sms.SmsSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

data class ScanResult(val read: Int = 0, val added: Int = 0, val secretsSkipped: Int = 0)

data class AccountBalance(val label: String, val kind: AccountKind, val balanceMinor: Long, val asOf: Long)

/** Single entry point for everything the app stores. */
class Repository(
    private val db: AppDatabase,
    private val settings: Settings,
    private val sms: SmsSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    private val txns = db.txns()
    private val scanLock = Mutex()
    private var rules: Map<String, LearnedRule>? = null

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    // ---- reading -------------------------------------------------------------------------

    fun observeMonth(month: YearMonth): Flow<List<TxnEntity>> = txns.observeBetween(Months.start(month, zone()), Months.end(month, zone()))

    fun observeRange(from: YearMonth, toInclusive: YearMonth): Flow<List<TxnEntity>> =
        txns.observeBetween(Months.start(from, zone()), Months.end(toInclusive, zone()))

    suspend fun range(from: YearMonth, toInclusive: YearMonth): List<TxnEntity> =
        txns.between(Months.start(from, zone()), Months.end(toInclusive, zone()))

    fun observe(id: Long): Flow<TxnEntity?> = txns.observe(id)
    fun search(query: String): Flow<List<TxnEntity>> = txns.search(query.trim())
    fun observeCount(): Flow<Int> = txns.observeCount()
    suspend fun earliestMonth(): YearMonth? = txns.earliest()?.let { Months.of(it, zone()) }

    fun observeDues(): Flow<List<DueEntity>> = db.dues().observeFrom(LocalDate.now(zone()).toEpochDay() - 2)

    /** Latest balance each account has reported, newest first. */
    suspend fun balances(): List<AccountBalance> = txns.recentWithBalance()
        .groupBy { it.accountKey }
        .map { (_, list) -> list.maxBy { it.occurredAt } }
        .filter { it.accountKind == AccountKind.BANK || it.accountKind == AccountKind.DEBIT_CARD || it.accountKind == AccountKind.WALLET }
        .map { AccountBalance(it.accountLabel, it.accountKind, it.balanceMinor ?: 0, it.occurredAt) }
        .sortedByDescending { it.asOf }

    // ---- ingestion -----------------------------------------------------------------------

    private suspend fun ruleMap(): Map<String, LearnedRule> = rules ?: db.rules().all()
        .associate { it.merchantKey to LearnedRule(it.categoryId, it.displayName, it.fromAi) }
        .also { rules = it }

    /**
     * Reads the inbox from [since] (default: a little before the previous scan) and stores any
     * transactions found. Safe to call repeatedly and concurrently; duplicates are ignored.
     */
    suspend fun scan(since: Long? = null, onProgress: (ScanResult) -> Unit = {}): ScanResult = withContext(Dispatchers.IO) {
        scanLock.withLock {
            _scanning.value = true
            try {
                val prefs = settings.current()
                val started = clock()
                val from = since ?: (prefs.lastScanAt - OVERLAP_MS).coerceAtLeast(0)
                val config = CategorizerConfig(prefs.salaryMinMinor, prefs.salaryMaxMinor)
                val ruleBook = ruleMap()
                var result = ScanResult()
                for (message in sms.read(from)) {
                    result = result.copy(read = result.read + 1)
                    if (SmsParser.mightBeFinancial(message.body)) {
                        when (ingest(message.sender, message.body, message.date, config, prefs.shiftLateSalary, ruleBook)) {
                            Outcome.ADDED -> result = result.copy(added = result.added + 1)
                            Outcome.SECRET -> result = result.copy(secretsSkipped = result.secretsSkipped + 1)
                            Outcome.SKIPPED -> Unit
                        }
                    }
                    if (result.read % 200 == 0) onProgress(result)
                }
                settings.setLastScan(started)
                onProgress(result)
                result
            } finally {
                _scanning.value = false
            }
        }
    }

    private enum class Outcome { ADDED, SECRET, SKIPPED }

    private suspend fun ingest(
        sender: String,
        body: String,
        date: Long,
        config: CategorizerConfig,
        shiftLateSalary: Boolean,
        ruleBook: Map<String, LearnedRule>,
    ): Outcome {
        val verdict = SmsParser.parse(sender, body, Months.dateOf(date, zone()))
        when (verdict) {
            SmsVerdict.Sensitive -> return Outcome.SECRET
            is SmsVerdict.Ignored -> return Outcome.SKIPPED
            is SmsVerdict.Due -> {
                val due = verdict.due
                val label = Categorizer.accountLabel(
                    app.kanakku.core.sms.ParsedTxn(
                        0, Direction.OUT, app.kanakku.core.model.Nature.GENERIC, Channel.CARD, AccountKind.CREDIT_CARD,
                        due.accountLast4, due.institution, null, null, null, null, null,
                    ),
                )
                db.dues().upsert(DueEntity(label = label, totalMinor = due.totalMinor, minimumMinor = due.minimumMinor, dueDay = due.dueDate.toEpochDay(), seenAt = date))
                return Outcome.SKIPPED
            }
            is SmsVerdict.Transaction -> Unit
        }
        val parsed = verdict.txn
        val hash = hash(sender, body)
        val accountKey = Categorizer.accountKey(parsed)
        if (parsed.refNo != null && txns.countSameReference(parsed.refNo, parsed.amountMinor, parsed.direction, date) > 0) return Outcome.SKIPPED
        if (txns.countNearTwin(parsed.amountMinor, parsed.direction, accountKey, date, parsed.refNo) > 0) return Outcome.SKIPPED

        val classified = Categorizer.classify(parsed, config) { ruleBook[it] }
        val effective = if (classified.type == TxnType.INCOME && classified.categoryId == Categories.SALARY) {
            Months.salaryEffective(date, shiftLateSalary, zone())
        } else {
            date
        }
        val id = txns.insert(
            TxnEntity(
                amountMinor = parsed.amountMinor,
                direction = parsed.direction,
                type = classified.type,
                categoryId = classified.categoryId,
                merchantKey = classified.merchantKey,
                merchantName = classified.merchantName,
                accountKey = accountKey,
                accountLabel = Categorizer.accountLabel(parsed),
                accountKind = parsed.accountKind,
                channel = parsed.channel,
                occurredAt = date,
                effectiveAt = effective,
                source = Source.SMS,
                smsSender = sender,
                smsBody = SmsParser.normalise(body),
                smsHash = hash,
                refNo = parsed.refNo,
                balanceMinor = parsed.balanceMinor,
                categorySource = classified.source,
                needsReview = classified.needsReview,
                createdAt = clock(),
            ),
        )
        return if (id > 0) Outcome.ADDED else Outcome.SKIPPED
    }

    private fun hash(sender: String, body: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$sender|${SmsParser.normalise(body)}".toByteArray())
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    // ---- editing -------------------------------------------------------------------------

    suspend fun addManual(
        amountMinor: Long,
        direction: Direction,
        categoryId: String,
        title: String,
        channel: Channel,
        occurredAt: Long,
        note: String?,
    ): Long {
        val name = title.trim().ifEmpty { Categories.label(categoryId) }
        val type = Categorizer.typeFor(direction, categoryId)
        val prefs = settings.current()
        val kind = when (channel) {
            Channel.CASH -> AccountKind.CASH
            Channel.CARD -> AccountKind.CREDIT_CARD
            Channel.WALLET -> AccountKind.WALLET
            else -> AccountKind.BANK
        }
        val label = when (channel) {
            Channel.CASH -> "Cash"
            Channel.CARD -> "Card"
            Channel.UPI -> "UPI"
            Channel.WALLET -> "Wallet"
            else -> "Bank"
        }
        return txns.insert(
            TxnEntity(
                amountMinor = amountMinor,
                direction = direction,
                type = type,
                categoryId = categoryId,
                merchantKey = if (title.isBlank()) null else Merchants.keyOf(title),
                merchantName = name,
                accountKey = "manual:${channel.name}",
                accountLabel = label,
                accountKind = kind,
                channel = channel,
                occurredAt = occurredAt,
                effectiveAt = if (type == TxnType.INCOME && categoryId == Categories.SALARY) Months.salaryEffective(occurredAt, prefs.shiftLateSalary, zone()) else occurredAt,
                note = note?.trim()?.ifEmpty { null },
                source = Source.MANUAL,
                categorySource = CategorySource.USER,
                createdAt = clock(),
            ),
        )
    }

    suspend fun update(txn: TxnEntity) = txns.update(txn)
    suspend fun byId(id: Long): TxnEntity? = txns.byId(id)
    suspend fun delete(id: Long) = txns.delete(id)
    suspend fun restore(txn: TxnEntity) { txns.insert(txn) }

    /**
     * Files [txn] under [categoryId]. With [remember], the choice becomes a rule for the merchant
     * and is applied to its other transactions, past and future.
     */
    suspend fun setCategory(txn: TxnEntity, categoryId: String, remember: Boolean) {
        val type = Categorizer.typeFor(txn.direction, categoryId)
        val prefs = settings.current()
        val effective = if (type == TxnType.INCOME && categoryId == Categories.SALARY) {
            Months.salaryEffective(txn.occurredAt, prefs.shiftLateSalary, zone())
        } else {
            txn.occurredAt
        }
        db.withTransaction {
            txns.update(txn.copy(categoryId = categoryId, type = type, categorySource = CategorySource.USER, needsReview = false, effectiveAt = effective))
            val key = txn.merchantKey
            if (remember && key != null) {
                val existing = db.rules().byKey(key)
                db.rules().upsert(RuleEntity(key, categoryId, existing?.displayName, fromAi = false, updatedAt = clock()))
                txns.recategorise(key, txn.direction, categoryId, type, CategorySource.USER_RULE)
                rules = null
            }
        }
    }

    /** Gives a merchant a name of the user's choosing everywhere it appears. */
    suspend fun renameMerchant(txn: TxnEntity, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val key = txn.merchantKey
        db.withTransaction {
            txns.update(txn.copy(merchantName = clean))
            if (key != null) {
                val existing = db.rules().byKey(key)
                db.rules().upsert(RuleEntity(key, existing?.categoryId, clean, existing?.fromAi ?: false, clock()))
                txns.rename(key, clean)
                rules = null
            }
        }
    }

    suspend fun setExcluded(txn: TxnEntity, excluded: Boolean) = txns.update(txn.copy(excluded = excluded))
    suspend fun setNote(txn: TxnEntity, note: String) = txns.update(txn.copy(note = note.trim().ifEmpty { null }))

    // ---- assistant-suggested categories --------------------------------------------------

    suspend fun unknownMerchants(limit: Int = 40) = txns.unknownMerchants(limit)

    /** Applies categories suggested for merchants the dictionaries could not place. User rules always win. */
    suspend fun applySuggestedCategories(suggestions: Map<String, String>): Int {
        var changed = 0
        db.withTransaction {
            for ((key, categoryId) in suggestions) {
                if (!Categories.isExpense(categoryId) || categoryId == Categories.OTHER) continue
                val existing = db.rules().byKey(key)
                if (existing != null && !existing.fromAi && existing.categoryId != null) continue
                db.rules().upsert(RuleEntity(key, categoryId, existing?.displayName, fromAi = true, updatedAt = clock()))
                changed += txns.recategorise(key, Direction.OUT, categoryId, TxnType.EXPENSE, CategorySource.AI)
            }
            rules = null
        }
        return changed
    }

    // ---- housekeeping --------------------------------------------------------------------

    suspend fun exportCsv(): String {
        val rows = txns.all()
        val out = StringBuilder("date,time,type,amount,category,merchant,account,channel,note,source\n")
        fun cell(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
        for (t in rows) {
            val at = java.time.Instant.ofEpochMilli(t.occurredAt).atZone(zone())
            out.append(at.toLocalDate()).append(',')
                .append(at.toLocalTime().withNano(0)).append(',')
                .append(t.type.name.lowercase()).append(',')
                .append(java.math.BigDecimal.valueOf(t.amountMinor, 2).toPlainString()).append(',')
                .append(cell(Categories.label(t.categoryId))).append(',')
                .append(cell(t.merchantName)).append(',')
                .append(cell(t.accountLabel)).append(',')
                .append(t.channel.name.lowercase()).append(',')
                .append(cell(t.note)).append(',')
                .append(t.source.name.lowercase()).append('\n')
        }
        return out.toString()
    }

    suspend fun wipe() {
        db.withTransaction {
            txns.deleteAll()
            db.rules().deleteAll()
            db.dues().deleteAll()
            db.chat().clear()
        }
        rules = null
        settings.setLastScan(0)
    }

    companion object {
        /** Re-read a little before the last scan so nothing delivered late is missed. */
        const val OVERLAP_MS = 10 * 60 * 1000L
    }
}
