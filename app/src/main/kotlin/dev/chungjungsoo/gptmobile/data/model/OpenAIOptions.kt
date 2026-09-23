package dev.chungjungsoo.gptmobile.data.model

import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2

fun ClientType.supportsOpenAIOptions(): Boolean = this in setOf(
    ClientType.OPENAI,
    ClientType.CUSTOM,
    ClientType.GROQ,
    ClientType.OPENROUTER,
    ClientType.OLLAMA
)

val OPENAI_REASONING_EFFORTS = listOf("low", "medium", "high", "xhigh", "max")

fun PlatformV2.openAIReasoningEffort(): String? = reasoningEffort.takeIf { reasoning && it in OPENAI_REASONING_EFFORTS }

fun PlatformV2.openAIServiceTier(fastMode: Boolean): String? = "priority".takeIf { compatibleType == ClientType.OPENAI && fastMode }
