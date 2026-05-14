package com.snapfacture.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.securityDataStore by preferencesDataStore(name = "security_prefs")

@Singleton
class SecurityPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyAppLock = booleanPreferencesKey("app_lock_enabled")

    val flow: Flow<Boolean> = context.securityDataStore.data.map { it[keyAppLock] ?: false }

    suspend fun isAppLockEnabled(): Boolean = flow.first()

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.securityDataStore.edit { it[keyAppLock] = enabled }
    }
}
