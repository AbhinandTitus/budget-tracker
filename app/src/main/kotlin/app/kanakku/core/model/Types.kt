package app.kanakku.core.model

/** Which way the money moved relative to the user's own account or card. */
enum class Direction { IN, OUT }

/**
 * How a transaction counts towards the month.
 *  EXPENSE  – money spent; adds to "out".
 *  INCOME   – money earned or given to the user; adds to "in".
 *  REFUND   – money returned by a merchant; subtracts from "out" in its category.
 *  TRANSFER – money moved between the user's own pockets (card bill payment, self transfer);
 *             counted in neither total.
 */
enum class TxnType { EXPENSE, INCOME, REFUND, TRANSFER }

enum class Channel { UPI, CARD, BANK_TRANSFER, ATM, AUTOPAY, WALLET, CHEQUE, CASH, OTHER }

enum class AccountKind { CREDIT_CARD, DEBIT_CARD, BANK, WALLET, CASH, UNKNOWN }

/** What the message itself says about the event, before any categorisation. */
enum class Nature {
    GENERIC,
    ATM_WITHDRAWAL,
    CARD_BILL_PAYMENT,
    REFUND,
    CASHBACK,
    INTEREST,
    SALARY,
    FEE,
    EMI,
}

enum class Source { SMS, MANUAL }

/** Where a transaction's category came from; decides whether automatic re-categorisation may touch it. */
enum class CategorySource { BUILT_IN, LEXICON, USER_RULE, AI, USER, DEFAULT }
