package dev.chungjungsoo.gptmobile.data.repository

import android.content.ContextWrapper
import com.sun.net.httpserver.HttpServer
import dev.chungjungsoo.gptmobile.data.agent.provider.ProviderAttachmentEncoder
import dev.chungjungsoo.gptmobile.data.context.ConversationTurn
import dev.chungjungsoo.gptmobile.data.context.ProviderContextPolicy
import dev.chungjungsoo.gptmobile.data.database.entity.MessageV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.dto.anthropic.request.MessageRequest
import dev.chungjungsoo.gptmobile.data.dto.anthropic.response.MessageResponseChunk
import dev.chungjungsoo.gptmobile.data.dto.google.request.GenerateContentRequest
import dev.chungjungsoo.gptmobile.data.dto.google.response.GenerateContentResponse
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ChatCompletionRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.request.ResponsesRequest
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ChatCompletionChunk
import dev.chungjungsoo.gptmobile.data.dto.openai.response.ResponsesStreamEvent
import dev.chungjungsoo.gptmobile.data.model.AttachmentProviderRef
import dev.chungjungsoo.gptmobile.data.model.AttachmentRemoteType
import dev.chungjungsoo.gptmobile.data.model.ChatAttachment
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.AnthropicAPI
import dev.chungjungsoo.gptmobile.data.network.GoogleAPI
import dev.chungjungsoo.gptmobile.data.network.NetworkClient
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPI
import dev.chungjungsoo.gptmobile.data.network.OpenAIAPIImpl
import dev.chungjungsoo.gptmobile.data.network.ProviderRequestConfig
import dev.chungjungsoo.gptmobile.data.network.UploadedProviderFile
import dev.chungjungsoo.gptmobile.util.AttachmentPayloadCache
import dev.chungjungsoo.gptmobile.util.FileUtils
import io.ktor.client.engine.cio.CIO
import java.io.File
import java.net.InetSocketAddress
import java.util.Base64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentUploadCoordinatorTest {
    @Test
    fun `responses images work when the gateway files endpoint returns HTML`() = runBlocking {
        val imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII="
        val image = File.createTempFile("responses-image", ".png").apply {
            writeBytes(Base64.getDecoder().decode(imageBase64))
        }
        val paths = mutableListOf<String>()
        var responseRequest = ""
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            val request = exchange.requestBody.bufferedReader().use { it.readText() }
            val isResponses = exchange.requestURI.path == "/v1/responses"
            if (isResponses) responseRequest = request
            val body = if (isResponses) "data: [DONE]\n\n" else "<!doctype html><html>Not found</html>"
            exchange.responseHeaders.set("Content-Type", if (isResponses) "text/event-stream" else "text/html")
            exchange.sendResponseHeaders(if (isResponses) 200 else 404, body.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
        }
        server.start()
        val network = NetworkClient(CIO)
        try {
            val api = OpenAIAPIImpl(network)
            val coordinator = AttachmentUploadCoordinator(api, FakeAnthropicAPI(), FakeGoogleAPI())
            val platform = PlatformV2(
                uid = "gateway",
                name = "Responses gateway",
                compatibleType = ClientType.OPENAI,
                apiUrl = "http://127.0.0.1:${server.address.port}/v1",
                model = "vision-model"
            )
            val message = coordinator.ensureMessageAttachmentsForPlatform(
                MessageV2(
                    content = "describe this image",
                    platformType = null,
                    attachments = listOf(ChatAttachment(image.path, image.path, image.name, "image/png", image.length()))
                ),
                platform
            )
            assertTrue(message.attachments.single().providerRefs.isEmpty())
            AttachmentPayloadCache.put(image.path, FileUtils.EncodedImage("image/png", imageBase64))
            val input = ProviderAttachmentEncoder(ContextWrapper(null)).responsesInput(
                listOf(ConversationTurn(message, null, isCurrentTurn = true)),
                platform.uid
            )
            api.streamResponses(ResponsesRequest(platform.model, input), 5, ProviderRequestConfig(platform.apiUrl, null)).toList()

            assertEquals(listOf("/v1/responses"), paths)
            val content = Json.parseToJsonElement(responseRequest).jsonObject["input"]!!.jsonArray.single()
                .jsonObject["content"]!!.jsonArray
            assertEquals("describe this image", content[0].jsonObject["text"]!!.jsonPrimitive.content)
            assertEquals("input_image", content[1].jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals("data:image/png;base64,$imageBase64", content[1].jsonObject["image_url"]!!.jsonPrimitive.content)
            assertNull(content[1].jsonObject["file_id"])
        } finally {
            AttachmentPayloadCache.remove(image.path)
            network().close()
            server.stop(0)
            image.delete()
        }
    }

    @Test
    fun `existing openai ref is reused without upload`() = runBlocking {
        val openAIAPI = FakeOpenAIAPI(isAvailable = true)
        val coordinator = AttachmentUploadCoordinator(openAIAPI, FakeAnthropicAPI(), FakeGoogleAPI())
        val message = MessageV2(
            content = "hello",
            platformType = null,
            attachments = listOf(
                ChatAttachment(
                    localFilePath = "/tmp/image.png",
                    preparedFilePath = "/tmp/image.png",
                    displayName = "image.png",
                    mimeType = "image/png",
                    sizeBytes = 1,
                    providerRefs = listOf(
                        AttachmentProviderRef(
                            platformUid = "openai-platform",
                            remoteType = AttachmentRemoteType.OPENAI_FILE,
                            remoteId = "file-existing",
                            mimeType = "image/png",
                            uploadedAt = 1L
                        )
                    )
                )
            )
        )

        val updated = coordinator.ensureMessageAttachmentsForPlatform(
            message,
            PlatformV2(
                uid = "openai-platform",
                name = "OpenAI",
                compatibleType = ClientType.OPENAI,
                apiUrl = "https://api.openai.com",
                model = "gpt-4.1"
            )
        )

        assertEquals(0, openAIAPI.uploadCount)
        assertEquals("file-existing", updated.attachments.single().providerRefs.single().remoteId)
        coordinator.validateInlineAttachmentBudget(
            listOf(ConversationTurn(updated, null, isCurrentTurn = true)),
            maxInlineBytes = 0,
            openAIPlatformUid = "openai-platform"
        )
    }

    @Test
    fun `expired OpenAI image references fall back to inline without uploading again`() = runBlocking {
        val api = FakeOpenAIAPI(isAvailable = false)
        val coordinator = AttachmentUploadCoordinator(api, FakeAnthropicAPI(), FakeGoogleAPI())
        val profile = PlatformV2(uid = "openai-platform", name = "OpenAI", compatibleType = ClientType.OPENAI, apiUrl = "https://example.com/v1", model = "vision-model")
        val message = MessageV2(
            content = "describe",
            platformType = null,
            attachments = listOf(
                ChatAttachment(
                    "/image.png",
                    "/image.png",
                    "image.png",
                    "image/png",
                    1,
                    providerRefs = listOf(
                        AttachmentProviderRef(profile.uid, AttachmentRemoteType.OPENAI_FILE, "expired", mimeType = "image/png", uploadedAt = 1),
                        AttachmentProviderRef("other-profile", AttachmentRemoteType.GOOGLE_FILE, "keep", mimeType = "image/png", uploadedAt = 1)
                    )
                )
            )
        )
        val updated = coordinator.ensureMessageAttachmentsForPlatform(message, profile)

        assertEquals(0, api.uploadCount)
        assertNull(updated.attachments.single().providerRefFor(profile.uid))
        assertEquals("keep", updated.attachments.single().providerRefFor("other-profile")?.remoteId)
    }

    @Test
    fun `missing google ref uploads and stores remote uri`() = runBlocking {
        val googleAPI = FakeGoogleAPI()
        val coordinator = AttachmentUploadCoordinator(FakeOpenAIAPI(), FakeAnthropicAPI(), googleAPI)
        val tempFile = File.createTempFile("attachment", ".png").apply {
            writeBytes(ByteArray(32))
            deleteOnExit()
        }
        val message = MessageV2(
            content = "describe",
            platformType = null,
            attachments = listOf(
                ChatAttachment(
                    localFilePath = tempFile.absolutePath,
                    preparedFilePath = tempFile.absolutePath,
                    displayName = tempFile.name,
                    mimeType = "image/png",
                    sizeBytes = tempFile.length()
                )
            )
        )

        val updated = coordinator.ensureMessageAttachmentsForPlatform(
            message,
            PlatformV2(
                uid = "google-platform",
                name = "Google",
                compatibleType = ClientType.GOOGLE,
                apiUrl = "https://generativelanguage.googleapis.com",
                model = "gemini-2.0-flash"
            )
        )

        assertEquals(1, googleAPI.uploadCount)
        assertEquals("google-uri", updated.attachments.single().providerRefs.single().remoteId)
        assertEquals("files/google-file", updated.attachments.single().providerRefs.single().remoteName)
    }

    @Test(expected = IllegalStateException::class)
    fun `inline attachment budget rejects oversized payloads`() = runBlocking {
        val coordinator = AttachmentUploadCoordinator(FakeOpenAIAPI(), FakeAnthropicAPI(), FakeGoogleAPI())
        val first = File.createTempFile("inline-first", ".png").apply {
            writeBytes(ByteArray(7 * 1024 * 1024))
            deleteOnExit()
        }
        val second = File.createTempFile("inline-second", ".png").apply {
            writeBytes(ByteArray(7 * 1024 * 1024))
            deleteOnExit()
        }

        assertEquals(12L * 1024 * 1024, ProviderContextPolicy.forClientType(ClientType.OPENAI).maxInlineAttachmentBytes)
        coordinator.validateInlineAttachmentBudget(
            contextTurns = listOf(
                ConversationTurn(
                    userMessage = MessageV2(
                        content = "hi",
                        platformType = null,
                        attachments = listOf(
                            ChatAttachment(
                                localFilePath = first.absolutePath,
                                preparedFilePath = first.absolutePath,
                                displayName = first.name,
                                mimeType = "image/png",
                                sizeBytes = first.length()
                            ),
                            ChatAttachment(
                                localFilePath = second.absolutePath,
                                preparedFilePath = second.absolutePath,
                                displayName = second.name,
                                mimeType = "image/png",
                                sizeBytes = second.length()
                            )
                        )
                    ),
                    assistantMessage = null,
                    isCurrentTurn = true
                )
            ),
            maxInlineBytes = requireNotNull(ProviderContextPolicy.forClientType(ClientType.OPENAI).maxInlineAttachmentBytes),
            openAIPlatformUid = "openai-platform"
        )
    }

    private class FakeOpenAIAPI(
        private val isAvailable: Boolean = false
    ) : OpenAIAPI {
        var uploadCount = 0

        override fun streamChatCompletion(
            request: ChatCompletionRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ChatCompletionChunk> = emptyFlow()

        override fun streamResponses(
            request: ResponsesRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<ResponsesStreamEvent> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ): UploadedProviderFile {
            uploadCount += 1
            return UploadedProviderFile(id = "file-uploaded", mimeType = mimeType)
        }

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig): Boolean = isAvailable
        override suspend fun compactResponses(
            request: dev.chungjungsoo.gptmobile.data.dto.openai.request.CompactResponsesRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ) = dev.chungjungsoo.gptmobile.data.dto.openai.request.CompactResponsesResult()
    }

    private class FakeAnthropicAPI : AnthropicAPI {
        override fun streamChatMessage(
            messageRequest: MessageRequest,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<MessageResponseChunk> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ): UploadedProviderFile = UploadedProviderFile(id = "anthropic-file", mimeType = mimeType)

        override suspend fun isFileAvailable(fileId: String, config: ProviderRequestConfig): Boolean = false
    }

    private class FakeGoogleAPI : GoogleAPI {
        var uploadCount = 0

        override fun streamGenerateContent(
            request: GenerateContentRequest,
            model: String,
            timeoutSeconds: Int,
            config: ProviderRequestConfig
        ): Flow<GenerateContentResponse> = emptyFlow()

        override suspend fun uploadFile(
            filePath: String,
            fileName: String,
            mimeType: String,
            config: ProviderRequestConfig
        ): UploadedProviderFile {
            uploadCount += 1
            return UploadedProviderFile(
                id = "google-id",
                name = "files/google-file",
                uri = "google-uri",
                mimeType = mimeType
            )
        }

        override suspend fun isFileAvailable(fileName: String, config: ProviderRequestConfig): Boolean = false
    }
}
