package app.kanakku.ui

import app.kanakku.R
import app.kanakku.core.category.Categories
import app.kanakku.core.model.Channel

/** Line icon for each category. */
fun categoryIcon(id: String): Int = when (id) {
    Categories.FOOD -> R.drawable.ic_utensils
    Categories.GROCERIES -> R.drawable.ic_shopping_basket
    Categories.RENT -> R.drawable.ic_key_round
    Categories.BILLS -> R.drawable.ic_zap
    Categories.TRANSPORT -> R.drawable.ic_bus
    Categories.FUEL -> R.drawable.ic_fuel
    Categories.SHOPPING -> R.drawable.ic_shopping_bag
    Categories.HEALTH -> R.drawable.ic_heart_pulse
    Categories.ENTERTAINMENT -> R.drawable.ic_clapperboard
    Categories.SUBSCRIPTIONS -> R.drawable.ic_repeat
    Categories.TRAVEL -> R.drawable.ic_plane
    Categories.EDUCATION -> R.drawable.ic_graduation_cap
    Categories.PERSONAL -> R.drawable.ic_scissors
    Categories.PEOPLE -> R.drawable.ic_users
    Categories.INVESTMENTS -> R.drawable.ic_trending_up
    Categories.INSURANCE -> R.drawable.ic_umbrella
    Categories.EMI -> R.drawable.ic_landmark
    Categories.CASH -> R.drawable.ic_banknote
    Categories.FEES -> R.drawable.ic_percent
    Categories.GIVING -> R.drawable.ic_hand_heart
    Categories.SALARY -> R.drawable.ic_briefcase_business
    Categories.POCKET -> R.drawable.ic_hand_coins
    Categories.CASHBACK -> R.drawable.ic_badge_percent
    Categories.INTEREST -> R.drawable.ic_piggy_bank
    Categories.INCOME_OTHER -> R.drawable.ic_circle_plus
    Categories.TRANSFER -> R.drawable.ic_arrow_left_right
    else -> R.drawable.ic_circle_dashed
}

fun channelLabel(channel: Channel): String = when (channel) {
    Channel.UPI -> "UPI"
    Channel.CARD -> "Card"
    Channel.BANK_TRANSFER -> "Bank transfer"
    Channel.ATM -> "ATM"
    Channel.AUTOPAY -> "Auto-debit"
    Channel.WALLET -> "Wallet"
    Channel.CHEQUE -> "Cheque"
    Channel.CASH -> "Cash"
    Channel.OTHER -> "Bank"
}
