package app.hearth.core.data.settings

import kotlinx.serialization.Serializable
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Entity(tableName = "setting")
@Serializable
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
 * How Hearth reaches the family's AI. [relayUrl] is the shared relay, built in (HEARTH_RELAY_URL); [householdId]
 * names this household there and [tabletKey] proves this is its tablet. Both are made on the device. AI apps get
 * in with a pairing code, never with these.
 */
data class Connection(
    val enabled: Boolean = false,
    val relayUrl: String? = null,
    val householdId: String? = null,
    val tabletKey: String? = null,
) {
    /** The address to add once as a connector in ChatGPT or Claude: the same for every family. */
    val connectorUrl: String? get() = relayUrl?.let { "${it.trimEnd('/')}/mcp" }
    val tabletUrl: String? get() = if (relayUrl != null && householdId != null && tabletKey != null) {
        relayUrl.trimEnd('/').replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "/connect/$householdId?key=$tabletKey"
    } else null
}

/** Small key/value settings. Writes come from tools only. [defaultRelayUrl] is the build's shared relay. */
class SettingsRepository(
    private val db: HearthDatabase,
    private val defaultRelayUrl: String? = null,
    private val clock: () -> Long = ::nowMillis,
) {
    private val dao = db.settingDao()

    val connection: Flow<Connection> = dao.observeAll().map { toConnection(it) }

    suspend fun currentConnection(): Connection = toConnection(dao.all())

    /** Turns the connection on or off and makes the household's ids the first time. */
    suspend fun saveConnection(enabled: Boolean?): Connection = db.withWriteTransaction {
        val now = clock()
        val current = toConnection(dao.all())
        if (enabled != null) dao.put(SettingEntity(ENABLED, enabled.toString(), now))
        if (current.householdId == null) dao.put(SettingEntity(HOUSEHOLD, secret(), now))
        if (current.tabletKey == null) dao.put(SettingEntity(TABLET_KEY, secret(), now))
        toConnection(dao.all())
    }

    /** A plain setting (e.g. Google backup), for the few features that keep one. */
    fun value(key: String): Flow<String?> = dao.observeAll().map { rows -> rows.firstOrNull { it.key == key }?.value }.distinctUntilChanged()

    suspend fun currentValue(key: String): String? = dao.all().firstOrNull { it.key == key }?.value

    suspend fun put(key: String, value: String?) {
        if (value == null) dao.delete(listOf(key)) else dao.put(SettingEntity(key, value, clock()))
    }

    private fun toConnection(rows: List<SettingEntity>): Connection {
        val map = rows.associate { it.key to it.value }
        val relay = defaultRelayUrl?.takeIf { it.isNotBlank() }
        return Connection(map[ENABLED] == "true" && relay != null, relay, map[HOUSEHOLD], map[TABLET_KEY])
    }

    companion object {
        private const val ENABLED = "mcp.enabled"
        /** A relay address typed on the device by an older Hearth; no longer read, and it never travels. */
        private const val RELAY_URL = "mcp.relayUrl"
        private const val HOUSEHOLD = "mcp.householdId"
        private const val TABLET_KEY = "mcp.tabletKey"

        /** Settings that belong to this device and never travel in a backup. */
        val DEVICE_ONLY = setOf(ENABLED, RELAY_URL, HOUSEHOLD, TABLET_KEY, "mcp.connectorSecret", SYNC_KEYS, SYNC_LAST, SMART_LISTENING, INTERPRETER)

        /** Smarter listening on this device (each device downloads its own sentence model). */
        const val SMART_LISTENING = "listen.smart"

        /** Understanding conversations on this device (its own downloaded interpreter). */
        const val INTERPRETER = "listen.interpreter"

        /** This device's way into the household's sync (space, token, key), and when it last synced. */
        const val SYNC_KEYS = "sync.keys"
        const val SYNC_LAST = "sync.last"

        /** 64 random hex characters (two random UUIDs, from a secure random source). */
        @OptIn(ExperimentalUuidApi::class)
        fun secret(): String = Uuid.random().toHexString() + Uuid.random().toHexString()
    }
}
