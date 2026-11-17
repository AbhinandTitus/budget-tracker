package app.kanakku.core.category

data class Category(
    val id: String,
    val label: String,
    val kind: Kind,
    /** Money put aside rather than consumed (investments, chitty). Still an outflow. */
    val saving: Boolean = false,
) {
    enum class Kind { EXPENSE, INCOME, NEUTRAL }
}

object Categories {
    const val FOOD = "food"
    const val GROCERIES = "groceries"
    const val RENT = "rent"
    const val BILLS = "bills"
    const val TRANSPORT = "transport"
    const val FUEL = "fuel"
    const val SHOPPING = "shopping"
    const val HEALTH = "health"
    const val ENTERTAINMENT = "entertainment"
    const val SUBSCRIPTIONS = "subscriptions"
    const val TRAVEL = "travel"
    const val EDUCATION = "education"
    const val PERSONAL = "personal"
    const val PEOPLE = "people"
    const val INVESTMENTS = "investments"
    const val INSURANCE = "insurance"
    const val EMI = "emi"
    const val CASH = "cash"
    const val FEES = "fees"
    const val GIVING = "giving"
    const val OTHER = "other"

    const val SALARY = "salary"
    const val POCKET = "pocket"
    const val CASHBACK = "cashback"
    const val INTEREST = "interest"
    const val INCOME_OTHER = "income_other"

    const val TRANSFER = "transfer"

    val expense: List<Category> = listOf(
        Category(FOOD, "Food & dining", Category.Kind.EXPENSE),
        Category(GROCERIES, "Groceries", Category.Kind.EXPENSE),
        Category(RENT, "Rent", Category.Kind.EXPENSE),
        Category(BILLS, "Bills & recharge", Category.Kind.EXPENSE),
        Category(TRANSPORT, "Transport", Category.Kind.EXPENSE),
        Category(FUEL, "Fuel", Category.Kind.EXPENSE),
        Category(SHOPPING, "Shopping", Category.Kind.EXPENSE),
        Category(HEALTH, "Health", Category.Kind.EXPENSE),
        Category(ENTERTAINMENT, "Entertainment", Category.Kind.EXPENSE),
        Category(SUBSCRIPTIONS, "Subscriptions", Category.Kind.EXPENSE),
        Category(TRAVEL, "Travel", Category.Kind.EXPENSE),
        Category(EDUCATION, "Education", Category.Kind.EXPENSE),
        Category(PERSONAL, "Personal care", Category.Kind.EXPENSE),
        Category(PEOPLE, "People", Category.Kind.EXPENSE),
        Category(INVESTMENTS, "Investments", Category.Kind.EXPENSE, saving = true),
        Category(INSURANCE, "Insurance", Category.Kind.EXPENSE),
        Category(EMI, "Loans & EMI", Category.Kind.EXPENSE),
        Category(CASH, "Cash", Category.Kind.EXPENSE),
        Category(FEES, "Fees & charges", Category.Kind.EXPENSE),
        Category(GIVING, "Giving", Category.Kind.EXPENSE),
        Category(OTHER, "Other", Category.Kind.EXPENSE),
    )

    val income: List<Category> = listOf(
        Category(SALARY, "Salary", Category.Kind.INCOME),
        Category(POCKET, "Pocket money", Category.Kind.INCOME),
        Category(CASHBACK, "Cashback", Category.Kind.INCOME),
        Category(INTEREST, "Interest", Category.Kind.INCOME),
        Category(INCOME_OTHER, "Other income", Category.Kind.INCOME),
    )

    val transfer = Category(TRANSFER, "Transfer", Category.Kind.NEUTRAL)

    val all: List<Category> = expense + income + transfer
    private val index = all.associateBy { it.id }

    fun byId(id: String?): Category = index[id] ?: index.getValue(OTHER)
    fun exists(id: String?): Boolean = id != null && index.containsKey(id)
    fun label(id: String?): String = byId(id).label
    fun isExpense(id: String?): Boolean = byId(id).kind == Category.Kind.EXPENSE
    fun isIncome(id: String?): Boolean = byId(id).kind == Category.Kind.INCOME
}
