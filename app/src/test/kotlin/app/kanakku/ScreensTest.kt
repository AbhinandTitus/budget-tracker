package app.kanakku

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.kanakku.ai.Advisor
import app.kanakku.core.category.Categories
import app.kanakku.core.insight.Summaries
import app.kanakku.core.model.TxnType
import app.kanakku.data.Prefs
import app.kanakku.data.db.ChatEntity
import app.kanakku.data.db.TxnEntity
import app.kanakku.support.FakeInbox
import app.kanakku.support.IST
import app.kanakku.support.Seed
import app.kanakku.support.TODAY
import app.kanakku.support.World
import app.kanakku.ui.MonthView
import app.kanakku.ui.screens.ActivityFilter
import app.kanakku.ui.screens.ActivityScreen
import app.kanakku.ui.screens.AddEntrySheet
import app.kanakku.ui.screens.AdvisorScreen
import app.kanakku.ui.screens.HomeScreen
import app.kanakku.ui.screens.InsightsScreen
import app.kanakku.ui.screens.LearnScreen
import app.kanakku.ui.screens.OnboardingScreen
import app.kanakku.ui.screens.SettingsScreen
import app.kanakku.ui.screens.TxnSheet
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.KanakkuTheme
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.YearMonth
import java.util.TimeZone

/** Renders every screen with six months of seeded data. Images land in build/shots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h780dp-xxhdpi")
class ScreensTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var world: World
    private lateinit var rows: List<TxnEntity>
    private val month = YearMonth.from(TODAY)
    private val pad = PaddingValues(top = 28.dp, bottom = 88.dp)
    private val prefs = Prefs(onboarded = true, hasGeminiKey = true, lastScanAt = app.kanakku.support.NOW)

    @Before fun seed() = runBlocking {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        world = World(FakeInbox(Seed.messages()))
        world.repository.scan(0)
        val rent = world.repository.range(month, month).first { it.merchantName == "Suma Nair" }
        world.repository.setCategory(rent, Categories.RENT, remember = true)
        world.repository.renameMerchant(world.repository.byId(rent.id)!!, "Rent · Suma Nair")
        rows = world.repository.range(month.minusMonths(6), month)
    }

    private fun view(m: YearMonth = month) = MonthView(
        month = m, today = TODAY,
        txns = rows.filter { YearMonth.from(app.kanakku.core.Months.dateOf(it.effectiveAt, IST)) == m },
        summary = Summaries.of(m, rows, IST),
        history = (5 downTo 0).map { Summaries.of(month.minusMonths(it.toLong()), rows, IST) },
        previous = Summaries.of(m.minusMonths(1), rows, IST),
        loaded = true,
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            KanakkuTheme {
                Box(Modifier.fillMaxSize().background(Ink.Bg)) {
                    content()
                    BottomBarFor(name)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/$name.png")
    }

    @Composable
    private fun androidx.compose.foundation.layout.BoxScope.BottomBarFor(name: String) {
        val tab = when {
            name.startsWith("home") -> Tab.HOME
            name.startsWith("activity") -> Tab.ACTIVITY
            name.startsWith("insights") -> Tab.INSIGHTS
            name.startsWith("advisor") -> Tab.ADVISOR
            else -> return
        }
        BottomBar(tab, {}, {}, Modifier.align(Alignment.BottomCenter), 16.dp, 64.dp)
    }

    @Test fun home() = runBlocking {
        val dues = world.db.dues().observeFrom(0).first()
        shot("home") { HomeScreen(view(), prefs, dues, false, pad, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun homeEmpty() {
        val empty = MonthView(month, TODAY, summary = Summaries.of(month, emptyList(), IST), loaded = true)
        shot("home_empty") { HomeScreen(empty, prefs, emptyList(), false, pad, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun homePastMonth() {
        shot("home_october") { HomeScreen(view(month.minusMonths(1)), prefs, emptyList(), false, pad, {}, {}, {}, {}, {}, {}, {}, {}, {}) }
    }

    @Test fun activity() {
        shot("activity") { ActivityScreen(view(), "", null, ActivityFilter.ALL, pad, {}, {}, {}, {}, {}) }
    }

    @Test fun insights() = runBlocking {
        val position = world.advisor.position()
        shot("insights") { InsightsScreen(view(), position, pad, {}, {}, {}, {}) }
    }

    @Test fun insightsLower() = runBlocking {
        val position = world.advisor.position()
        compose.setContent {
            KanakkuTheme { Box(Modifier.fillMaxSize().background(Ink.Bg)) { InsightsScreen(view(), position, pad, {}, {}, {}, {}) } }
        }
        compose.waitForIdle()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Ask about this plan"))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/insights_plan.png")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Top merchants", ignoreCase = true))
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/shots/insights_lists.png")
    }

    @Test fun advisor() {
        val chat = listOf(
            ChatEntity(1, Advisor.USER, "What should I do with what's left this month?", 0),
            ChatEntity(
                2, Advisor.MODEL,
                "You typically keep about **₹14,800** a month after living costs, and November is on the same track.\n\n" +
                    "- Move ₹7,400 into a sweep FD on salary day until you hold three months of spending\n" +
                    "- Start a ₹5,200 Nifty 50 index SIP dated the 2nd. At 12% a year that alone is about ₹12L in ten years\n" +
                    "- Put ₹1,500 into a Midcap 150 index fund for extra growth. It can fall 40% in a bad year, so leave it for 7+ years\n\n" +
                    "**Try this:** buy one Nifty 50 ETF unit for about ₹300 today and note the price you paid against the last traded price.",
                0,
            ),
        )
        shot("advisor") { AdvisorScreen(chat, false, true, 1, pad, "", {}, {}, {}, {}, {}) }
    }

    @Test fun advisorEmpty() {
        shot("advisor_empty") { AdvisorScreen(emptyList(), false, true, 0, pad, "", {}, {}, {}, {}, {}) }
    }

    @Test fun advisorNoKey() {
        shot("advisor_nokey") { AdvisorScreen(emptyList(), false, false, 0, pad, "", {}, {}, {}, {}, {}) }
    }

    @Test fun learn() {
        shot("learn") { LearnScreen(1, PaddingValues(top = 28.dp, bottom = 16.dp), {}, {}, {}) }
    }

    @Test fun settings() {
        shot("settings") {
            SettingsScreen(prefs, false, true, rows.size, PaddingValues(top = 28.dp, bottom = 16.dp), {}, { _, _ -> }, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }

    @Test fun onboarding() {
        shot("onboarding_1") { OnboardingScreen(0, false, 0, 0, false, PaddingValues(top = 28.dp, bottom = 16.dp), {}, {}, {}, {}, {}) }
    }

    @Test fun onboardingPermission() {
        shot("onboarding_2") { OnboardingScreen(1, false, 0, 0, true, PaddingValues(top = 28.dp, bottom = 16.dp), {}, {}, {}, {}, {}) }
    }

    @Test fun addEntrySheet() {
        compose.setContent { KanakkuTheme { Box(Modifier.fillMaxSize().background(Ink.Bg)) { AddEntrySheet({}) { _, _, _, _, _, _, _ -> } } } }
        compose.waitForIdle()
        compose.onNodeWithText("3").performClick()
        compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("0").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/add_entry.png")
    }

    @Test fun txnSheet() {
        val txn = rows.first { it.merchantName == "Swiggy" && it.type == TxnType.EXPENSE }
        compose.setContent { KanakkuTheme { Box(Modifier.fillMaxSize().background(Ink.Bg)) { TxnSheet(txn, {}, { _, _ -> }, {}, {}, {}, {}) } } }
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/txn_sheet.png")
    }

    @Test fun categoryPicker() {
        val txn = rows.first { it.needsReview }
        compose.setContent { KanakkuTheme { Box(Modifier.fillMaxSize().background(Ink.Bg)) { TxnSheet(txn, {}, { _, _ -> }, {}, {}, {}, {}) } } }
        compose.waitForIdle()
        compose.onNodeWithText("Category").performClick()
        compose.waitForIdle()
        captureScreenRoboImage("build/shots/category_picker.png")
    }
}
