package app.hearth.core.data.settings

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.withWriteTransaction
import app.hearth.core.data.HearthDatabase
import app.hearth.core.model.nowMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Entity(tableName = "setting")
data class SettingEntity(@PrimaryKey val key: String, val value: String, val updatedAt: Long)

@Dao
interface SettingDao {
    @Query("SELECT * FROM setting")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM setting")
    suspend fun all(): List<SettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: SettingEntity)

    @Query("DELETE FROM setting WHERE key IN (:keys)")
    suspend fun delete(keys: List<String>)
}

/**
 * How Hearth reaches the family's AI. [connectorSecret] goes into the connector URL the AI apps use;
 * [tabletKey] proves to the relay that this is the household's own tablet. Both are made on the device.
 */
data class Connection(
    val enabled: Boolean = false,
    val relayUrl: String? = null,
    val connectorSecret: String? = null,
    val tabletKey: String? = null,
) {
    /** The URL to paste into ChatGPT or Claude. */
    val connectorUrl: String? get() = if (relayUrl != null && connectorSecret != null) "${relayUrl.trimEnd('/')}/mcp/$connectorSecret" else null
    val tabletUrl: String? get() = if (relayUrl != null && connectorSecret != null && tabletKey != null) {
        relayUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/connect/$connectorSecret?key=$tabletKey"
    } else null
}

/** Small key/value settings. Writes come from tools only. */
class SettingsRepository(
    private val db: HearthDatabase,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.settingDao()

    val connection: Flow<Connection> = dao.observeAll().map { toConnection(it) }

    suspend fun currentConnection(): Connection = toConnection(dao.all())

    /** Updates what's given; makes the secrets the first time. [newSecrets] starts over with fresh ones. */
    suspend fun saveConnection(enabled: Boolean?, relayUrl: String?, newSecrets: Boolean = false): Connection = db.withWriteTransaction {
        val now = clock()
        val current = toConnection(dao.all())
        if (enabled != null) dao.put(SettingEntity(ENABLED, enabled.toString(), now))
        if (relayUrl != null) dao.put(SettingEntity(RELAY_URL, relayUrl.trim().trimEnd('/'), now))
        if (newSecrets || current.connectorSecret == null) dao.put(SettingEntity(SECRET, secret(), now))
        if (newSecrets || current.tabletKey == null) dao.put(SettingEntity(TABLET_KEY, secret(), now))
        toConnection(dao.all())
    }

    private fun toConnection(rows: List<SettingEntity>): Connection {
        val map = rows.associate { it.key to it.value }
        return Connection(map[ENABLED] == "true", map[RELAY_URL], map[SECRET], map[TABLET_KEY])
    }

    private companion object {
        const val ENABLED = "mcp.enabled"
        const val RELAY_URL = "mcp.relayUrl"
        const val SECRET = "mcp.connectorSecret"
        const val TABLET_KEY = "mcp.tabletKey"

        /** 64 random hex characters (two random UUIDs, from a secure random source). */
        @OptIn(ExperimentalUuidApi::class)
        fun secret(): String = Uuid.random().toHexString() + Uuid.random().toHexString()
    }
}
