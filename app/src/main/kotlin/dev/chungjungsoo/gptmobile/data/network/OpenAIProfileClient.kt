package dev.chungjungsoo.gptmobile.data.network

import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.model.openAIReasoningEffort
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Url
import io.ktor.http.isSuccess
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

@Serializable
data class OpenAIModelOption(val id: String, val contextWindow: Int? = null)

class OpenAIProfileException(val messageResource: Int) : IllegalArgumentException()

class OpenAIProfileClient @Inject constructor(private val networkClient: NetworkClient) {
    suspend fun models(platform: PlatformV2): List<OpenAIModelOption> {
        val response = networkClient().get(openAIEndpoint(platform.apiUrl, "models")) {
            platform.token?.takeIf { it.isNotBlank() }?.let { bearerAuth(it.trim()) }
            timeout { requestTimeoutMillis = 30_000 }
        }
        return parseOpenAIModels(checkedBody(response, platform.token))
    }

    suspend fun verify(platform: PlatformV2) {
        if (platform.model.isBlank()) throw OpenAIProfileException(R.string.provider_select_model)
        val endpoint = if (platform.compatibleType == ClientType.OPENAI) "responses" else "chat/completions"
        val response = networkClient().post(openAIEndpoint(platform.apiUrl, endpoint)) {
            platform.token?.takeIf { it.isNotBlank() }?.let { bearerAuth(it.trim()) }
            timeout { requestTimeoutMillis = 60_000 }
            setBody(openAIProbeBody(platform).toString())
        }
        val body = NetworkClient.json.parseToJsonElement(checkedBody(response, platform.token)) as? JsonObject
        if (body == null ||
            body["error"] is JsonObject ||
            (body["output"] !is JsonArray && (body["choices"] as? JsonArray)?.isNotEmpty() != true)
        ) {
            throw OpenAIProfileException(R.string.provider_invalid_response)
        }
    }

    private suspend fun checkedBody(response: HttpResponse, token: String?): String {
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val message = runCatching {
                val root = NetworkClient.json.parseToJsonElement(body) as? JsonObject
                ((root?.get("error") as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
            }.getOrNull() ?: response.status.description
            val safeMessage = token?.trim()?.takeIf { it.isNotBlank() }?.let { message.replace(it, "[redacted]") } ?: message
            error("HTTP ${response.status.value}: ${safeMessage.take(500)}")
        }
        return body
    }
}

internal fun openAIEndpoint(baseUrl: String, path: String): String {
    val base = baseUrl.trim()
    val url = runCatching { Url(base) }.getOrElse { throw OpenAIProfileException(R.string.provider_invalid_base_url) }
    if (!(
            url.protocol.name in setOf("http", "https") &&
                url.host.isNotBlank() &&
                url.user == null &&
                url.password == null &&
                url.parameters.isEmpty() &&
                url.fragment.isEmpty() &&
                base.endsWith('/')
            )
    ) {
        throw OpenAIProfileException(R.string.provider_invalid_base_url)
    }
    return base + path
}

internal fun openAIProbeBody(platform: PlatformV2): JsonObject = buildJsonObject {
    put("model", platform.model)
    put("stream", false)
    if (platform.compatibleType == ClientType.OPENAI) {
        put("input", "Reply OK.")
        put("max_output_tokens", 32)
        platform.openAIReasoningEffort()?.let { effort ->
            put("reasoning", buildJsonObject { put("effort", effort) })
        }
    } else {
        put(
            "messages",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", "Reply OK.")
                    }
                )
            }
        )
        val effort = platform.openAIReasoningEffort()
        put(if (effort == null) "max_tokens" else "max_completion_tokens", 32)
        effort?.let { put("reasoning_effort", it) }
    }
}

internal fun parseOpenAIModels(body: String): List<OpenAIModelOption> {
    val root = NetworkClient.json.parseToJsonElement(body) as? JsonObject
    val data = root?.get("data") as? JsonArray ?: throw OpenAIProfileException(R.string.provider_invalid_models_response)
    return data.mapNotNull { element ->
        val model = element as? JsonObject ?: return@mapNotNull null
        val id = (model["id"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val capacity = listOf("context_window", "context_length", "max_context_tokens", "max_model_len", "max_input_tokens")
            .firstNotNullOfOrNull { (model[it] as? JsonPrimitive)?.intOrNull?.takeIf { count -> count > 0 } }
        OpenAIModelOption(id, capacity)
    }.distinctBy { it.id }.sortedBy { it.id }
}
