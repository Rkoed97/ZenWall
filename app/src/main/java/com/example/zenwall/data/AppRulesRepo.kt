package com.example.zenwall.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("app_rules")

class AppRulesRepo(private val context: Context) {
    private val BLOCKED: Preferences.Key<Set<String>> = stringSetPreferencesKey("blocked_packages")
    private val WHITELIST_MODE: Preferences.Key<Boolean> = booleanPreferencesKey("whitelist_mode")

    val blockedPackagesFlow: Flow<Set<String>> =
        context.dataStore.data.map { it[BLOCKED] ?: emptySet() }

    val whitelistModeFlow: Flow<Boolean> =
        context.dataStore.data.map { it[WHITELIST_MODE] ?: false }

    suspend fun setBlockedPackages(pkgs: Set<String>) {
        context.dataStore.edit { it[BLOCKED] = pkgs }
    }

    suspend fun setWhitelistMode(enabled: Boolean) {
        context.dataStore.edit { it[WHITELIST_MODE] = enabled }
    }

    suspend fun getBlockedPackagesOnce(): List<String> =
        context.dataStore.data.map { it[BLOCKED] ?: emptySet() }.first().toList()

    suspend fun getWhitelistModeOnce(): Boolean =
        context.dataStore.data.map { it[WHITELIST_MODE] ?: false }.first()
}
