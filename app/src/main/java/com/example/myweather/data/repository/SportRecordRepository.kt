package com.example.myweather.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myweather.data.model.SportRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sportDataStore: DataStore<Preferences> by preferencesDataStore("sport_records")

class SportRecordRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val recordsKey = stringPreferencesKey("records")

    fun observeRecords(): Flow<List<SportRecord>> =
        appContext.sportDataStore.data.map { prefs ->
            decode(prefs[recordsKey])
        }

    suspend fun addRecord(record: SportRecord) {
        appContext.sportDataStore.edit { prefs ->
            val updated = (decode(prefs[recordsKey]) + record)
                .sortedByDescending { it.startTime }
            prefs[recordsKey] = json.encodeToString(updated)
        }
    }

    suspend fun deleteRecord(id: Long) {
        appContext.sportDataStore.edit { prefs ->
            val updated = decode(prefs[recordsKey]).filterNot { it.id == id }
            prefs[recordsKey] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String?): List<SportRecord> {
        if (raw.isNullOrEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<SportRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
