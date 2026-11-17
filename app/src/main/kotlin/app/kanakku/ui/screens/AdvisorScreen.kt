package app.kanakku.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.kanakku.R
import app.kanakku.ai.Advisor
import app.kanakku.ai.LearningTrack
import app.kanakku.data.db.ChatEntity
import app.kanakku.ui.components.Chevron
import app.kanakku.ui.components.Glyph
import app.kanakku.ui.components.Gutter
import app.kanakku.ui.components.IconButton
import app.kanakku.ui.components.Panel
import app.kanakku.ui.components.PrimaryButton
import app.kanakku.ui.theme.Ink
import app.kanakku.ui.theme.Type

private val starters = listOf(
    "What should I do with what's left this month?",
    "Where can I cut back without feeling it?",
    "How do I start investing with my salary?",
    "I want to try a first trade with ₹200. Walk me through it.",
    "Explain options trading and how to learn it safely.",
)

@Composable
fun AdvisorScreen(
    messages: List<ChatEntity>,
    thinking: Boolean,
    hasKey: Boolean,
    learnStep: Int,
    contentPadding: PaddingValues,
    draft: String,
    onDraft: (String) -> Unit,
    onSend: (String) -> Unit,
    onClear: () -> Unit,
    onAddKey: () -> Unit,
    onLearn: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, thinking) {
        val last = messages.size + (if (thinking) 1 else 0) + 1
        if (last > 0) listState.animateScrollToItem(last)
    }
    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Advisor", Modifier.weight(1f), style = Type.Title)
            if (messages.isNotEmpty()) IconButton(R.drawable.ic_trash, "Clear conversation", onClear, tint = Ink.Text3)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, contentPadding = PaddingValues(top = 14.dp, bottom = 12.dp)) {
            item("learn") { LearnBanner(learnStep, onLearn) }
            if (!hasKey) {
                item("key") { ConnectCard(onAddKey) }
            } else if (messages.isEmpty()) {
                item("starters") {
                    Column(Modifier.padding(horizontal = Gutter).padding(top = 22.dp)) {
                        Text("Ask about your money", style = Type.Heading)
                        Text("Answers use your own income and spending.", style = Type.Caption, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        starters.forEach { q ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(Ink.Surface)
                                    .clickable { onSend(q) }.padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(q, Modifier.weight(1f), style = Type.Body.copy(color = Ink.Text2))
                                Glyph(R.drawable.ic_arrow_up_right, Ink.Text4, 16.dp)
                            }
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { Bubble(it) }
            if (thinking) item("thinking") { ThinkingDots() }
        }
        Composer(draft, onDraft, enabled = hasKey && !thinking, onSend = onSend, bottom = contentPadding.calculateBottomPadding())
    }
}

@Composable
private fun LearnBanner(done: Int, onClick: () -> Unit) {
    val next = LearningTrack.current(done)
    Panel(Modifier.padding(horizontal = Gutter).fillMaxWidth(), onClick = onClick, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Ink.SurfaceHi), contentAlignment = Alignment.Center) {
                Glyph(R.drawable.ic_graduation_cap, Ink.Gold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (next != null) "Learn markets · step ${done + 1} of ${LearningTrack.steps.size}" else "Learn markets · complete", style = Type.Caption)
                Text(next?.title ?: "Review the ten steps", style = Type.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Chevron()
        }
    }
}

@Composable
private fun ConnectCard(onAddKey: () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter).padding(top = 28.dp)) {
        Text("Connect Gemini", style = Type.Heading)
        Text(
            "The advisor uses Google's Gemini with your own API key. It sees monthly totals, merchant names, amounts and dates – " +
                "never your messages or account numbers.",
            Modifier.padding(top = 6.dp, bottom = 18.dp), style = Type.Body.copy(color = Ink.Text3),
        )
        PrimaryButton("Add API key", onAddKey)
    }
}

@Composable
private fun Bubble(message: ChatEntity) {
    val mine = message.role == Advisor.USER
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        if (mine) {
            Box(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).background(Ink.SurfaceTop).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(message.text, style = Type.Body)
            }
        } else {
            Text(renderMarkdown(message.text), Modifier.fillMaxWidth().padding(end = 20.dp), style = Type.Body.copy(color = if (message.failed) Ink.Warn else Ink.Text, lineHeight = Type.Body.lineHeight * 1.08f))
        }
    }
}

/** Renders the light Markdown the model uses: bold, bullets and stray heading marks. */
internal fun renderMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.trim().lines()
    lines.forEachIndexed { index, raw ->
        var line = raw.trimEnd()
        val bullet = Regex("""^\s*(?:[-*•]|\d+[.)])\s+""").find(line)
        if (bullet != null) {
            val marker = bullet.value.trim()
            append(if (marker.first().isDigit()) "$marker " else "•  ")
            line = line.substring(bullet.range.last + 1)
        }
        val heading = line.startsWith("#")
        if (heading) line = line.trimStart('#', ' ')
        val parts = line.split("**")
        parts.forEachIndexed { i, part ->
            val clean = part.replace(Regex("""(?<![\w*])[*_](?=\S)|(?<=\S)[*_](?![\w*])"""), "")
            if (i % 2 == 1 || heading) withStyle(SpanStyle(fontWeight = FontWeight(600))) { append(clean) } else append(clean)
        }
        if (index != lines.lastIndex) append('\n')
    }
}

@Composable
private fun ThinkingDots() {
    val pulse = rememberInfiniteTransition(label = "thinking")
    Row(Modifier.padding(horizontal = Gutter, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val a by pulse.animateFloat(0.25f, 1f, infiniteRepeatable(tween(520, delayMillis = i * 140), RepeatMode.Reverse), label = "dot$i")
            Box(Modifier.size(7.dp).alpha(a).clip(CircleShape).background(Ink.Text2))
        }
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, enabled: Boolean, onSend: (String) -> Unit, bottom: androidx.compose.ui.unit.Dp) {
    Column(Modifier.fillMaxWidth().imePadding().padding(bottom = bottom)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Ink.Surface).padding(horizontal = 18.dp, vertical = 13.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) Text(if (enabled) "Ask anything about your money" else "Advisor", style = Type.Body.copy(color = Ink.Text4))
                BasicTextField(
                    draft, onDraft, Modifier.fillMaxWidth(), enabled = enabled,
                    textStyle = Type.Body, cursorBrush = SolidColor(Ink.Gold), maxLines = 5,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = enabled && draft.isNotBlank()
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(if (canSend) Ink.Gold else Ink.Surface)
                    .clickable(enabled = canSend) { onSend(draft.trim()); onDraft("") },
                contentAlignment = Alignment.Center,
            ) { Glyph(R.drawable.ic_arrow_up, if (canSend) Ink.OnGold else Ink.Text4, description = "Send") }
        }
    }
}

@Composable
fun LearnScreen(done: Int, contentPadding: PaddingValues, onBack: () -> Unit, onSetDone: (Int) -> Unit, onAsk: (String) -> Unit) {
    var open by rememberSaveable { mutableStateOf(done.coerceAtMost(LearningTrack.steps.lastIndex)) }
    LazyColumn(Modifier.fillMaxSize().background(Ink.Bg), contentPadding = contentPadding) {
        item("top") {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = Gutter, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(R.drawable.ic_arrow_left, "Back", onBack)
                Spacer(Modifier.width(4.dp))
                Text("Learn markets", style = Type.Title)
            }
            Text(
                "Ten small steps with real money, each sized so that a mistake costs less than a meal. Keep this separate from the money you invest for the long run.",
                Modifier.padding(horizontal = Gutter).padding(top = 10.dp, bottom = 16.dp), style = Type.Body.copy(color = Ink.Text3),
            )
        }
        items(LearningTrack.steps.size, key = { it }) { i ->
            val step = LearningTrack.steps[i]
            val finished = i < done
            val expanded = open == i
            Panel(Modifier.padding(horizontal = Gutter, vertical = 5.dp).fillMaxWidth(), onClick = { open = if (expanded) -1 else i }, padding = PaddingValues(16.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(28.dp).clip(CircleShape).background(if (finished) Ink.Gold else Ink.SurfaceHi),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (finished) Glyph(R.drawable.ic_check, Ink.OnGold, 16.dp)
                            else Text("${i + 1}", style = Type.Label.copy(color = if (i == done) Ink.Gold else Ink.Text3))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(step.title, style = Type.BodyStrong.copy(color = if (finished) Ink.Text2 else Ink.Text))
                            Text(step.stake, style = Type.Caption)
                        }
                    }
                    if (expanded) {
                        Spacer(Modifier.height(14.dp))
                        Text(step.action, style = Type.Body)
                        Spacer(Modifier.height(10.dp))
                        Text("What it teaches", style = Type.Overline)
                        Text(step.lesson, Modifier.padding(top = 4.dp), style = Type.Body.copy(color = Ink.Text2))
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            app.kanakku.ui.components.QuietButton(
                                if (finished) "Mark not done" else "Mark done",
                                { onSetDone(if (finished) i else i + 1) }, Modifier.weight(1f),
                                color = if (finished) Ink.Text2 else Ink.Gold,
                            )
                            app.kanakku.ui.components.QuietButton("Ask how", { onAsk("Guide me through this step: ${step.title}. ${step.action}") }, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        item("end") { Spacer(Modifier.height(20.dp)) }
    }
}
