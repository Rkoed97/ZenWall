package com.example.zenwall.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

private val Context.profileDataStore by preferencesDataStore("profiles_store")

/**
 * Minimal repository to manage user profiles and their app lists without Room.
 * Profiles are stored as a JSON string in Preferences DataStore.
 */
class ProfileRepository(private val context: Context) {
    private val KEY_ACTIVE_ID: Preferences.Key<Long> = longPreferencesKey("active_profile_id")
    private val KEY_PROFILES_JSON: Preferences.Key<String> = stringPreferencesKey("profiles_json")
    private val KEY_NEXT_ID: Preferences.Key<Long> = longPreferencesKey("profiles_next_id")

    private val mutex = Mutex()

    data class Profile(
        val id: Long,
        val name: String,
        val mode: ProfileMode,
        val apps: List<String>,
        val createdAt: Long,
        val updatedAt: Long,
    )

    enum class ProfileMode { WHITELIST, BLACKLIST }

    data class ProfileSummary(val id: Long, val name: String, val mode: ProfileMode, val appCount: Int)

    val profilesFlow: Flow<List<Profile>> = context.profileDataStore.data.map { prefs ->
        val json = prefs[KEY_PROFILES_JSON] ?: "[]"
        parseProfiles(json)
    }

    val activeProfileIdFlow: Flow<Long?> = context.profileDataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_ID]
    }

    val activeProfileFlow: Flow<Profile?> = kotlinx.coroutines.flow.combine(profilesFlow, activeProfileIdFlow) { list, id ->
        id?.let { theId -> list.firstOrNull { it.id == theId } }
    }

    suspend fun getProfilesOnce(): List<Profile> = profilesFlow.first()

    suspend fun ensureDefaultProfileIfEmpty(legacyMode: Boolean, legacyApps: List<String>) {
        mutex.withLock {
            val current = getProfilesOnce()
            if (current.isNotEmpty()) return
            val now = System.currentTimeMillis()
            val id = nextId()
            val default = Profile(
                id = id,
                name = "Default",
                mode = if (legacyMode) ProfileMode.WHITELIST else ProfileMode.BLACKLIST,
                apps = legacyApps,
                createdAt = now,
                updatedAt = now
            )
            saveProfiles(listOf(default))
            // Set active to the default profile if none active
            context.profileDataStore.edit { prefs ->
                if (!prefs.asMap().containsKey(KEY_ACTIVE_ID)) {
                    prefs[KEY_ACTIVE_ID] = id
                }
            }
        }
    }

    suspend fun createProfile(name: String, mode: ProfileMode, apps: List<String>): Long = mutex.withLock {
        val list = getProfilesOnce().toMutableList()
        val now = System.currentTimeMillis()
        val id = nextId()
        list.add(Profile(id, name, mode, apps.distinct().sorted(), now, now))
        saveProfiles(list)
        // If this is the first profile ever, make it active by default
        if (list.size == 1) {
            context.profileDataStore.edit { it[KEY_ACTIVE_ID] = id }
        }
        id
    }

    suspend fun updateProfile(updated: Profile) = mutex.withLock {
        val list = getProfilesOnce().toMutableList()
        val idx = list.indexOfFirst { it.id == updated.id }
        if (idx != -1) {
            val now = System.currentTimeMillis()
            list[idx] = updated.copy(
                apps = updated.apps.distinct().sorted(),
                updatedAt = now
            )
            saveProfiles(list)
        }
    }

    suspend fun deleteProfile(id: Long) = mutex.withLock {
        val list = getProfilesOnce().filter { it.id != id }
        saveProfiles(list)
        // If the active profile was deleted, clear or switch to the first available
        context.profileDataStore.edit { prefs ->
            val active = prefs[KEY_ACTIVE_ID]
            if (active == id) {
                val newActive = list.firstOrNull()?.id
                if (newActive == null) {
                    prefs.remove(KEY_ACTIVE_ID)
                } else {
                    prefs[KEY_ACTIVE_ID] = newActive
                }
            }
        }
    }

    suspend fun duplicateProfile(id: Long): Long = mutex.withLock {
        val list = getProfilesOnce()
        val src = list.firstOrNull { it.id == id } ?: return@withLock -1L
        val newId = nextId()
        val now = System.currentTimeMillis()
        val copy = src.copy(
            id = newId,
            name = src.name + " (copy)",
            createdAt = now,
            updatedAt = now
        )
        saveProfiles(list + copy)
        newId
    }

    fun getProfileFlow(id: Long): Flow<Profile?> = profilesFlow.map { list -> list.firstOrNull { it.id == id } }

    suspend fun setActiveProfile(id: Long) {
        context.profileDataStore.edit { it[KEY_ACTIVE_ID] = id }
    }

    suspend fun getActiveProfileOnce(): Profile? {
        val list = getProfilesOnce()
        val activeId = context.profileDataStore.data.map { it[KEY_ACTIVE_ID] }.first()
        return list.firstOrNull { it.id == activeId }
    }

    // Internal JSON helpers
    private suspend fun saveProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("mode", p.mode.name)
            obj.put("createdAt", p.createdAt)
            obj.put("updatedAt", p.updatedAt)
            obj.put("apps", JSONArray(p.apps))
            arr.put(obj)
        }
        context.profileDataStore.edit { it[KEY_PROFILES_JSON] = arr.toString() }
    }

    private fun parseProfiles(json: String): List<Profile> {
        return try {
            val arr = JSONArray(json)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val appsArr = o.optJSONArray("apps") ?: JSONArray()
                    val apps = buildList(appsArr.length()) {
                        for (j in 0 until appsArr.length()) add(appsArr.getString(j))
                    }
                    add(
                        Profile(
                            id = o.getLong("id"),
                            name = o.getString("name"),
                            mode = ProfileMode.valueOf(o.getString("mode")),
                            apps = apps,
                            createdAt = o.optLong("createdAt"),
                            updatedAt = o.optLong("updatedAt"),
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun nextId(): Long {
        var newId = 0L
        context.profileDataStore.edit { prefs ->
            val current = prefs[KEY_NEXT_ID] ?: 1L
            newId = current
            prefs[KEY_NEXT_ID] = current + 1
        }
        return newId
    }
}
