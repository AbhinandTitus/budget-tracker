package app.kanakku.support

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.kanakku.Graph
import app.kanakku.ai.Advisor
import app.kanakku.ai.GeminiClient
import app.kanakku.data.Repository
import app.kanakku.data.SecretBox
import app.kanakku.data.Settings
import app.kanakku.data.db.AppDatabase
import app.kanakku.data.sms.RawSms
import app.kanakku.data.sms.SmsSource
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.random.Random

val IST: ZoneId = ZoneId.of("Asia/Kolkata")
val TODAY: LocalDate = LocalDate.of(2026, 11, 16)
val NOW: Long = LocalDateTime.of(2026, 11, 16, 14, 5).atZone(IST).toInstant().toEpochMilli()

class PlainBox : SecretBox {
    override fun seal(plain: String) = "sealed:$plain"
    override fun open(sealed: String) = sealed.removePrefix("sealed:")
}

class FakeInbox(var messages: List<RawSms> = emptyList()) : SmsSource {
    override fun read(since: Long): Sequence<RawSms> = messages.filter { it.date >= since }.sortedBy { it.date }.asSequence()
}

class World(val inbox: FakeInbox = FakeInbox(), client: GeminiClient = GeminiClient()) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db = AppDatabase.inMemory(context)
    // The preferences store is a process-wide singleton, so start each test from a clean one.
    val settings = Settings(context, PlainBox()).also { kotlinx.coroutines.runBlocking { it.clear() } }
    val repository = Repository(db, settings, inbox, clock = { NOW }, zone = { IST })
    val advisor = Advisor(repository, settings, db.chat(), client, clock = { NOW }, zone = { IST })
    val graph = Graph(db, settings, repository, advisor).also(Graph::install)
}

fun at(date: LocalDate, hour: Int = 13, minute: Int = 20): Long = date.atTime(hour, minute).atZone(IST).toInstant().toEpochMilli()

/** Six months of realistic alerts, written the way the banks write them. */
object Seed {
    private const val CARD = "VM-SBICRD-S"
    private const val UPI = "JD-SBIUPI-S"
    private const val BANK = "VM-SBIINB-S"

    private fun dmy(d: LocalDate) = "%02d/%02d/%02d".format(d.dayOfMonth, d.monthValue, d.year % 100)
    private fun compact(d: LocalDate) = "%02d%s%02d".format(d.dayOfMonth, d.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }, d.year % 100)

    fun card(d: LocalDate, rupees: Double, merchant: String, h: Int = 13, m: Int = 20) = RawSms(
        CARD,
        "Rs.%,.2f spent on your SBI Credit Card ending 4521 at %s on %s. Trxn. not done by you? Report at https://sbicard.com/Dispute".format(rupees, merchant, dmy(d)),
        at(d, h, m),
    )

    fun upi(d: LocalDate, rupees: Double, payee: String, ref: Long, h: Int = 13, m: Int = 20) = RawSms(
        UPI,
        "Dear UPI user A/C X7788 debited by %.1f on date %s trf to %s Refno %d. If not u? call 1800111109. -SBI".format(rupees, compact(d), payee, ref),
        at(d, h, m),
    )

    fun credit(d: LocalDate, rupees: Double, from: String, ref: Long, h: Int = 10) = RawSms(
        UPI, "Dear SBI User, your A/c X7788-credited by Rs.%.0f on %s transfer from %s Ref No %d -SBI".format(rupees, compact(d), from, ref), at(d, h, 5),
    )

    fun salary(d: LocalDate, rupees: Double, balance: Double) = RawSms(
        BANK,
        "Your A/C XXXXX7788 Credited INR %,.2f on %s -Deposit by transfer from M/S NORTHWIND TECHNOLOGIES. Avl Bal INR %,.2f-SBI".format(rupees, dmy(d), balance),
        at(d, 18, 40),
    )

    fun messages(): List<RawSms> {
        val rnd = Random(7)
        val out = ArrayList<RawSms>()
        var ref = 631200000000L
        fun next() = ++ref

        val food = listOf("SWIGGY" to 180..520, "ZOMATO LIMITED" to 200..560, "HOTEL ARYA NIVAS" to 90..260, "ROYAL BAKERY" to 40..180, "CHICKING" to 220..480, "INDIAN COFFEE HOUSE" to 70..190)
        val groceries = listOf("LULU HYPERMARKET" to 700..2400, "SUPPLYCO" to 300..900, "SREE KRISHNA STORES" to 80..420, "FRESH FISH MARKET" to 180..520, "SWIGGY INSTAMART" to 220..680)
        val transport = listOf("KSRTC" to 40..260, "KOCHI METRO RAIL LTD" to 30..80, "UBER INDIA SYSTEMS PVT" to 120..380)
        val shopping = listOf("AMAZON PAY INDIA PRIVA" to 400..2600, "MYNTRA DESIGNS" to 700..2200, "ROYAL TEXTILES" to 500..1800, "OXYGEN THE DIGITAL EXPERT" to 900..3200)

        for (back in 5 downTo 0) {
            val month = java.time.YearMonth.from(TODAY).minusMonths(back.toLong())
            val lastDay = if (back == 0) TODAY.dayOfMonth else month.lengthOfMonth()
            fun day(d: Int) = month.atDay(d.coerceIn(1, month.lengthOfMonth()))
            fun allowed(d: Int) = d <= lastDay

            // Salary for this month arrives at the end of the previous one.
            val payday = month.minusMonths(1).atEndOfMonth().minusDays(1)
            out += salary(payday, 48500.0, 52345.67 + rnd.nextInt(4000))
            if (allowed(2)) out += upi(day(2), 9000.0, "SUMA NAIR", next(), 9, 12)
            if (allowed(5)) out += upi(day(5), 3000.0, "INDIAN CLEARING CORP", next(), 8, 2)
            if (allowed(6)) out += card(day(6), 199.0, "NETFLIX COM", 6, 1)
            if (allowed(9)) out += card(day(9), 119.0, "SPOTIFY INDIA", 7, 30)
            if (allowed(8)) out += upi(day(8), 299.0, "JIO PREPAID", next(), 20, 14)
            if (allowed(11) && back % 2 == 0) out += upi(day(11), 1240.0 + rnd.nextInt(400), "KSEB", next(), 19, 3)
            if (allowed(4) && (back == 0 || back == 2 || back == 4)) out += credit(day(4), 3000.0, "RAJAN P", next())
            if (allowed(14)) out += upi(day(14), 900.0, "POWER GYM", next(), 7, 5)

            repeat(13 + rnd.nextInt(4)) {
                val (m, range) = food.random(rnd)
                val d = 1 + rnd.nextInt(month.lengthOfMonth())
                if (allowed(d)) out += if (rnd.nextBoolean()) card(day(d), range.random(rnd).toDouble(), m, 12 + rnd.nextInt(10), rnd.nextInt(60)) else upi(day(d), range.random(rnd).toDouble(), m, next(), 12 + rnd.nextInt(10), rnd.nextInt(60))
            }
            repeat(5 + rnd.nextInt(2)) {
                val (m, range) = groceries.random(rnd)
                val d = 1 + rnd.nextInt(month.lengthOfMonth())
                if (allowed(d)) out += upi(day(d), range.random(rnd).toDouble(), m, next(), 17 + rnd.nextInt(4), rnd.nextInt(60))
            }
            repeat(6 + rnd.nextInt(3)) {
                val (m, range) = transport.random(rnd)
                val d = 1 + rnd.nextInt(month.lengthOfMonth())
                if (allowed(d)) out += upi(day(d), range.random(rnd).toDouble(), m, next(), 8 + rnd.nextInt(3), rnd.nextInt(60))
            }
            repeat(2) {
                val d = 1 + rnd.nextInt(month.lengthOfMonth())
                if (allowed(d)) out += card(day(d), (500..900).random(rnd).toDouble(), "IOCL KAKKANAD FUELS", 18, rnd.nextInt(60))
            }
            repeat(1 + rnd.nextInt(2)) {
                val (m, range) = shopping.random(rnd)
                val d = 1 + rnd.nextInt(month.lengthOfMonth())
                if (allowed(d)) out += card(day(d), range.random(rnd).toDouble(), m, 21, rnd.nextInt(60))
            }
            val d1 = 3 + rnd.nextInt(20)
            if (allowed(d1)) out += upi(day(d1), (120..480).random(rnd).toDouble(), "ST MARYS MEDICALS", next(), 19, 40)
            val d2 = 5 + rnd.nextInt(20)
            if (allowed(d2)) out += card(day(d2), 2 * 190.0, "BOOKMYSHOW", 16, 10)
            if (allowed(18)) out += upi(day(18), 250.0, "Mr ANOOP K S", next(), 21, 15)
        }

        // This month's loose ends.
        out += upi(TODAY.minusDays(1), 340.0, "ZYXWV ENTERPRISES", ++ref, 15, 42)
        out += upi(TODAY, 185.0, "QUICKBITES", ++ref, 13, 10)
        out += RawSms(
            CARD,
            "E-statement of SBI Credit Card ending XX4521 dated 05/11/2026 has been mailed. Total Amt Due: Rs 6,842.00; Min Amt Due: Rs 342.00; Payable by 25/11/2026. If not received, SMS ENRS to 5676791",
            at(TODAY.withDayOfMonth(5), 9, 0),
        )
        out += RawSms(CARD, "OTP for online purchase of Rs. 1,234.00 at AMAZON thru SBI Credit Card ending 4521 is 482913. Do not share it with anyone.", at(TODAY.minusDays(3), 21, 1))
        out += RawSms("AX-HDFCBK-P", "Get Rs.500 cashback credited on spends above Rs.5000. T&C", at(TODAY.minusDays(2), 11, 0))
        return out
    }
}
