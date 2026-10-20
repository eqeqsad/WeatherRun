package com.example.myweather

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myweather.data.model.SportRecord
import com.example.myweather.data.model.SportStats
import com.example.myweather.data.model.computeStats
import com.example.myweather.data.repository.SportRecordRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SportRecordRepository(application)

    val records: StateFlow<List<SportRecord>> = repository.observeRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val stats: StateFlow<SportStats> = records
        .map { computeStats(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), computeStats(emptyList()))

    fun delete(record: SportRecord) {
        viewModelScope.launch { repository.deleteRecord(record.id) }
    }
}
