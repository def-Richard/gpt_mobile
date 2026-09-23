package dev.chungjungsoo.gptmobile.data.network

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAIProfileClientTest {
    @Test
    fun `models and probe use the supplied base path key model and effort`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val network = NetworkClient(CIO)
        val requests = mutableListOf<Triple<String, String?, String>>()
        server.createContext("/") { exchange ->
            requests += Triple(exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.bufferedReader().readText())
            val body = if (exchange.requestURI.path.endsWith("models")) {
                """{"data":[{"id":"z"},{"id":"test-model","context_window":998000},{"id":"test-model"}]}"""
            } else {
                """{"id":"response-1","output":[]}"""
            }
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val platform = profile.copy(apiUrl = "http://127.0.0.1:${server.address.port}/custom/v1/", token = "test-key")
            val client = OpenAIProfileClient(network)
            val models = client.models(platform)
            assertEquals(listOf("test-model", "z"), models.map { it.id })
            assertEquals(998000, models.first().contextWindow)
            client.verify(platform)
            client.models(platform.copy(token = "changed-key"))
            assertEquals(listOf("/custom/v1/models", "/custom/v1/responses", "/custom/v1/models"), requests.map { it.first })
            assertEquals(listOf("Bearer test-key", "Bearer test-key", "Bearer changed-key"), requests.map { it.second })
            val probe = NetworkClient.json.parseToJsonElement(requests[1].third).jsonObject
            assertEquals("test-model", probe.getValue("model").jsonPrimitive.content)
            assertEquals("high", probe.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content)
        } finally {
            network().close()
            server.stop(0)
        }
    }

    @Test
    fun `disabled reasoning omits both protocol parameters`() {
        assertFalse(openAIProbeBody(profile.copy(reasoning = false)).containsKey("reasoning"))
        assertFalse(openAIProbeBody(profile.copy(compatibleType = ClientType.CUSTOM, reasoning = false)).containsKey("reasoning_effort"))
        assertEquals("high", openAIProbeBody(profile.copy(compatibleType = ClientType.CUSTOM)).getValue("reasoning_effort").jsonPrimitive.content)
    }

    @Test
    fun `invalid base urls and non-model responses are rejected`() {
        listOf("https://example.com/v1", "file:///tmp/", "https://key@example.com/v1/", "https://example.com/v1/?key=value").forEach { base ->
            assertTrue(runCatching { openAIEndpoint(base, "models") }.isFailure)
        }
        assertTrue(runCatching { parseOpenAIModels("""{"error":{"message":"unauthorized"}}""") }.isFailure)
    }

    @Test
    fun `authentication failure is not reported as a successful empty list`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val network = NetworkClient(CIO)
        server.createContext("/") { exchange ->
            val bytes = """{"error":{"message":"bad secret-test-key"}}""".toByteArray()
            exchange.sendResponseHeaders(401, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val result = runCatching { OpenAIProfileClient(network).models(profile.copy(apiUrl = "http://127.0.0.1:${server.address.port}/", token = "  secret-test-key  ")) }
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("401"))
            assertFalse(result.exceptionOrNull()?.message.orEmpty().contains("secret-test-key"))
        } finally {
            network().close()
            server.stop(0)
        }
    }

    private val profile = PlatformV2(name = "OpenAI", compatibleType = ClientType.OPENAI, apiUrl = "https://example.com/v1/", model = "test-model", reasoning = true, reasoningEffort = "high")
}
