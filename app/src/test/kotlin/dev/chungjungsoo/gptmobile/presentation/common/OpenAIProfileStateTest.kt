package dev.chungjungsoo.gptmobile.presentation.common

import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIModelOption
import dev.chungjungsoo.gptmobile.data.network.OpenAIProfileClient
import io.ktor.client.engine.cio.CIO
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenAIProfileStateTest {
    @Test
    fun `reopening uses saved models and only a successful manual refresh replaces them`() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val response = AtomicReference("""{"data":[{"id":"first"},{"id":"second"}]}""")
        val status = AtomicInteger(200)
        server.createContext("/v1/models") { exchange ->
            requests.incrementAndGet()
            val body = response.get().toByteArray()
            exchange.sendResponseHeaders(status.get(), body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        val network = NetworkClient(CIO)
        try {
            var saved = emptyList<OpenAIModelOption>()
            val profile = PlatformV2(id = 1, name = "Test", compatibleType = ClientType.OPENAI, apiUrl = "http://127.0.0.1:${server.address.port}/v1/", model = "first")
            fun state() = OpenAIProfileState(OpenAIProfileClient(network), this, { saved }, { _, models -> saved = models }, { it.message ?: "Unavailable" })
            val first = state()
            first.load(profile)
            yield()
            assertEquals(0, requests.get())
            first.refresh(profile)
            yield()
            withTimeout(5000) { while (first.isLoading) delay(10) }
            assertEquals(listOf("first", "second"), saved.map { it.id })
            val reopened = state()
            reopened.load(profile.copy(model = "second"))
            yield()
            assertEquals(saved, reopened.models)
            assertEquals(1, requests.get())
            response.set("""{"data":[{"id":"replacement"}]}""")
            reopened.refresh(profile)
            yield()
            withTimeout(5000) { while (reopened.isLoading) delay(10) }
            assertEquals(listOf("replacement"), saved.map { it.id })
            status.set(503)
            reopened.refresh(profile)
            yield()
            withTimeout(5000) { while (reopened.isLoading) delay(10) }
            assertEquals(listOf("replacement"), reopened.models.map { it.id })
            assertNotNull(reopened.error)
            status.set(200)
            response.set("""{"data":[]}""")
            reopened.refresh(profile)
            yield()
            withTimeout(5000) { while (reopened.isLoading) delay(10) }
            assertEquals(emptyList<OpenAIModelOption>(), saved)
            assertEquals(4, requests.get())
        } finally {
            network().close()
            server.stop(0)
        }
    }
}
