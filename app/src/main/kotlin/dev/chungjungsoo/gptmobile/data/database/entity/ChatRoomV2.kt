package dev.chungjungsoo.gptmobile.data.database.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Parcelize
@Entity(tableName = "chats_v2")
data class ChatRoomV2(
    /**
     Now, enabled platforms are stored as list of strings.
     The strings are UUID V4 strings from PlatformV2.uid
     */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "chat_id")
    val id: Int = 0,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "enabled_platform")
    val enabledPlatform: List<String>,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis() / 1000,

    @ColumnInfo(name = "active_platform_uid")
    val activePlatformUid: String? = null,

    @ColumnInfo(name = "reasoning_efforts", defaultValue = "'{}'")
    val reasoningEfforts: Map<String, String> = emptyMap(),

    @ColumnInfo(name = "fast_platforms", defaultValue = "''")
    val fastPlatforms: List<String> = emptyList()
) : Parcelable

class StringListConverter {
    @TypeConverter
    fun fromString(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split(",")

    @TypeConverter
    fun fromList(value: List<String>): String = if (value.isEmpty()) "" else value.joinToString(",")
}

class ChatReasoningConverter {
    @TypeConverter
    fun fromString(value: String): Map<String, String> = Json.decodeFromString(value)

    @TypeConverter
    fun toString(value: Map<String, String>): String = Json.encodeToString(value)
}
