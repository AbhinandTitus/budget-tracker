package app.kanakku

import app.kanakku.ai.Advisor
import app.kanakku.ai.GeminiClient
import app.kanakku.ai.GeminiException
import app.kanakku.ai.Turn
import app.kanakku.core.category.Categories
import app.kanakku.support.FakeInbox
import app.kanakku.support.Seed
import app.kanakku.support.TODAY
import app.kanakku.support.World
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class AdvisorTest {
    private val server = MockWebServer()
    private lateinit var client: GeminiClient

    @Before fun start() {
        server.start()
        client = GeminiClient(baseUrl = server.url("/v1beta").toString().trimEnd('/'))
    }

    @After fun stop() = server.close()

    private fun reply(text: String) = MockResponse.Builder().code(200).body(
        """{"candidates":[{"content":{"role":"model","parts":[{"text":"hidden reasoning","thought":true},{"text":${quote(text)}}]},"finishReason":"STOP"}],"usageMetadata":{"totalTokenCount":10}}""",
    ).build()

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

    @Test fun generateSendsKeyAndReadsAnswer() = runBlocking {
        server.enqueue(reply("Put ₹5,000 into an index SIP."))
        val answer = client.generate("KEY123", "gemini-flash-latest", "system text", listOf(Turn(true, "hello")))
        assertEquals("Put ₹5,000 into an index SIP.", answer)
        val request = server.takeRequest()
        assertEquals("KEY123", request.headers["x-goog-api-key"])
        assertTrue(request.url.encodedPath.endsWith("/models/gemini-flash-latest:generateContent"))
        val body = request.body!!.utf8()
        assertTrue(body.contains("\"systemInstruction\""))
        assertTrue(body.contains("\"role\":\"user\""))
        assertFalse(request.url.toString().contains("KEY123"))
    }

    @Test fun errorsAreMappedToPlainLanguage() = runBlocking {
        suspend fun kindFor(code: Int, body: String): GeminiException.Kind {
            server.enqueue(MockResponse.Builder().code(code).body(body).build())
            try {
                client.generate("k", "m", "s", listOf(Turn(true, "q")))
                fail("expected failure")
            } catch (e: GeminiException) {
                assertTrue(e.userMessage.isNotBlank())
                return e.kind
            }
            error("unreachable")
        }
        assertEquals(GeminiException.Kind.INVALID_KEY, kindFor(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT"}}"""))
        assertEquals(GeminiException.Kind.QUOTA, kindFor(429, """{"error":{"code":429,"message":"Quota exceeded","status":"RESOURCE_EXHAUSTED"}}"""))
        assertEquals(GeminiException.Kind.MODEL_NOT_FOUND, kindFor(404, """{"error":{"code":404,"message":"models/x is not found","status":"NOT_FOUND"}}"""))
        assertEquals(GeminiException.Kind.SERVER, kindFor(503, "upstream unavailable"))
        assertEquals(GeminiException.Kind.BLOCKED, kindFor(200, """{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(GeminiException.Kind.EMPTY, kindFor(200, """{"candidates":[{"content":{"parts":[]},"finishReason":"MAX_TOKENS"}]}"""))
    }

    @Test fun listModelsKeepsOnlyChatModels() = runBlocking {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """{"models":[
                {"name":"models/gemini-flash-latest","supportedGenerationMethods":["generateContent","countTokens"]},
                {"name":"models/gemini-embedding-001","supportedGenerationMethods":["embedContent"]},
                {"name":"models/gemini-pro-latest","supportedGenerationMethods":["generateContent"]},
                {"name":"models/imagen-4","supportedGenerationMethods":["predict"]}]}""",
            ).build(),
        )
        assertEquals(listOf("gemini-flash-latest", "gemini-pro-latest"), client.listModels("k"))
    }

    @Test fun askStoresBothSidesAndGroundsTheRequest() = runBlocking {
        val world = World(FakeInbox(Seed.messages()), client)
        world.repository.scan(0)
        world.settings.setGeminiKey("TEST-KEY-not-a-real-key-000")
        server.enqueue(reply("You have about **₹12,000** left.\n- Start a ₹5,000 SIP"))
        world.advisor.ask("What should I do with what's left?")
        val chat = world.advisor.conversation().first()
        assertEquals(listOf(Advisor.USER, Advisor.MODEL), chat.map { it.role })
        assertFalse(chat.last().failed)
        val sent = server.takeRequest().body!!.utf8()
        assertTrue(sent.contains("Index fund SIP"))
        assertTrue(sent.contains("Their numbers"))
        assertFalse("account digits must not be sent", sent.contains("7788") || sent.contains("4521"))
        assertFalse("message text must not be sent", sent.contains("Refno") || sent.contains("Trxn. not done"))
    }

    @Test fun askWithoutKeyExplainsWhatToDo() = runBlocking {
        val world = World(client = client)
        world.advisor.ask("hello")
        val last = world.advisor.conversation().first().last()
        assertTrue(last.failed)
        assertTrue(last.text.contains("Settings"))
        assertEquals(0, server.requestCount)
    }

    @Test fun unknownMerchantsAreCategorisedByNameOnly() = runBlocking {
        val world = World(FakeInbox(Seed.messages()), client)
        world.repository.scan(0)
        world.settings.setGeminiKey("TEST-KEY-not-a-real-key-000")
        val before = world.repository.unknownMerchants().map { it.merchantKey }
        assertTrue(before.containsAll(listOf("ZYXWV ENTERPRISES", "QUICKBITES")))
        server.enqueue(reply("""[{"key":"QUICKBITES","category":"food"},{"key":"ZYXWV ENTERPRISES","category":"other"},{"key":"NOT ASKED","category":"fuel"}]"""))
        val changed = world.advisor.categoriseUnknownMerchants()
        assertEquals(1, changed)
        val month = YearMonth.from(TODAY)
        val quick = world.repository.range(month, month).first { it.merchantKey == "QUICKBITES" }
        assertEquals(Categories.FOOD, quick.categoryId)
        assertFalse(quick.needsReview)
        val sent = server.takeRequest().body!!.utf8()
        assertTrue(sent.contains("QUICKBITES"))
        assertTrue(sent.contains("responseSchema"))
        assertFalse(sent.contains("7788"))
    }
}
