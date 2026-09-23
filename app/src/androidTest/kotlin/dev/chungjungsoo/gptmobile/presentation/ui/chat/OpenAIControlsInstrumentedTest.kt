package dev.chungjungsoo.gptmobile.presentation.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.chungjungsoo.gptmobile.data.database.entity.AgentRun
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIModelOption
import dev.chungjungsoo.gptmobile.data.network.OpenAIProfileClient
import dev.chungjungsoo.gptmobile.presentation.common.OpenAIProfileState
import dev.chungjungsoo.gptmobile.presentation.theme.GPTMobileTheme
import io.ktor.client.engine.cio.CIO
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OpenAIControlsInstrumentedTest {
    private val network = NetworkClient(CIO)

    @After
    fun closeNetwork() {
        network().close()
    }

    @Test
    fun cachedModelsAreDirectChoicesWithAnOptInFastCheckboxOnlyForOpenAI() {
        var selectedModel = ""
        composeRule.setContent {
            var current by remember { mutableStateOf(profile) }
            var fast by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            val models = remember {
                OpenAIProfileState(OpenAIProfileClient(network), scope, { listOf(OpenAIModelOption("test-model"), OpenAIModelOption("other-model")) }, { _, _ -> }, { it.message.orEmpty() })
            }
            LaunchedEffect(models) { models.load(current) }
            val providers = listOf(profile, profile.copy(uid = "custom", name = "Compatible", compatibleType = ClientType.CUSTOM))
            GPTMobileTheme {
                ChatModelControls(
                    current, providers, null, true,
                    onProvider = { uid -> current = providers.first { it.uid == uid } },
                    onModel = {
                        selectedModel = it
                        current = current.copy(model = it)
                    },
                    onEffort = {}, onContextSettings = {},
                    fastMode = fast, onFastMode = { fast = it }, modelState = models
                )
            }
        }
        composeRule.onNodeWithText("test-model").performClick()
        composeRule.onNodeWithText("Fast").assertIsOff()
        val fastBounds = composeRule.onNodeWithText("Fast").fetchSemanticsNode().boundsInRoot
        val modelBounds = composeRule.onNodeWithText("other-model").fetchSemanticsNode().boundsInRoot
        assertTrue(fastBounds.bottom <= modelBounds.top)
        composeRule.onNodeWithText("Fast").performClick()
        composeRule.onNodeWithText("Fast").assertIsOn()
        composeRule.onNodeWithText("other-model").performClick()
        assertEquals("other-model", selectedModel)
        composeRule.onNodeWithText("OpenAI").performClick()
        composeRule.onNodeWithText("Compatible").performClick()
        composeRule.onNodeWithText("test-model").performClick()
        composeRule.onNodeWithText("Fast").assertDoesNotExist()
        composeRule.onNodeWithText("other-model").assertExists()
    }

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun controlsSwitchProviderAndEffortAndOpenContextDetails() {
        var selectedProvider = ""
        var selectedEffort = ""
        composeRule.setContent {
            var platform by remember { mutableStateOf(profile) }
            GPTMobileTheme {
                Column {
                    ChatModelControls(
                        platform,
                        listOf(profile, profile.copy(uid = "second", name = "Second provider")),
                        usage,
                        enabled = true,
                        onProvider = { selectedProvider = it },
                        onModel = {},
                        onEffort = {
                            selectedEffort = it
                            platform = platform.copy(reasoning = it.isNotEmpty(), reasoningEffort = it)
                        },
                        onContextSettings = {}
                    )
                    Text("Composer below controls")
                }
            }
        }
        val providerBounds = composeRule.onNodeWithText("OpenAI").fetchSemanticsNode().boundsInRoot
        val composerBounds = composeRule.onNodeWithText("Composer below controls").fetchSemanticsNode().boundsInRoot
        assertTrue(providerBounds.bottom <= composerBounds.top)
        composeRule.onNodeWithText("OpenAI").performClick()
        composeRule.onNodeWithText("Second provider").performClick()
        assertEquals("second", selectedProvider)
        composeRule.onNodeWithText("high").performClick()
        composeRule.onNodeWithText("low").performClick()
        assertEquals("low", selectedEffort)
        composeRule.onNodeWithContentDescription("Context window").performClick()
        composeRule.onNodeWithText("17% used (83% remaining)").assertExists()
        composeRule.onNodeWithText("170k tokens used, 998k total").assertExists()
    }

    @Test
    fun responseMetadataShowsTimeAndExpandableTokenBreakdown() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(
                    text = "Answer",
                    canEdit = true,
                    canRetry = true,
                    isLoading = false,
                    agentRun = usage,
                    showMetadata = true,
                    replyAt = usage.completedAt,
                    revisionIndexLabel = "1/2",
                    canShowNextRevision = true
                )
            }
        }
        composeRule.onNodeWithText("4s").assertExists()
        // Markdown is parsed asynchronously; measure only after the answer has appeared.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Answer").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val copy = composeRule.onNodeWithContentDescription("Copy Text").fetchSemanticsNode().boundsInRoot
            val next = composeRule.onNodeWithContentDescription("Next revision").fetchSemanticsNode().boundsInRoot
            kotlin.math.abs(copy.center.y - next.center.y) <= 2f
        }
        val timing = composeRule.onNodeWithText(formatMessageTime(104)).fetchSemanticsNode().boundsInRoot
        val metadata = composeRule.onNodeWithText("170k").fetchSemanticsNode().boundsInRoot
        assertEquals(timing.center.y, metadata.center.y, 2f)
        val toolbar = composeRule.onNodeWithContentDescription("Copy Text").fetchSemanticsNode().boundsInRoot
        val revision = composeRule.onNodeWithContentDescription("Next revision").fetchSemanticsNode().boundsInRoot
        val hint = composeRule.onNodeWithText("Retry may run assigned tools again.").fetchSemanticsNode().boundsInRoot
        val answer = composeRule.onNodeWithText("Answer").fetchSemanticsNode().boundsInRoot
        val density = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertEquals(toolbar.center.y, revision.center.y, 2f)
        assertTrue("The footer must stay within two compact rows and a hint", hint.bottom - answer.bottom <= 86 * density)
        assertTrue(metadata.bottom <= toolbar.top)
        composeRule.onNodeWithText(formatMessageTime(104)).performClick()
        composeRule.onNodeWithText(formatDetailedMessageTime(104)).assertExists()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("170k").performClick()
        composeRule.onNodeWithText("Input: 160,000").assertExists()
        composeRule.onNodeWithText("Output: 10,000").assertExists()
        composeRule.onNodeWithText("Cached input: 80,000").assertExists()
    }

    @Test
    fun singleReplyDoesNotShowRedundantVersionControls() {
        composeRule.setContent {
            GPTMobileTheme {
                OpponentChatBubble(text = "Answer", canRetry = true, isLoading = false, revisionIndexLabel = "1/1")
            }
        }
        composeRule.onNodeWithText("1/1").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Previous revision").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Next revision").assertDoesNotExist()
    }

    private val profile = PlatformV2(uid = "profile", name = "OpenAI", compatibleType = ClientType.OPENAI, apiUrl = "https://example.com/v1/", model = "test-model", reasoning = true, reasoningEffort = "high")
    private val usage = AgentRun(
        runId = "run", chatId = 1, userMessageId = 1, assistantMessageId = 2, profileUid = "profile", providerSnapshot = "OPENAI", modelSnapshot = "test-model",
        status = "COMPLETED", startedAt = 100, completedAt = 104, inputTokens = 160000, outputTokens = 10000, cachedTokens = 80000, contextTokens = 170000, contextLimit = 998000
    )
}
