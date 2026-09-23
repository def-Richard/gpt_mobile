package dev.chungjungsoo.gptmobile.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import dev.chungjungsoo.gptmobile.data.database.entity.ChatRoomV2
import dev.chungjungsoo.gptmobile.data.database.entity.PlatformV2
import dev.chungjungsoo.gptmobile.data.model.ClientType
import dev.chungjungsoo.gptmobile.data.network.OpenAIModelOption
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderModelCatalogInstrumentedTest {
    @Test
    fun catalogsAndFastSelectionSurviveDatabaseReopenWithoutCrossingProviders() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "provider-model-catalog-test"
        context.deleteDatabase(name)
        var database = Room.databaseBuilder(context, ChatDatabaseV2::class.java, name).build()
        try {
            val first = PlatformV2(uid = "first", name = "First", compatibleType = ClientType.OPENAI, apiUrl = "https://example.com/v1/", model = "a")
            val second = first.copy(uid = "second", name = "Second")
            val firstId = database.platformDao().addPlatform(first).toInt()
            val secondId = database.platformDao().addPlatform(second).toInt()
            val catalog = listOf(OpenAIModelOption("model,with-comma", 998000), OpenAIModelOption("b"))
            assertTrue(database.platformDao().replaceModelCatalog(first.copy(id = firstId), catalog))
            assertTrue(database.platformDao().replaceModelCatalog(second.copy(id = secondId), listOf(OpenAIModelOption("other"))))
            database.platformDao().editSettingsPreservingCatalog(first.copy(id = firstId, reasoning = true), false)
            val chatId = database.agentPersistenceDao().insertChatRoom(ChatRoomV2(title = "Fast", enabledPlatform = listOf("first"), fastPlatforms = listOf("first"))).toInt()
            database.close()
            database = Room.databaseBuilder(context, ChatDatabaseV2::class.java, name).build()
            assertEquals(catalog, database.platformDao().getPlatform(firstId)?.modelCatalog)
            assertEquals(listOf("other"), database.platformDao().getPlatform(secondId)?.modelCatalog?.map { it.id })
            assertEquals(listOf("first"), database.agentPersistenceDao().getChatRoom(chatId)?.fastPlatforms)
            val updated = first.copy(id = firstId, apiUrl = "https://other.example/v1/")
            database.platformDao().editSettingsPreservingCatalog(updated, true)
            assertTrue(database.platformDao().getPlatform(firstId)!!.modelCatalog.isEmpty())
            assertFalse(database.platformDao().replaceModelCatalog(first.copy(id = firstId), catalog))
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
