package com.puppycoder.relay.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "computers")
data class ComputerEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val endpoint: String,
    val workspace: String,
    val username: String,
    val encryptedPassword: String,
    val routeMode: String,
    val tunnelProfileId: String?,
    val allowDirectFallback: Boolean,
    val connectionState: String,
    val version: String,
    val latencyMs: Long,
    val isDemo: Boolean,
)

@Entity(tableName = "tunnel_profiles")
data class TunnelProfileEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val encryptedPassword: String,
    val encryptedPrivateKey: String,
    val encryptedPrivateKeyPassphrase: String,
    val hostKeyFingerprint: String,
    val priority: Int,
    val enabled: Boolean,
)

@Entity(
    tableName = "tunnel_routes",
    foreignKeys = [
        ForeignKey(
            entity = TunnelProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class TunnelRouteEntity(
    @androidx.room.PrimaryKey val id: String,
    val profileId: String,
    val hostPattern: String,
    val port: Int?,
)

data class TunnelWithRoutes(
    @Embedded val profile: TunnelProfileEntity,
    @Relation(parentColumn = "id", entityColumn = "profileId")
    val routes: List<TunnelRouteEntity>,
)

@Entity(
    tableName = "conversations",
    foreignKeys = [
        ForeignKey(
            entity = ComputerEntity::class,
            parentColumns = ["id"],
            childColumns = ["computerId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("computerId"), Index("updatedAt")],
)
data class ConversationEntity(
    @androidx.room.PrimaryKey val id: String,
    val computerId: String,
    val title: String,
    val workspace: String,
    val remoteConversationId: String?,
    val modelId: String?,
    val modelProviderId: String?,
    val modelDisplayName: String?,
    val state: String,
    val lastMessagePreview: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversationId"),
        Index(value = ["conversationId", "createdAt"]),
        Index("deliveryState"),
    ],
)
data class ChatMessageEntity(
    @androidx.room.PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val body: String,
    val deliveryState: String,
    val remoteMessageId: String?,
    val remoteTurnId: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "message_images",
    foreignKeys = [
        ForeignKey(
            entity = ChatMessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["messageId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("messageId")],
)
data class MessageImageEntity(
    @androidx.room.PrimaryKey val id: String,
    val messageId: String,
    val mimeType: String,
    val filePath: String,
    val fileName: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long,
)

@Entity(
    tableName = "tool_activity",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("conversationId"), Index("messageId")],
)
data class ToolActivityEntity(
    @androidx.room.PrimaryKey val id: String,
    val conversationId: String,
    val messageId: String?,
    val title: String,
    val detail: String,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Dao
interface ChatDao {
    @Query("SELECT * FROM computers ORDER BY name COLLATE NOCASE")
    fun observeComputers(): Flow<List<ComputerEntity>>

    @Query("SELECT * FROM computers ORDER BY name COLLATE NOCASE")
    suspend fun getComputers(): List<ComputerEntity>

    @Query("SELECT * FROM computers WHERE id = :id")
    suspend fun getComputer(id: String): ComputerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComputer(computer: ComputerEntity)

    @Query("DELETE FROM computers WHERE id = :id")
    suspend fun deleteComputer(id: String)

    @Transaction
    @Query("SELECT * FROM tunnel_profiles ORDER BY priority DESC, name COLLATE NOCASE")
    fun observeTunnels(): Flow<List<TunnelWithRoutes>>

    @Transaction
    @Query("SELECT * FROM tunnel_profiles ORDER BY priority DESC, name COLLATE NOCASE")
    suspend fun getTunnels(): List<TunnelWithRoutes>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTunnelProfile(profile: TunnelProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTunnelRoutes(routes: List<TunnelRouteEntity>)

    @Query("DELETE FROM tunnel_routes WHERE profileId = :profileId")
    suspend fun deleteTunnelRoutes(profileId: String)

    @Query("DELETE FROM tunnel_profiles WHERE id = :id")
    suspend fun deleteTunnel(id: String)

    @Transaction
    suspend fun upsertTunnel(profile: TunnelProfileEntity, routes: List<TunnelRouteEntity>) {
        upsertTunnelProfile(profile)
        deleteTunnelRoutes(profile.id)
        upsertTunnelRoutes(routes)
    }

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query(
        "SELECT DISTINCT conversations.* FROM conversations " +
            "LEFT JOIN messages ON messages.conversationId = conversations.id " +
            "WHERE conversations.title LIKE '%' || :query || '%' " +
            "OR messages.body LIKE '%' || :query || '%' " +
            "ORDER BY conversations.updatedAt DESC",
    )
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeConversation(id: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Query("SELECT * FROM conversations WHERE computerId = :computerId AND remoteConversationId = :remoteId LIMIT 1")
    suspend fun getConversationByRemoteId(computerId: String, remoteId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id")
    fun observeMessages(conversationId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt, id")
    suspend fun getMessages(conversationId: String): List<ChatMessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: String): ChatMessageEntity?

    @Query(
        "SELECT message_images.* FROM message_images " +
            "INNER JOIN messages ON messages.id = message_images.messageId " +
            "WHERE messages.conversationId = :conversationId ORDER BY message_images.createdAt, message_images.id",
    )
    fun observeMessageImages(conversationId: String): Flow<List<MessageImageEntity>>

    @Query("SELECT * FROM message_images WHERE messageId = :messageId ORDER BY createdAt, id")
    suspend fun getMessageImages(messageId: String): List<MessageImageEntity>

    @Query(
        "SELECT * FROM messages WHERE conversationId = :conversationId AND role = 'USER' " +
            "AND deliveryState = 'QUEUED' ORDER BY createdAt, id LIMIT 1",
    )
    suspend fun nextQueuedMessage(conversationId: String): ChatMessageEntity?

    @Query("SELECT DISTINCT conversationId FROM messages WHERE deliveryState = 'QUEUED'")
    suspend fun conversationsWithQueuedMessages(): List<String>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMessageImages(images: List<MessageImageEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessagesIfMissing(messages: List<ChatMessageEntity>)

    @Update
    suspend fun updateMessage(message: ChatMessageEntity)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("SELECT * FROM tool_activity WHERE conversationId = :conversationId ORDER BY createdAt, id")
    fun observeTools(conversationId: String): Flow<List<ToolActivityEntity>>

    @Query("SELECT * FROM tool_activity WHERE id = :id")
    suspend fun getTool(id: String): ToolActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTool(tool: ToolActivityEntity)

    @Query("UPDATE messages SET deliveryState = 'QUEUED', updatedAt = :now WHERE deliveryState = 'SENDING'")
    suspend fun recoverSendingMessages(now: Long)

    @Query(
        "UPDATE messages SET deliveryState = 'DELIVERY_UNCERTAIN', " +
            "errorMessage = 'Connection ended before completion', updatedAt = :now " +
            "WHERE deliveryState = 'STREAMING'",
    )
    suspend fun recoverStreamingMessages(now: Long)

    @Transaction
    suspend fun insertOptimisticMessage(
        conversation: ConversationEntity,
        message: ChatMessageEntity,
        images: List<MessageImageEntity> = emptyList(),
    ) {
        insertMessage(message)
        if (images.isNotEmpty()) insertMessageImages(images)
        updateConversation(conversation)
    }
}

@Database(
    entities = [
        ComputerEntity::class,
        TunnelProfileEntity::class,
        TunnelRouteEntity::class,
        ConversationEntity::class,
        ChatMessageEntity::class,
        MessageImageEntity::class,
        ToolActivityEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class PuppyCoderDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var instance: PuppyCoderDatabase? = null

        fun get(context: Context): PuppyCoderDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PuppyCoderDatabase::class.java,
                "puppycoder.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE conversations ADD COLUMN modelId TEXT")
                database.execSQL("ALTER TABLE conversations ADD COLUMN modelProviderId TEXT")
                database.execSQL("ALTER TABLE conversations ADD COLUMN modelDisplayName TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `message_images` (" +
                        "`id` TEXT NOT NULL, `messageId` TEXT NOT NULL, `mimeType` TEXT NOT NULL, " +
                        "`filePath` TEXT NOT NULL, `fileName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, " +
                        "`width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`), FOREIGN KEY(`messageId`) REFERENCES `messages`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_message_images_messageId` ON `message_images` (`messageId`)",
                )
            }
        }
    }
}

internal fun ConversationEntity.toModel() = Conversation(
    id = id,
    computerId = computerId,
    title = title,
    workspace = workspace,
    remoteConversationId = remoteConversationId,
    modelId = modelId,
    modelProviderId = modelProviderId,
    modelDisplayName = modelDisplayName,
    state = ConversationState.valueOf(state),
    lastMessagePreview = lastMessagePreview,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun Conversation.toEntity() = ConversationEntity(
    id = id,
    computerId = computerId,
    title = title,
    workspace = workspace,
    remoteConversationId = remoteConversationId,
    modelId = modelId,
    modelProviderId = modelProviderId,
    modelDisplayName = modelDisplayName,
    state = state.name,
    lastMessagePreview = lastMessagePreview,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ChatMessageEntity.toModel(images: List<MessageImage> = emptyList()) = ChatMessage(
    id = id,
    conversationId = conversationId,
    role = MessageRole.valueOf(role),
    body = body,
    deliveryState = DeliveryState.valueOf(deliveryState),
    remoteMessageId = remoteMessageId,
    remoteTurnId = remoteTurnId,
    errorMessage = errorMessage,
    images = images,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun MessageImageEntity.toModel() = MessageImage(
    id = id,
    messageId = messageId,
    mimeType = mimeType,
    filePath = filePath,
    fileName = fileName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    createdAt = createdAt,
)

internal fun MessageImage.toEntity() = MessageImageEntity(
    id = id,
    messageId = messageId,
    mimeType = mimeType,
    filePath = filePath,
    fileName = fileName,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    createdAt = createdAt,
)

internal fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
    conversationId = conversationId,
    role = role.name,
    body = body,
    deliveryState = deliveryState.name,
    remoteMessageId = remoteMessageId,
    remoteTurnId = remoteTurnId,
    errorMessage = errorMessage,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun ToolActivityEntity.toModel() = ToolActivity(
    id = id,
    conversationId = conversationId,
    messageId = messageId,
    title = title,
    detail = detail,
    state = ToolActivityState.valueOf(state),
    createdAt = createdAt,
    updatedAt = updatedAt,
)
