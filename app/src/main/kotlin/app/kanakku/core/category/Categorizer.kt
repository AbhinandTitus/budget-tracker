package app.kanakku.core.category

import app.kanakku.core.model.AccountKind
import app.kanakku.core.model.CategorySource
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.Nature
import app.kanakku.core.model.TxnType
import app.kanakku.core.sms.ParsedTxn

/** Something the user has taught the app about one merchant. */
data class LearnedRule(val categoryId: String?, val displayName: String?, val fromAi: Boolean = false)

data class CategorizerConfig(
    /** A bank credit inside this range (paise) is treated as salary. */
    val salaryMinMinor: Long = 40_000_00,
    val salaryMaxMinor: Long = 60_000_00,
)

data class Classified(
    val type: TxnType,
    val categoryId: String,
    val merchantKey: String?,
    val merchantName: String,
    val source: CategorySource,
    val needsReview: Boolean,
)

/**
 * Turns a parsed message into a typed, categorised transaction.
 *
 * Precedence: what the message itself states (ATM, fee, card bill payment) → what the user has
 * taught → known brands → the shop-name lexicon → person detection → "Other" (flagged for review).
 */
object Categorizer {

    fun classify(
        txn: ParsedTxn,
        config: CategorizerConfig = CategorizerConfig(),
        rules: (String) -> LearnedRule? = { null },
    ): Classified {
        val merchant = Merchants.describe(txn.counterparty, txn.vpa)
        val rule = merchant.key?.let(rules)
        val brand = if (merchant.search.isNotEmpty()) MerchantBook.brand(merchant.search) ?: MerchantBook.brand(merchant.compact) else null
        val lexicon = if (merchant.search.isNotEmpty()) {
            MerchantBook.lexiconCategory(merchant.search)
                ?: if (merchant.isVpa) MerchantBook.runTogetherCategory(merchant.compact) else null
        } else {
            null
        }
        val person = !merchant.isPhoneVpa && brand == null && lexicon == null &&
            (txn.personHint == true || (txn.personHint != false && Merchants.looksLikePerson(if (merchant.isVpa) merchant.search.replace(Regex("""\d+"""), " ").trim() else merchant.key)))
        val isPerson = merchant.isPhoneVpa || person

        val institution = txn.institution?.name
        val name = rule?.displayName ?: when (txn.nature) {
            Nature.CARD_BILL_PAYMENT -> if (txn.accountKind == AccountKind.CREDIT_CARD && institution != null) "$institution payment" else "Card bill payment"
            Nature.ATM_WITHDRAWAL -> "ATM withdrawal"
            Nature.FEE -> "Bank charges"
            Nature.INTEREST -> "Interest"
            Nature.EMI -> brand?.name ?: "Loan EMI"
            Nature.CASHBACK -> brand?.name?.let { "$it cashback" } ?: "Cashback"
            Nature.SALARY -> merchant.pretty ?: "Salary"
            else -> null
        } ?: brand?.name ?: merchant.pretty ?: fallbackName(txn)

        fun result(type: TxnType, category: String, source: CategorySource, review: Boolean = false) =
            Classified(type, category, merchant.key, name, source, review)

        if (txn.nature == Nature.CARD_BILL_PAYMENT) return result(TxnType.TRANSFER, Categories.TRANSFER, CategorySource.BUILT_IN)

        // What the user (or the assistant, on their behalf) has taught for this merchant.
        rule?.categoryId?.takeIf(Categories::exists)?.let { id ->
            val source = if (rule.fromAi) CategorySource.AI else CategorySource.USER_RULE
            val kind = Categories.byId(id).kind
            return when {
                kind == Category.Kind.NEUTRAL -> result(TxnType.TRANSFER, id, source)
                txn.direction == Direction.OUT && kind == Category.Kind.EXPENSE -> result(TxnType.EXPENSE, id, source)
                txn.direction == Direction.IN && kind == Category.Kind.INCOME -> result(TxnType.INCOME, id, source)
                txn.direction == Direction.IN && kind == Category.Kind.EXPENSE -> result(TxnType.REFUND, id, source)
                else -> null
            } ?: return@let
        }

        val merchantCategory = brand?.category ?: lexicon
        val merchantSource = if (brand != null) CategorySource.BUILT_IN else CategorySource.LEXICON

        if (txn.direction == Direction.OUT) {
            return when {
                txn.nature == Nature.ATM_WITHDRAWAL -> result(TxnType.EXPENSE, Categories.CASH, CategorySource.BUILT_IN)
                txn.nature == Nature.FEE -> result(TxnType.EXPENSE, Categories.FEES, CategorySource.BUILT_IN)
                txn.nature == Nature.EMI -> result(TxnType.EXPENSE, merchantCategory?.takeIf { it == Categories.INSURANCE } ?: Categories.EMI, CategorySource.BUILT_IN)
                merchantCategory != null -> result(TxnType.EXPENSE, merchantCategory, merchantSource)
                isPerson -> result(TxnType.EXPENSE, Categories.PEOPLE, CategorySource.LEXICON)
                else -> result(TxnType.EXPENSE, Categories.OTHER, CategorySource.DEFAULT, review = true)
            }
        }

        // Money in.
        val onCard = txn.accountKind == AccountKind.CREDIT_CARD
        val inSalaryRange = txn.amountMinor in config.salaryMinMinor..config.salaryMaxMinor
        return when {
            txn.nature == Nature.REFUND || onCard -> result(TxnType.REFUND, merchantCategory ?: Categories.OTHER, if (merchantCategory != null) merchantSource else CategorySource.DEFAULT)
            txn.nature == Nature.CASHBACK -> result(TxnType.INCOME, Categories.CASHBACK, CategorySource.BUILT_IN)
            txn.nature == Nature.INTEREST -> result(TxnType.INCOME, Categories.INTEREST, CategorySource.BUILT_IN)
            txn.nature == Nature.SALARY -> result(TxnType.INCOME, Categories.SALARY, CategorySource.BUILT_IN)
            // A credit from a shop or a broker is money coming back, not income.
            merchantCategory != null -> result(TxnType.REFUND, merchantCategory, merchantSource)
            inSalaryRange && txn.accountKind != AccountKind.WALLET && !isPerson -> result(TxnType.INCOME, Categories.SALARY, CategorySource.BUILT_IN)
            isPerson -> result(TxnType.INCOME, Categories.POCKET, CategorySource.LEXICON)
            else -> result(TxnType.INCOME, Categories.INCOME_OTHER, CategorySource.DEFAULT)
        }
    }

    private fun fallbackName(txn: ParsedTxn): String = when (txn.direction) {
        Direction.OUT -> when (txn.channel) {
            Channel.UPI -> "UPI payment"
            Channel.CARD -> "Card spend"
            Channel.BANK_TRANSFER -> "Bank transfer"
            Channel.AUTOPAY -> "Auto-debit"
            Channel.CHEQUE -> "Cheque"
            else -> "Payment"
        }
        Direction.IN -> when (txn.channel) {
            Channel.UPI -> "UPI credit"
            Channel.BANK_TRANSFER -> "Bank credit"
            Channel.CHEQUE -> "Cheque deposit"
            else -> "Credit"
        }
    }

    /** The transaction type implied by putting a movement in [categoryId]. */
    fun typeFor(direction: Direction, categoryId: String): TxnType {
        val kind = Categories.byId(categoryId).kind
        return when {
            kind == Category.Kind.NEUTRAL -> TxnType.TRANSFER
            direction == Direction.OUT -> TxnType.EXPENSE
            kind == Category.Kind.INCOME -> TxnType.INCOME
            else -> TxnType.REFUND
        }
    }

    /** Categories that make sense for a movement in [direction]. */
    fun choicesFor(direction: Direction): List<Category> =
        if (direction == Direction.OUT) Categories.expense + Categories.transfer
        else Categories.income + Categories.transfer + Categories.expense

    /** Short label for the account a transaction came from, e.g. "SBI Card ••1234". */
    fun accountLabel(txn: ParsedTxn): String {
        val inst = txn.institution?.name
        val base = when (txn.accountKind) {
            AccountKind.CREDIT_CARD -> when {
                inst == null -> "Credit card"
                inst.contains("Card", ignoreCase = true) || txn.institution.kind == app.kanakku.core.sms.Institution.Kind.CARD -> inst
                else -> inst.removeSuffix(" Bank") + " Card"
            }
            AccountKind.DEBIT_CARD -> (inst?.removeSuffix(" Bank")?.let { "$it debit card" }) ?: "Debit card"
            AccountKind.WALLET -> inst?.let { "$it wallet" } ?: "Wallet"
            AccountKind.CASH -> "Cash"
            else -> inst ?: "Bank account"
        }
        return if (txn.accountLast4 != null) "$base ••${txn.accountLast4}" else base
    }

    fun accountKey(txn: ParsedTxn): String =
        "${txn.institution?.id ?: "unknown"}:${txn.accountKind.name}:${txn.accountLast4 ?: "-"}"
}
