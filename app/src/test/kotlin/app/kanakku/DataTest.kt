package app.kanakku

import app.kanakku.ai.KnowledgeBase
import app.kanakku.ai.Plan
import app.kanakku.ai.Planner
import app.kanakku.ai.Prompts
import app.kanakku.core.Money
import app.kanakku.core.category.Categories
import app.kanakku.core.insight.Summaries
import app.kanakku.core.model.Channel
import app.kanakku.core.model.Direction
import app.kanakku.core.model.TxnType
import app.kanakku.data.sms.RawSms
import app.kanakku.support.FakeInbox
import app.kanakku.support.IST
import app.kanakku.support.Seed
import app.kanakku.support.TODAY
import app.kanakku.support.World
import app.kanakku.support.at
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class DataTest {

    private val november = YearMonth.of(2026, 11)

    @Test fun scanStoresTransactionsAndNeverSecrets() = runBlocking {
        val world = World(FakeInbox(Seed.messages()))
        val result = world.repository.scan(0)
        assertTrue("added ${result.added}", result.added > 150)
        assertEquals(1, result.secretsSkipped)
        val all = world.repository.range(november.minusMonths(6), november)
        assertTrue(all.none { it.smsBody.orEmpty().contains("OTP", ignoreCase = true) })
        assertTrue(all.none { it.smsBody.orEmpty().contains("482913") })
    }

    @Test fun scanningTwiceAddsNothing() = runBlocking {
        val world = World(FakeInbox(Seed.messages()))
        val first = world.repository.scan(0)
        val second = world.repository.scan(0)
        assertTrue(first.added > 0)
        assertEquals(0, second.added)
    }

    @Test fun monthEndSalaryCountsForTheNextMonth() = runBlocking {
        val world = World(FakeInbox(Seed.messages()))
        world.repository.scan(0)
        val rows = world.repository.range(november.minusMonths(6), november)
        val nov = Summaries.of(november, rows, IST)
        val oct = Summaries.of(november.minusMonths(1), rows, IST)
        assertEquals(48_500_00L + 3_000_00L, nov.incomeMinor)
        assertEquals(48_500_00L, oct.incomeMinor)
        assertTrue(nov.spentMinor in 1 until nov.incomeMinor)
        assertEquals(Categories.SALARY, nov.incomeByCategory.first().categoryId)
    }

    @Test fun teachingACategoryAppliesEverywhere() = runBlocking {
        val world = World(FakeInbox(Seed.messages()))
        world.repository.scan(0)
        val rows = world.repository.range(november.minusMonths(6), november)
        val rent = rows.filter { it.merchantName == "Suma Nair" }
        assertTrue(rent.size >= 5)
        assertTrue(rent.all { it.categoryId == Categories.PEOPLE })
        world.repository.setCategory(rent.first(), Categories.RENT, remember = true)
        val after = world.repository.range(november.minusMonths(6), november).filter { it.merchantName == "Suma Nair" }
        assertTrue(after.all { it.categoryId == Categories.RENT })

        // And for messages that arrive later.
        world.inbox.messages = world.inbox.messages + Seed.upi(TODAY, 9000.0, "SUMA NAIR", 999000111222, 18, 0)
        world.repository.scan(0)
        val newest = world.repository.range(november, november).filter { it.merchantName == "Suma Nair" }.maxBy { it.occurredAt }
        assertEquals(Categories.RENT, newest.categoryId)
    }

    @Test fun cardBillPaymentIsNotSpending() = runBlocking {
        val world = World(
            FakeInbox(
                listOf(
                    Seed.card(TODAY.minusDays(4), 1200.0, "SWIGGY"),
                    Seed.upi(TODAY.minusDays(2), 6842.0, "SBI CARDS AND PAYMENT", 631299990001),
                    RawSms("VM-SBICRD-S", "We have received payment of Rs.6,842.00 via UPI & the same has been credited to your SBI Credit Card ending 4521. Your available limit is Rs.93,158.00.", at(TODAY.minusDays(2), 13, 22)),
                ),
            ),
        )
        world.repository.scan(0)
        val rows = world.repository.range(november, november)
        assertEquals(3, rows.size)
        assertEquals(2, rows.count { it.type == TxnType.TRANSFER })
        assertEquals(1200_00L, Summaries.of(november, rows, IST).spentMinor)
    }

    @Test fun refundLowersItsCategory() = runBlocking {
        val world = World(
            FakeInbox(
                listOf(
                    Seed.card(TODAY.minusDays(6), 1499.0, "AMAZON PAY INDIA PRIVA"),
                    RawSms("VM-SBICRD-S", "Refund of Rs.499.00 from AMAZON has been credited to your SBI Credit Card ending 4521 on 14/11/26.", at(TODAY.minusDays(2))),
                ),
            ),
        )
        world.repository.scan(0)
        val summary = Summaries.of(november, world.repository.range(november, november), IST)
        assertEquals(1000_00L, summary.spentMinor)
        assertEquals(0L, summary.incomeMinor)
    }

    @Test fun duplicateAlertsForOnePaymentAreCountedOnce() = runBlocking {
        val first = Seed.upi(TODAY, 500.0, "SWIGGY", 631288880001, 13, 10)
        val echo = RawSms("JD-SBIUPI-S", "UPI txn: Rs.500.00 debited from A/C X7788 to SWIGGY. Refno 631288880001 -SBI", first.date + 20_000)
        val world = World(FakeInbox(listOf(first, echo)))
        assertEquals(1, world.repository.scan(0).added)
    }

    @Test fun manualEntryAndStatementDue() = runBlocking {
        val world = World(FakeInbox(Seed.messages()))
        world.repository.scan(0)
        val id = world.repository.addManual(120_00, Direction.OUT, Categories.FOOD, "Tea and snacks", Channel.CASH, at(TODAY), null)
        assertTrue(id > 0)
        assertEquals("Tea and snacks", world.repository.byId(id)!!.merchantName)
        val due = world.db.dues().observeFrom(0).first().single()
        assertEquals(6842_00L, due.totalMinor)
        assertEquals("SBI Card ••4521", due.label)
    }

    @Test fun apiKeyIsStoredSealed() = runBlocking {
        val world = World()
        assertNull(world.settings.geminiKey())
        assertTrue(world.settings.setGeminiKey("TEST-KEY-not-a-real-key-000"))
        assertEquals("TEST-KEY-not-a-real-key-000", world.settings.geminiKey())
        assertTrue(world.settings.current().hasGeminiKey)
        world.settings.setGeminiKey("")
        assertFalse(world.settings.current().hasGeminiKey)
    }

    @Test fun plannerFollowsTheBufferStages() {
        val early = Planner.build(15_000_00, 30_000_00, null)!!
        assertEquals(Plan.Stage.BUILD_BUFFER, early.stage)
        assertEquals(15_000_00L, early.lines.sumOf { it.amountMinor })
        assertEquals(7_500_00L, early.lines.first().amountMinor)
        val later = Planner.build(15_000_00, 30_000_00, 120_000_00)!!
        assertEquals(Plan.Stage.GROW, later.stage)
        assertTrue(later.lines.first { it.label == "Index fund SIP" }.amountMinor > early.lines.first { it.label == "Index fund SIP" }.amountMinor)
        assertTrue(later.lines.all { it.amountMinor % 100_00 == 0L })
        assertNull(Planner.build(300_00, 30_000_00, null))
        // ₹5,000 a month at 12% for ten years is about ₹11.6 lakh.
        assertEquals(11.6, Planner.futureValue(5_000_00, 0.12, 10) / 100_000_00.0, 0.1)
    }

    @Test fun knowledgeRetrievalFindsTheRightNotes() {
        assertTrue(KnowledgeBase.retrieve("how do options trading work").take(2).any { it.id == "options" })
        assertEquals("creditcard", KnowledgeBase.retrieve("should I pay minimum due on my sbi credit card").first().id)
        assertTrue(KnowledgeBase.retrieve("I want to try a first trade with 200 rupees").any { it.id == "learnmarkets" })
        assertTrue(KnowledgeBase.retrieve("is ksfe chitty good").any { it.id == "chitty" })
        assertTrue(KnowledgeBase.retrieve("where should I invest what is left").any { it.id in setOf("index", "order", "sip") })
    }

    @Test fun advisorPromptIsGroundedInTheLedger() = runBlocking {
        val world = World(FakeInbox(Seed.messages()))
        world.repository.scan(0)
        val position = world.advisor.position()
        assertNotNull(position.plan)
        val rows = world.repository.range(november.minusMonths(5), november)
        val prompt = Prompts.system(position, 1, "How much am I spending on Swiggy and what should I invest?", rows, IST)
        assertTrue(prompt.contains("Nov 2026"))
        assertTrue(prompt.contains("Index fund SIP"))
        assertTrue(prompt.contains("Swiggy"))
        assertTrue(prompt.contains("Buy one index ETF unit"))
        assertTrue(prompt.contains("The order money should go in"))
        // Nothing that identifies an account, and no raw message text.
        assertFalse(prompt.contains("7788"))
        assertFalse(prompt.contains("4521"))
        assertFalse(prompt.contains("Refno"))
        assertEquals("₹1,23,456", Money.format(1_23_456_00))
    }
}
