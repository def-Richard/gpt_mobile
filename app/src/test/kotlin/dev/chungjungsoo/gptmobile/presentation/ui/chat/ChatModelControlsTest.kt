package dev.chungjungsoo.gptmobile.presentation.ui.chat

import dev.chungjungsoo.gptmobile.data.database.entity.ChatReasoningConverter
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatModelControlsTest {
    @Test
    fun `reasoning choices use the requested protocol values`() {
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), dev.chungjungsoo.gptmobile.data.model.OPENAI_REASONING_EFFORTS)
    }

    @Test
    fun `message metadata uses compact duration and token units`() {
        assertEquals("5s", formatDuration(5))
        assertEquals("3m", formatDuration(180))
        assertEquals("1h", formatDuration(3600))
        assertEquals("172k", formatTokenSummary(172000))
        assertEquals("56k", formatTokenSummary(56000))
        assertEquals("42", formatTokenSummary(42))
        assertEquals("—", formatTokenSummary(null))
    }

    @Test
    fun `context percentage uses the request total without counting cache twice`() {
        assertEquals(17, contextUsagePercent(170000, 998000))
        assertEquals(100, contextUsagePercent(1100, 1000))
        assertNull(contextUsagePercent(null, 998000))
        assertNull(contextUsagePercent(170000, null))
    }

    @Test
    fun `switching providers preserves earlier replies and independent reasoning selections`() {
        val messages = listOf(
            MessageV2(id = 1, chatId = 1, content = "Question", platformType = null),
            MessageV2(id = 2, chatId = 1, content = "Original reply", platformType = "first", linkedMessageId = 1)
        )
        val grouped = groupPersistedMessages(messages, listOf("first", "second"), 1)
        assertEquals("Original reply", grouped.assistantMessages.single()[0].content)
        assertEquals("second", grouped.assistantMessages.single()[1].platformType)
        assertEquals(0, preferredAssistantIndex(grouped.assistantMessages.single()))
        val switchedReply = listOf(
            MessageV2(content = "", platformType = "first"),
            MessageV2(id = 3, content = "Second provider reply", platformType = "second")
        )
        assertEquals(1, preferredAssistantIndex(switchedReply, 0))
        val efforts = mapOf("first" to "high", "second" to "")
        val converter = ChatReasoningConverter()
        assertEquals(efforts, converter.fromString(converter.toString(efforts)))
    }
}
